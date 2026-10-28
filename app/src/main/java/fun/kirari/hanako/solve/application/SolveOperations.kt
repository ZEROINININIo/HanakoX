package `fun`.kirari.hanako.solve.application

import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.ModelSelection
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.QuotedFragment
import `fun`.kirari.hanako.solve.model.WorkflowTaskState
import `fun`.kirari.hanako.solve.model.WorkflowTaskKind
import `fun`.kirari.hanako.solve.model.WorkflowTaskStatus
import `fun`.kirari.hanako.solve.model.ConversationIntent
import `fun`.kirari.hanako.core.model.loadHistoryBitmaps
import `fun`.kirari.hanako.solve.runtime.WorkflowTaskManager
import `fun`.kirari.hanako.solve.workflow.ProcessingPipeline
import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull

internal data class SolveTaskSnapshot(
    val task: WorkflowTaskState,
    val result: ProcessingResult?
)

internal data class SolveTaskHandle(
    val taskId: String,
    val updates: Flow<SolveTaskSnapshot>
)

internal data class ActiveSolveTask(
    val taskId: String,
    val historyId: String,
    val kind: WorkflowTaskKind,
    val answerVersionIndex: Int?,
    val conversationTurnId: String?
)

internal data class SolveHistoryState(
    val results: List<ProcessingResult>,
    val activeTasks: Map<String, ActiveSolveTask>
)

