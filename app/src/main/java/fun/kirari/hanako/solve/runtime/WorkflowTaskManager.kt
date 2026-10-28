package `fun`.kirari.hanako.solve.runtime
import `fun`.kirari.hanako.solve.model.WorkflowTaskKind
import `fun`.kirari.hanako.solve.model.WorkflowTaskState
import `fun`.kirari.hanako.solve.model.WorkflowTaskStatus
import `fun`.kirari.hanako.solve.model.ConversationIntent
import `fun`.kirari.hanako.core.model.FollowUpTurn
import `fun`.kirari.hanako.core.model.withCommittedAssistantVersion
import `fun`.kirari.hanako.core.model.withStreamingAssistantText
import `fun`.kirari.hanako.core.model.withAssistantError

import android.graphics.Bitmap
import `fun`.kirari.hanako.core.model.AnswerVersion
import `fun`.kirari.hanako.core.model.AutomationActionDelivery
import `fun`.kirari.hanako.core.model.ProcessingEvent
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingStatus
import `fun`.kirari.hanako.core.model.displayedAnswerVersions
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.solve.workflow.ProcessingPipeline
import `fun`.kirari.hanako.solve.workflow.HanakoWorkflowEngine
import `fun`.kirari.hanako.solve.workflow.ConversationWorkflowEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class WorkflowTaskManager(
    private val repository: WorkflowHistoryRepository,
    private val resultStore: WorkflowResultStore,
    private val taskRegistry: WorkflowTaskRegistry,
    private val workflowFactory: HanakoWorkflowEngine,
    private val conversationWorkflow: ConversationWorkflowEngine,
    private val titleSummaryService: TitleSummaryService? = null,
    private val scope: CoroutineScope,
    private val processingTimeoutMillis: Long = 90_000L
) {
    private val tag = "HanakoWorkflowTasks"
    private val automationDeliveryMutex = Mutex()
    val tasks: StateFlow<Map<String, WorkflowTaskState>> = taskRegistry.tasks
    val liveResults: StateFlow<Map<String, ProcessingResult>> = resultStore.liveResults
    val pendingAutomationResults: StateFlow<Map<String, ProcessingResult>> = liveResults
        .map { results ->
            results.filterValues { result ->
                result.automationAction != null &&
                    result.automationActionDelivery == AutomationActionDelivery.PENDING
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun isRunning(historyId: String): Boolean {
        return taskRegistry.isRunning(historyId)
    }

    fun startAnswerTask(
        models: ProcessingPipeline.ResolvedModels,
        bitmaps: List<Bitmap>
    ): String {
        val taskId = java.util.UUID.randomUUID().toString()
        taskRegistry.register(taskId, historyId = null, kind = WorkflowTaskKind.ANSWER)
        val job = scope.launch {
            var baseResult: ProcessingResult? = null
            val progressEvents = mutableListOf<ProcessingEvent>()
            val answerText = StringBuilder()
            runCatching {
                withTimeout(processingTimeoutMillis) {
                    val (preparedBaseResult, capturedImages) = workflowFactory.prepareBaseResult(models, bitmaps)
                    baseResult = preparedBaseResult
                    taskRegistry.bindHistory(taskId, preparedBaseResult.id)
                    resultStore.upsert(preparedBaseResult)

                    val workflowOutput = workflowFactory.runAnswerWorkflow(
                        models = models,
                        capturedImages = capturedImages,
                        onOcrDelta = { text ->
                            resultStore.update(preparedBaseResult.id) { current ->
                                current.copy(extractedText = text)
                            }
                        },
                        onAnswerDelta = { delta ->
                            answerText.append(delta)
                            resultStore.update(preparedBaseResult.id) { current ->
                                current.copy(answer = answerText.toString())
                            }
                        },
                        onProgressEvent = { event ->
                            progressEvents.add(event)
                            resultStore.update(preparedBaseResult.id) { current ->
                                current.copy(events = preparedBaseResult.events + progressEvents)
                            }
                        }
                    )
                    val finished = workflowFactory.buildAnswerResult(
                        base = preparedBaseResult,
                        models = models,
                        output = workflowOutput,
                        progressEvents = progressEvents
                    )
                    val finalAnswer = answerText.toString().ifBlank { finished.answer }
                    finished.copy(
                        answer = finalAnswer,
                        answerVersions = listOf(AnswerVersion(finalAnswer))
                    )
                }
            }.onSuccess { result ->
                AppDebugLogStore.i(tag, "answer task success taskId=$taskId historyId=${result.id}")
                resultStore.upsert(result)
                titleSummaryService?.let { service ->
                    scope.launch { service.generate(result, models) }
                }
                taskRegistry.mark(taskId, WorkflowTaskStatus.SUCCESS)
            }.onFailure { error ->
                if (error is CancellationException) {
                    withContext(NonCancellable) {
                        baseResult?.let { base ->
                            val cancelled = cancellationResult(base)
                            resultStore.upsert(cancelled)
                        }
                    }
                    taskRegistry.mark(taskId, WorkflowTaskStatus.CANCELLED)
                    return@onFailure
                }
                AppDebugLogStore.e(tag, "answer task failed taskId=$taskId", error)
                baseResult?.let { base ->
                    val failed = failureResult(base, error)
                    resultStore.upsert(failed)
                }
                taskRegistry.mark(taskId, WorkflowTaskStatus.ERROR, error.message)
            }
        }
        taskRegistry.trackJob(taskId, job)
        return taskId
    }

    fun startRegenerateAnswerTask(
        existingResult: ProcessingResult,
        models: ProcessingPipeline.ResolvedModels,
        bitmaps: List<Bitmap>
    ): String {
        val historyId = existingResult.id
        val taskId = "regenerate:$historyId:${System.currentTimeMillis()}"
        if (isRunning(historyId)) return taskId
        val versionIndex = existingResult.displayedAnswerVersions().size
        taskRegistry.register(taskId, historyId, WorkflowTaskKind.REGENERATE_ANSWER, versionIndex)

        val job = scope.launch {
            val progressEvents = mutableListOf<ProcessingEvent>()
            val answerText = StringBuilder()
            val (baseResult, capturedImages) = workflowFactory.prepareRegenerationBaseResult(
                existingResult = existingResult,
                models = models,
                bitmaps = bitmaps
            )
            val startedResult = baseResult.withStartedAnswerVersionFrom(existingResult)
            resultStore.upsert(startedResult)

            runCatching {
                withTimeout(processingTimeoutMillis) {
                    val workflowOutput = workflowFactory.runAnswerWorkflow(
                        models = models,
                        capturedImages = capturedImages,
                        onOcrDelta = { text ->
                            resultStore.update(historyId) { current ->
                                current.copy(extractedText = text)
                            }
                        },
                        onAnswerDelta = { delta ->
                            answerText.append(delta)
                            resultStore.update(historyId) { current ->
                                current.withUpdatedAnswerVersion(versionIndex, answerText.toString())
                            }
                        },
                        onProgressEvent = { event ->
                            progressEvents.add(event)
                            resultStore.update(historyId) { current ->
                                current.copy(events = startedResult.events + progressEvents)
                            }
                        }
                    )
                    val finished = workflowFactory.buildAnswerResult(
                        base = baseResult,
                        models = models,
                        output = workflowOutput,
                        progressEvents = progressEvents
                    )
                    val finalAnswer = answerText.toString().ifBlank { finished.answer }
                    val latestResult = resultStore.latest(historyId) ?: startedResult
                    latestResult.copy(
                        status = ProcessingStatus.SUCCESS,
                        detail = finished.detail,
                        extractedText = finished.extractedText,
                        answer = finalAnswer,
                        answerVersions = latestResult.answerVersions.replaceAt(versionIndex, AnswerVersion(finalAnswer)),
                        events = finished.events,
                        checkpoints = finished.checkpoints,
                        modelSummary = finished.modelSummary,
                        route = finished.route,
                        assistantName = finished.assistantName
                    )
                }
            }.onSuccess { regenerated ->
                AppDebugLogStore.i(tag, "regenerate task success taskId=$taskId historyId=$historyId")
                resultStore.upsert(regenerated)
                taskRegistry.mark(taskId, WorkflowTaskStatus.SUCCESS)
            }.onFailure { error ->
                if (error is CancellationException) {
                    withContext(NonCancellable) {
                        resultStore.upsert(existingResult)
                    }
                    taskRegistry.mark(taskId, WorkflowTaskStatus.CANCELLED)
                    return@onFailure
                }
                AppDebugLogStore.e(tag, "regenerate task failed taskId=$taskId historyId=$historyId", error)
                val failed = failureResult(startedResult, error)
                resultStore.upsert(failed)
                taskRegistry.mark(taskId, WorkflowTaskStatus.ERROR, error.message)
            }
        }
        taskRegistry.trackJob(taskId, job)
        return taskId
    }

    fun startAutomationTask(
        models: ProcessingPipeline.ResolvedModels,
        bitmaps: List<Bitmap>
    ): String {
        val taskId = java.util.UUID.randomUUID().toString()
        taskRegistry.register(taskId, historyId = null, kind = WorkflowTaskKind.AUTOMATION)
        val job = scope.launch {
            var baseResult: ProcessingResult? = null
            val progressEvents = mutableListOf<ProcessingEvent>()
            val thoughtText = StringBuilder()
            runCatching {
                withTimeout(processingTimeoutMillis) {
                    val (preparedBaseResult, capturedImages) = workflowFactory.prepareBaseResult(
                        models = models,
                        bitmaps = bitmaps,
                        detail = "自动流程已开始"
                    )
                    baseResult = preparedBaseResult
                    taskRegistry.bindHistory(taskId, preparedBaseResult.id)
                    resultStore.upsert(preparedBaseResult)

                    val workflowOutput = workflowFactory.runAutomationWorkflow(
                        models = models,
                        capturedImages = capturedImages,
                        onOcrDelta = { text ->
                            resultStore.update(preparedBaseResult.id) { current ->
                                current.copy(extractedText = text)
                            }
                        },
                        onThoughtDelta = { delta ->
                            thoughtText.append(delta)
                            resultStore.update(preparedBaseResult.id) { current ->
                                current.copy(automationThought = thoughtText.toString())
                            }
                        },
                        onProgressEvent = { event ->
                            progressEvents.add(event)
                            resultStore.update(preparedBaseResult.id) { current ->
                                current.copy(events = preparedBaseResult.events + progressEvents)
                            }
                        }
                    )
                    val (_, result) = workflowFactory.buildAutomationResult(
                        base = preparedBaseResult,
                        models = models,
                        output = workflowOutput,
                        progressEvents = progressEvents
                    )
                    val finalThought = thoughtText.toString().ifBlank { result.automationThought }
                    result.copy(automationThought = finalThought)
                }
            }.onSuccess { result ->
                AppDebugLogStore.i(tag, "automation task success taskId=$taskId historyId=${result.id}")
                resultStore.upsert(result)
                taskRegistry.mark(taskId, WorkflowTaskStatus.SUCCESS)
            }.onFailure { error ->
                if (error is CancellationException) {
                    withContext(NonCancellable) {
                        baseResult?.let { base ->
                            val cancelled = cancellationResult(base)
                            resultStore.upsert(cancelled)
                        }
                    }
                    taskRegistry.mark(taskId, WorkflowTaskStatus.CANCELLED)
                    return@onFailure
                }
                AppDebugLogStore.e(tag, "automation task failed taskId=$taskId", error)
                baseResult?.let { base ->
                    val failed = failureResult(base, error)
                    resultStore.upsert(failed)
                }
                taskRegistry.mark(taskId, WorkflowTaskStatus.ERROR, error.message)
            }
        }
        taskRegistry.trackJob(taskId, job)
        return taskId
    }

    fun startConversationTask(
        existingResult: ProcessingResult,
        models: ProcessingPipeline.ResolvedModels,
        intent: ConversationIntent
    ): String {
        val historyId = existingResult.id
        val turnId = java.util.UUID.randomUUID().toString()
        val taskId = "conversation:$historyId:$turnId"
        if (isRunning(historyId)) return taskId
        taskRegistry.register(
            taskId = taskId,
            historyId = historyId,
            kind = WorkflowTaskKind.CONVERSATION,
            conversationTurnId = turnId
        )

        val job = scope.launch {
            var startedResult: ProcessingResult? = null
            runCatching {
                val prepared = conversationWorkflow.prepareTurn(
                    existingResult = existingResult,
                    models = models,
                    intent = intent,
                    turnId = turnId
                )
                startedResult = prepared.startedResult
                resultStore.upsert(prepared.startedResult)

                val answer = StringBuilder()
                withTimeout(models.firstDeltaTimeoutMillis.coerceAtLeast(90_000L)) {
                    conversationWorkflow.runTurn(prepared) { delta ->
                        answer.append(delta)
                        resultStore.updateConversationTurn(historyId, turnId) { turn ->
                            turn.withStreamingAssistantText(answer.toString())
                        }
                    }
                }
                require(answer.isNotBlank()) { "模型未返回文本内容" }
                resultStore.updateConversationTurnNow(historyId, turnId) { turn ->
                    turn.withCommittedAssistantVersion()
                } ?: error("对话记录已被删除")
            }.onSuccess {
                AppDebugLogStore.i(tag, "conversation task success taskId=$taskId historyId=$historyId")
                taskRegistry.mark(taskId, WorkflowTaskStatus.SUCCESS)
            }.onFailure { error ->
                if (error is CancellationException) {
                    withContext(NonCancellable) {
                        startedResult?.let {
                            resultStore.updateConversationTurnNow(historyId, turnId) { turn ->
                                turn.withAssistantError(CANCELLATION_MESSAGE)
                            }
                        }
                    }
                    taskRegistry.mark(taskId, WorkflowTaskStatus.CANCELLED)
                    return@onFailure
                }
                AppDebugLogStore.e(tag, "conversation task failed taskId=$taskId historyId=$historyId", error)
                val message = error.message?.takeIf(String::isNotBlank) ?: "请求失败"
                startedResult?.let {
                    resultStore.updateConversationTurnNow(historyId, turnId) { turn ->
                        turn.withAssistantError(message)
                    }
                }
                taskRegistry.mark(taskId, WorkflowTaskStatus.ERROR, message)
            }
        }
        taskRegistry.trackJob(taskId, job)
        return taskId
    }

    fun cancelTask(taskId: String) {
        taskRegistry.cancelTask(taskId)
    }

    suspend fun cancelHistoryTask(historyId: String) {
        taskRegistry.cancelRunningHistoryTasks(historyId)
    }

    suspend fun removeHistoryResult(historyId: String) {
        cancelHistoryTask(historyId)
        resultStore.remove(historyId)
            repository.update { current ->
                current.copy(
                    history = current.history.filterNot { it.id == historyId },
                    historyMetadata = current.historyMetadata.filterNot { it.historyId == historyId },
                    lastResult = current.lastResult?.takeUnless { it.id == historyId }
                )
            }
    }

    suspend fun clearHistory() {
        taskRegistry.cancelAll()
        resultStore.clear()
        repository.update { it.copy(history = emptyList(), historyMetadata = emptyList(), lastResult = null) }
    }

    fun mergedHistory(persisted: List<ProcessingResult>): List<ProcessingResult> {
        return resultStore.mergedWith(persisted)
    }

    suspend fun latestHistoryResult(historyId: String): ProcessingResult? {
        return resultStore.latest(historyId)
    }

    suspend fun updateHistoryResult(
        historyId: String,
        transform: (ProcessingResult) -> ProcessingResult
    ): ProcessingResult? {
        return resultStore.update(historyId, transform)
    }

    suspend fun claimAutomationAction(historyId: String): ProcessingResult? {
        return automationDeliveryMutex.withLock {
            val current = resultStore.latest(historyId)
                ?.takeIf { it.automationActionDelivery == AutomationActionDelivery.PENDING }
                ?: return@withLock null
            resultStore.updateNow(historyId) {
                current.copy(automationActionDelivery = AutomationActionDelivery.DELIVERING)
            }
        }
    }

    suspend fun completeAutomationAction(historyId: String) {
        finishAutomationAction(historyId, AutomationActionDelivery.COMPLETED)
    }

    suspend fun failAutomationAction(historyId: String) {
        finishAutomationAction(historyId, AutomationActionDelivery.FAILED)
    }

    suspend fun abandonAutomationAction(historyId: String) {
        finishAutomationAction(historyId, AutomationActionDelivery.ABANDONED)
    }

    private suspend fun finishAutomationAction(
        historyId: String,
        delivery: AutomationActionDelivery
    ) {
        automationDeliveryMutex.withLock {
            resultStore.updateNow(historyId) { result ->
                if (result.automationActionDelivery == AutomationActionDelivery.DELIVERING) {
                    result.copy(automationActionDelivery = delivery)
                } else {
                    result
                }
            }
        }
    }

    suspend fun reconcileInterruptedTasks() {
        val runningTasks = tasks.value.values.filter { it.status == WorkflowTaskStatus.RUNNING }
        repository.update { current ->
            val reconciledHistory = current.history.map { result ->
                result.reconcileInterruptedState(runningTasks).reconcileAutomationDelivery()
            }
            val reconciledById = reconciledHistory.associateBy(ProcessingResult::id)
            current.copy(
                history = reconciledHistory,
                lastResult = current.lastResult?.let { last ->
                    reconciledById[last.id]
                        ?: last.reconcileInterruptedState(runningTasks).reconcileAutomationDelivery()
                }
            )
        }
        val reconciled = repository.read()
        (reconciled.history + listOfNotNull(reconciled.lastResult))
            .distinctBy(ProcessingResult::id)
            .filter { result ->
                result.automationAction != null &&
                    result.automationActionDelivery == AutomationActionDelivery.PENDING
            }
            .forEach(resultStore::restore)
    }

    private fun failureResult(base: ProcessingResult, error: Throwable): ProcessingResult {
        val isTimeout = error is TimeoutCancellationException
        val message = error.message?.ifBlank { null } ?: if (isTimeout) "请求超时（90 秒）" else "处理失败"
        return base.copy(
            status = if (isTimeout) ProcessingStatus.TIMEOUT else ProcessingStatus.ERROR,
            detail = message,
            events = base.events + ProcessingEvent(
                title = if (isTimeout) "请求超时" else "请求失败",
                detail = message
            )
        )
    }

    private fun cancellationResult(base: ProcessingResult): ProcessingResult {
        return base.copy(
            status = ProcessingStatus.ERROR,
            detail = CANCELLATION_MESSAGE,
            events = base.events + ProcessingEvent(
                title = "请求已取消",
                detail = CANCELLATION_MESSAGE
            )
        )
    }

    private fun ProcessingResult.reconcileInterruptedState(
        runningTasks: List<WorkflowTaskState>
    ): ProcessingResult {
        val hasActivePrimaryTask = runningTasks.any { task ->
            task.historyId == id && task.kind != WorkflowTaskKind.CONVERSATION
        }
        val reconciledTurns = followUpTurns.map { turn ->
            val hasActiveTurn = runningTasks.any { task ->
                task.historyId == id &&
                    task.kind == WorkflowTaskKind.CONVERSATION &&
                    task.conversationTurnId == turn.id
            }
            if (!turn.completed && !hasActiveTurn) {
                turn.withAssistantError(INTERRUPTION_MESSAGE)
            } else {
                turn
            }
        }
        if (status != ProcessingStatus.RUNNING || hasActivePrimaryTask) {
            return copy(followUpTurns = reconciledTurns)
        }
        return copy(
            status = ProcessingStatus.ERROR,
            detail = INTERRUPTION_MESSAGE,
            followUpTurns = reconciledTurns,
            events = events + ProcessingEvent(
                title = "任务已中断",
                detail = INTERRUPTION_MESSAGE
            )
        )
    }

    private fun ProcessingResult.reconcileAutomationDelivery(): ProcessingResult {
        return if (automationActionDelivery == AutomationActionDelivery.DELIVERING) {
            copy(automationActionDelivery = AutomationActionDelivery.ABANDONED)
        } else {
            this
        }
    }

    private companion object {
        const val CANCELLATION_MESSAGE = "请求已取消"
        const val INTERRUPTION_MESSAGE = "应用进程中断，任务未能完成"
    }
}

private suspend fun WorkflowResultStore.updateConversationTurn(
    historyId: String,
    turnId: String,
    transform: (FollowUpTurn) -> FollowUpTurn
): ProcessingResult? {
    return update(historyId) { result ->
        result.copy(
            followUpTurns = result.followUpTurns.map { turn ->
                if (turn.id == turnId) transform(turn) else turn
            }
        )
    }
}

private suspend fun WorkflowResultStore.updateConversationTurnNow(
    historyId: String,
    turnId: String,
    transform: (FollowUpTurn) -> FollowUpTurn
): ProcessingResult? {
    return updateNow(historyId) { result ->
        result.copy(
            followUpTurns = result.followUpTurns.map { turn ->
                if (turn.id == turnId) transform(turn) else turn
            }
        )
    }
}

private fun ProcessingResult.withStartedAnswerVersionFrom(previous: ProcessingResult): ProcessingResult {
    val versions = previous.displayedAnswerVersions() + AnswerVersion("")
    return copy(answer = "", answerVersions = versions)
}

private fun ProcessingResult.withUpdatedAnswerVersion(
    index: Int,
    text: String
): ProcessingResult {
    val versions = answerVersions.ifEmpty { displayedAnswerVersions() }
    return copy(
        answer = text,
        answerVersions = versions.replaceAt(index, AnswerVersion(text))
    )
}

private fun List<AnswerVersion>.replaceAt(index: Int, value: AnswerVersion): List<AnswerVersion> {
    return mapIndexed { currentIndex, currentValue ->
        if (currentIndex == index) value else currentValue
    }
}