internal class SolveOperations(
    private val pipeline: ProcessingPipeline,
    private val taskManager: WorkflowTaskManager
) {
    private val tag = "HanakoSolveOperations"
    val pendingAutomationResults: StateFlow<Map<String, ProcessingResult>> =
        taskManager.pendingAutomationResults

    fun startAnswer(settings: AppSettings, bitmaps: List<Bitmap>): SolveTaskHandle {
        require(bitmaps.isNotEmpty()) { "没有可处理的截图" }
        val taskId = taskManager.startAnswerTask(
            models = pipeline.resolveModels(settings),
            bitmaps = bitmaps
        )
        return taskHandle(taskId)
    }

    fun startAutomation(settings: AppSettings, bitmaps: List<Bitmap>): SolveTaskHandle {
        require(bitmaps.isNotEmpty()) { "没有可处理的截图" }
        val taskId = taskManager.startAutomationTask(
            models = pipeline.resolveModels(settings),
            bitmaps = bitmaps
        )
        return taskHandle(taskId)
    }

    suspend fun regenerate(settings: AppSettings, historyId: String): SolveTaskHandle? {
        val existing = taskManager.latestHistoryResult(historyId) ?: return null
        val bitmaps = existing.loadHistoryBitmaps()
        return runCatching { regenerate(settings, existing, bitmaps) }
            .getOrElse { error ->
                AppDebugLogStore.e(tag, "regenerate start failed historyId=$historyId", error)
                null
            }
    }

    fun regenerate(
        settings: AppSettings,
        existing: ProcessingResult,
        bitmaps: List<Bitmap>
    ): SolveTaskHandle? {
        if (existing.automationAction != null || taskManager.isRunning(existing.id)) return null
        require(bitmaps.isNotEmpty()) { "找不到原始截图，无法重新生成" }
        val models = pipeline.resolveModels(settings)
        val taskId = taskManager.startRegenerateAnswerTask(
            existingResult = existing.copy(followUpTurns = emptyList()),
            models = models,
            bitmaps = bitmaps
        )
        return taskHandle(taskId)
    }

    suspend fun continueConversation(
        settings: AppSettings,
        historyId: String,
        prompt: String,
        quotedFragments: List<QuotedFragment> = emptyList(),
        modelSelection: ModelSelection? = null
    ) {
        startConversation(settings, historyId, ConversationIntent.NewTurn(prompt, quotedFragments), modelSelection)
    }

    suspend fun retryLatestConversation(
        settings: AppSettings,
        historyId: String,
        modelSelection: ModelSelection? = null
    ) {
        startConversation(settings, historyId, ConversationIntent.RegenerateLatest, modelSelection)
    }

    private suspend fun startConversation(
        settings: AppSettings,
        historyId: String,
        intent: ConversationIntent,
        modelSelection: ModelSelection?
    ) {
        val existing = taskManager.latestHistoryResult(historyId) ?: return
        if (taskManager.isRunning(historyId)) return
        val normalizedIntent = when (intent) {
            is ConversationIntent.NewTurn -> {
                val prompt = intent.prompt.trim()
                if (prompt.isBlank()) return
                ConversationIntent.NewTurn(prompt, intent.quotedFragments)
            }
            ConversationIntent.RegenerateLatest -> {
                if (existing.followUpTurns.isEmpty()) return
                ConversationIntent.RegenerateLatest
            }
        }
        val conversationSettings = settings.withConversationModel(existing.route, modelSelection)
        val models = resolveModels(conversationSettings, historyId)?.copy(route = existing.route) ?: return
        taskManager.startConversationTask(
            existingResult = existing,
            models = models,
            intent = normalizedIntent
        )
    }

    fun cancelTask(taskId: String) {
        taskManager.cancelTask(taskId)
    }

    suspend fun clearHistory() {
        taskManager.clearHistory()
    }

    suspend fun removeHistoryResult(historyId: String) {
        taskManager.removeHistoryResult(historyId)
    }

    suspend fun completeAutomationAction(historyId: String) {
        taskManager.completeAutomationAction(historyId)
    }

    suspend fun claimAutomationAction(historyId: String): ProcessingResult? {
        return taskManager.claimAutomationAction(historyId)
    }

    suspend fun failAutomationAction(historyId: String) {
        taskManager.failAutomationAction(historyId)
    }

    suspend fun abandonAutomationAction(historyId: String) {
        taskManager.abandonAutomationAction(historyId)
    }

    fun observeHistory(persistedHistory: Flow<List<ProcessingResult>>): Flow<SolveHistoryState> {
        return combine(
            persistedHistory,
            taskManager.liveResults,
            taskManager.tasks
        ) { persisted, _, tasks ->
            val activeTasks = tasks.values
                .filter { it.status == WorkflowTaskStatus.RUNNING }
                .mapNotNull { task ->
                    val historyId = task.historyId ?: return@mapNotNull null
                    historyId to ActiveSolveTask(
                        taskId = task.taskId,
                        historyId = historyId,
                        kind = task.kind,
                        answerVersionIndex = task.answerVersionIndex,
                        conversationTurnId = task.conversationTurnId
                    )
                }
                .toMap()
            SolveHistoryState(
                results = taskManager.mergedHistory(persisted),
                activeTasks = activeTasks
            )
        }
    }

    private fun taskHandle(taskId: String): SolveTaskHandle {
        val updates = combine(taskManager.tasks, taskManager.liveResults) { tasks, results ->
            val task = tasks[taskId] ?: return@combine null
            SolveTaskSnapshot(
                task = task,
                result = task.historyId?.let(results::get)
            )
        }.mapNotNull { it }
        return SolveTaskHandle(taskId = taskId, updates = updates)
    }

    private fun resolveModels(
        settings: AppSettings,
        historyId: String
    ): ProcessingPipeline.ResolvedModels? {
        return runCatching { pipeline.resolveModels(settings) }
            .getOrElse { error ->
                AppDebugLogStore.e(tag, "resolve models failed historyId=$historyId", error)
                null
            }
    }
}

internal fun AppSettings.withConversationModel(
    route: `fun`.kirari.hanako.core.model.ProcessingRoute,
    selection: ModelSelection?
): AppSettings {
    if (selection == null) return this
    return when (route) {
        `fun`.kirari.hanako.core.model.ProcessingRoute.OCR_THEN_LLM ->
            copy(textModelSelection = selection)
        `fun`.kirari.hanako.core.model.ProcessingRoute.MULTIMODAL_DIRECT ->
            copy(visionModelSelection = selection)
    }
}
