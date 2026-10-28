package `fun`.kirari.hanako.solve.runtime

import android.graphics.Bitmap
import `fun`.kirari.hanako.solve.model.AutomationResult
import `fun`.kirari.hanako.core.model.AnswerVersion
import `fun`.kirari.hanako.core.model.AutomationActionRecord
import `fun`.kirari.hanako.core.model.AutomationActionType
import `fun`.kirari.hanako.core.model.AutomationActionDelivery
import `fun`.kirari.hanako.core.model.ProcessingEvent
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.model.ProcessingStatus
import `fun`.kirari.hanako.core.model.FollowUpTurn
import `fun`.kirari.hanako.core.model.displayedAssistantVersions
import `fun`.kirari.hanako.core.model.latestAssistantText
import `fun`.kirari.hanako.solve.model.WorkflowTaskKind
import `fun`.kirari.hanako.solve.model.WorkflowTaskStatus
import `fun`.kirari.hanako.solve.model.ConversationIntent
import `fun`.kirari.hanako.core.data.WebSearchSettings
import `fun`.kirari.hanako.core.data.defaultAssistant
import `fun`.kirari.hanako.core.data.defaultProvider
import `fun`.kirari.hanako.solve.workflow.ProcessingPipeline
import `fun`.kirari.hanako.solve.workflow.AnswerNodeOutput
import `fun`.kirari.hanako.solve.workflow.AnswerWorkflowOutput
import `fun`.kirari.hanako.solve.workflow.AutomationNodeOutput
import `fun`.kirari.hanako.solve.workflow.AutomationWorkflowOutput
import `fun`.kirari.hanako.solve.workflow.CapturedImages
import `fun`.kirari.hanako.solve.workflow.HanakoWorkflowEngine
import `fun`.kirari.hanako.solve.workflow.ConversationWorkflowEngine
import `fun`.kirari.hanako.solve.workflow.PreparedConversationTurn
import `fun`.kirari.hanako.solve.workflow.OcrNodeOutput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkflowTaskManagerTest {

    @Test
    fun conversationTask_streamsIntoUnifiedResultAndTaskState() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        val existing = testProcessingResult(id = "history-1", answer = "initial")
        harness.conversation.deltas = listOf("follow", " up")

        harness.manager.startConversationTask(
            existingResult = existing,
            models = testModels(),
            intent = ConversationIntent.NewTurn("why?")
        )
        advanceUntilIdle()

        val completed = harness.resultStore.liveResults.value.getValue("history-1")
        assertEquals(1, completed.followUpTurns.size)
        assertEquals("why?", completed.followUpTurns.single().userText)
        assertEquals("follow up", completed.followUpTurns.single().latestAssistantText())
        assertEquals(listOf("follow up"), completed.followUpTurns.single().assistantVersions.map { it.text })
        assertTrue(completed.followUpTurns.single().completed)
        val task = harness.manager.tasks.value.values.single()
        assertEquals(WorkflowTaskKind.CONVERSATION, task.kind)
        assertEquals(WorkflowTaskStatus.SUCCESS, task.status)
        assertEquals(completed.followUpTurns.single().id, task.conversationTurnId)
    }

    @Test
    fun conversationTask_persistsCompletedTurnBeforeTaskFinishes() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        val existing = testProcessingResult(id = "history-1", answer = "initial")
        harness.conversation.deltas = listOf("persisted answer")

        harness.manager.startConversationTask(
            existingResult = existing,
            models = testModels(),
            intent = ConversationIntent.NewTurn("why?")
        )
        runCurrent()

        val persistedTurn = harness.repository.settings.history.single().followUpTurns.single()
        assertEquals("persisted answer", persistedTurn.latestAssistantText())
        assertEquals(listOf("persisted answer"), persistedTurn.assistantVersions.map { it.text })
        assertTrue(persistedTurn.completed)
        assertEquals(WorkflowTaskStatus.SUCCESS, harness.manager.tasks.value.values.single().status)
    }

    @Test
    fun conversationRetry_preservesLatestTurnVersions() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        val existing = testProcessingResult(id = "history-1", answer = "initial").copy(
            followUpTurns = listOf(
                FollowUpTurn(userText = "first", assistantText = "one", completed = true),
                FollowUpTurn(userText = "retry me", assistantText = "old answer", completed = true)
            )
        )
        harness.conversation.deltas = listOf("recovered")

        harness.manager.startConversationTask(
            existingResult = existing,
            models = testModels(),
            intent = ConversationIntent.RegenerateLatest
        )
        advanceUntilIdle()

        val turns = harness.resultStore.liveResults.value.getValue("history-1").followUpTurns
        assertEquals(listOf("first", "retry me"), turns.map { it.userText })
        assertEquals("recovered", turns.last().latestAssistantText())
        assertEquals(listOf("old answer", "recovered"), turns.last().assistantVersions.map { it.text })
    }

    @Test
    fun regenerate_createsNewAnswerVersionBeforeWorkflowCompletes() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        val existing = testProcessingResult(
            id = "history-1",
            answer = "old answer",
            screenshotPaths = listOf("history-1.png")
        )
        harness.engine.answerGate = CompletableDeferred()
        harness.engine.answerDeltas["history-1"] = emptyList()
        harness.engine.finalAnswers["history-1"] = "new answer"

        harness.manager.startRegenerateAnswerTask(
            existingResult = existing,
            models = testModels(),
            bitmaps = emptyList()
        )
        runCurrent()

        val live = harness.resultStore.liveResults.value.getValue("history-1")
        assertEquals(listOf("old answer", ""), live.answerVersions.map { it.text })
        assertEquals(1, harness.manager.tasks.value.values.single().answerVersionIndex)
        assertTrue(harness.manager.isRunning("history-1"))

        harness.engine.answerGate?.complete(Unit)
        advanceUntilIdle()

        val completed = harness.resultStore.liveResults.value.getValue("history-1")
        assertEquals(ProcessingStatus.SUCCESS, completed.status)
        assertEquals("new answer", completed.answerVersions.last().text)
    }

    @Test
    fun regenerate_registersSynchronouslyAndRejectsImmediateDuplicateStart() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        val existing = testProcessingResult(id = "history-1", answer = "old answer")
        harness.engine.answerGate = CompletableDeferred()

        harness.manager.startRegenerateAnswerTask(existing, testModels(), emptyList())
        harness.manager.startRegenerateAnswerTask(existing, testModels(), emptyList())

        assertEquals(1, harness.manager.tasks.value.size)
        assertTrue(harness.manager.isRunning(existing.id))

        harness.manager.cancelHistoryTask(existing.id)
        advanceUntilIdle()
    }

    @Test
    fun answerTasks_keepConcurrentProgressIsolatedByHistoryId() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        harness.engine.generatedHistoryIds += listOf("history-1", "history-2")
        harness.engine.answerDeltas["history-1"] = listOf("A")
        harness.engine.answerDeltas["history-2"] = listOf("B")

        harness.manager.startAnswerTask(testModels(), emptyList())
        harness.manager.startAnswerTask(testModels(), emptyList())
        advanceUntilIdle()

        assertEquals("A", harness.resultStore.liveResults.value.getValue("history-1").answer)
        assertEquals("B", harness.resultStore.liveResults.value.getValue("history-2").answer)
        assertEquals(
            setOf(WorkflowTaskStatus.SUCCESS),
            harness.manager.tasks.value.values.map { it.status }.toSet()
        )
    }

    @Test
    fun answerTask_registersAtTopLevelBeforeHistoryIdIsPrepared() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        harness.engine.generatedHistoryIds += "history-1"
        harness.engine.answerGate = CompletableDeferred()

        val taskId = harness.manager.startAnswerTask(testModels(), emptyList())

        val registered = harness.manager.tasks.value.getValue(taskId)
        assertEquals(WorkflowTaskKind.ANSWER, registered.kind)
        assertNull(registered.historyId)

        runCurrent()
        assertEquals("history-1", harness.manager.tasks.value.getValue(taskId).historyId)
        harness.manager.cancelTask(taskId)
        advanceUntilIdle()
    }

    @Test
    fun answerTask_continuesAfterUiStateObserversAreCancelled() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        harness.engine.generatedHistoryIds += "history-1"
        harness.engine.answerDeltas["history-1"] = listOf("answer")
        harness.engine.answerGate = CompletableDeferred()
        val observer = backgroundScope.launch {
            harness.manager.liveResults.collect()
        }

        harness.manager.startAnswerTask(testModels(), emptyList())
        runCurrent()
        observer.cancel()
        harness.engine.answerGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(ProcessingStatus.SUCCESS, harness.repository.settings.history.single().status)
        assertEquals("answer", harness.repository.settings.history.single().answer)
        assertEquals(WorkflowTaskStatus.SUCCESS, harness.manager.tasks.value.values.single().status)
    }

    @Test
    fun automationAction_isPersistentlyClaimedBeforePlatformAdapterCompletesIt() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        harness.engine.generatedHistoryIds += "history-1"

        harness.manager.startAutomationTask(testModels(), emptyList())
        advanceUntilIdle()

        val pending = harness.manager.pendingAutomationResults.value.getValue("history-1")
        assertEquals(AutomationActionDelivery.PENDING, pending.automationActionDelivery)

        val claimed = harness.manager.claimAutomationAction("history-1")

        assertEquals(AutomationActionDelivery.DELIVERING, claimed?.automationActionDelivery)
        assertEquals(
            AutomationActionDelivery.DELIVERING,
            harness.repository.settings.history.single().automationActionDelivery
        )
        runCurrent()
        assertTrue(harness.manager.pendingAutomationResults.value.isEmpty())

        assertEquals(null, harness.manager.claimAutomationAction("history-1"))
        harness.manager.completeAutomationAction("history-1")

        assertTrue(harness.manager.pendingAutomationResults.value.isEmpty())
        assertEquals(
            AutomationActionDelivery.COMPLETED,
            harness.repository.settings.history.single().automationActionDelivery
        )
        harness.manager.abandonAutomationAction("history-1")
        assertEquals(
            AutomationActionDelivery.COMPLETED,
            harness.repository.settings.history.single().automationActionDelivery
        )
    }

    @Test
    fun reconcileInterruptedTasks_restoresPersistedPendingAutomationAction() = runTest {
        val pending = testProcessingResult(id = "history-1").copy(
            automationAction = AutomationActionRecord(AutomationActionType.SET_CLIPBOARD, "answer"),
            automationActionDelivery = AutomationActionDelivery.PENDING
        )
        val harness = ManagerHarness(
            testScope = TestScope(testScheduler),
            initialSettings = `fun`.kirari.hanako.core.data.AppSettings(
                history = listOf(pending),
                lastResult = pending
            )
        )

        harness.manager.reconcileInterruptedTasks()
        runCurrent()

        assertEquals("history-1", harness.manager.pendingAutomationResults.value.keys.single())
    }

    @Test
    fun reconcileInterruptedTasks_marksClaimedAutomationActionAsAbandonedWithoutReplay() = runTest {
        val delivering = testProcessingResult(id = "history-1").copy(
            automationAction = AutomationActionRecord(AutomationActionType.SET_CLIPBOARD, "answer"),
            automationActionDelivery = AutomationActionDelivery.DELIVERING
        )
        val harness = ManagerHarness(
            testScope = TestScope(testScheduler),
            initialSettings = `fun`.kirari.hanako.core.data.AppSettings(
                history = listOf(delivering),
                lastResult = delivering
            )
        )

        harness.manager.reconcileInterruptedTasks()
        runCurrent()

        assertTrue(harness.manager.pendingAutomationResults.value.isEmpty())
        assertEquals(
            AutomationActionDelivery.ABANDONED,
            harness.repository.settings.history.single().automationActionDelivery
        )
        assertEquals(
            AutomationActionDelivery.ABANDONED,
            harness.repository.settings.lastResult?.automationActionDelivery
        )
    }

    @Test
    fun automationAction_failureAndCancellationHaveDistinctDurableOutcomes() = runTest {
        val pending = testProcessingResult(id = "history-1").copy(
            automationAction = AutomationActionRecord(AutomationActionType.SET_CLIPBOARD, "answer"),
            automationActionDelivery = AutomationActionDelivery.PENDING
        )
        val harness = ManagerHarness(
            testScope = TestScope(testScheduler),
            initialSettings = `fun`.kirari.hanako.core.data.AppSettings(history = listOf(pending))
        )
        harness.resultStore.restore(pending)

        harness.manager.claimAutomationAction("history-1")
        harness.manager.failAutomationAction("history-1")
        assertEquals(
            AutomationActionDelivery.FAILED,
            harness.repository.settings.history.single().automationActionDelivery
        )

        val secondPending = pending.copy(id = "history-2")
        harness.resultStore.upsert(secondPending)
        harness.manager.claimAutomationAction("history-2")
        harness.manager.abandonAutomationAction("history-2")
        assertEquals(
            AutomationActionDelivery.ABANDONED,
            harness.repository.settings.history.first { it.id == "history-2" }.automationActionDelivery
        )
    }

    @Test
    fun answerCompletion_keepsAccumulatedDeltasWhenFinalOutputIsBlank() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        harness.engine.generatedHistoryIds += "history-1"
        harness.engine.answerDeltas["history-1"] = listOf("A", "B")
        harness.engine.finalAnswers["history-1"] = ""

        harness.manager.startAnswerTask(testModels(), emptyList())
        advanceUntilIdle()

        val completed = harness.resultStore.liveResults.value.getValue("history-1")
        assertEquals("AB", completed.answer)
        assertEquals(listOf("AB"), completed.answerVersions.map { it.text })
        assertEquals("AB", harness.repository.settings.history.single().answer)
    }

    @Test
    fun removeHistoryResult_cancelsRunningTaskAndPreventsLateResultResurrection() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        harness.engine.generatedHistoryIds += "history-1"
        harness.engine.answerDeltas["history-1"] = listOf("partial")
        harness.engine.answerGate = CompletableDeferred()

        harness.manager.startAnswerTask(testModels(), emptyList())
        runCurrent()

        assertEquals("partial", harness.resultStore.liveResults.value.getValue("history-1").answer)

        harness.manager.removeHistoryResult("history-1")
        runCurrent()
        harness.engine.answerGate?.complete(Unit)
        advanceUntilIdle()

        assertNull(harness.resultStore.liveResults.value["history-1"])
        assertFalse(harness.repository.settings.history.any { it.id == "history-1" })
        assertEquals(WorkflowTaskStatus.CANCELLED, harness.manager.tasks.value.values.single().status)
    }

    @Test
    fun cancelAnswerTask_persistsTerminalCancelledResult() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        harness.engine.generatedHistoryIds += "history-1"
        harness.engine.answerDeltas["history-1"] = listOf("partial")
        harness.engine.answerGate = CompletableDeferred()

        val taskId = harness.manager.startAnswerTask(testModels(), emptyList())
        runCurrent()
        harness.manager.cancelTask(taskId)
        advanceUntilIdle()

        val cancelled = harness.resultStore.liveResults.value.getValue("history-1")
        assertEquals(ProcessingStatus.ERROR, cancelled.status)
        assertEquals("请求已取消", cancelled.detail)
        assertEquals(ProcessingStatus.ERROR, harness.repository.settings.history.single().status)
        assertEquals(WorkflowTaskStatus.CANCELLED, harness.manager.tasks.value.getValue(taskId).status)
    }

    @Test
    fun cancelRegenerateTask_restoresResultFromBeforeRegeneration() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        val existing = testProcessingResult(id = "history-1", answer = "old answer").copy(
            status = ProcessingStatus.SUCCESS,
            detail = "done",
            answerVersions = listOf(AnswerVersion("old answer"))
        )
        harness.engine.answerGate = CompletableDeferred()

        val taskId = harness.manager.startRegenerateAnswerTask(
            existingResult = existing,
            models = testModels(),
            bitmaps = emptyList()
        )
        runCurrent()
        harness.manager.cancelTask(taskId)
        advanceUntilIdle()

        assertEquals(existing, harness.resultStore.liveResults.value.getValue(existing.id))
        assertEquals(existing, harness.repository.settings.history.single())
    }

    @Test
    fun cancelConversationTask_completesPendingTurnWithCancellationError() = runTest {
        val harness = ManagerHarness(testScope = TestScope(testScheduler))
        val existing = testProcessingResult(id = "history-1", answer = "initial")
        harness.conversation.deltas = listOf("partial")
        harness.conversation.gate = CompletableDeferred()

        val taskId = harness.manager.startConversationTask(
            existingResult = existing,
            models = testModels(),
            intent = ConversationIntent.NewTurn("why?")
        )
        runCurrent()
        harness.manager.cancelTask(taskId)
        advanceUntilIdle()

        val turn = harness.resultStore.liveResults.value.getValue(existing.id).followUpTurns.single()
        assertTrue(turn.completed)
        assertEquals("", turn.pendingAssistantText)
        assertEquals("请求已取消", turn.errorMessage)
        assertTrue(harness.repository.settings.history.single().followUpTurns.single().completed)
    }

    @Test
    fun reconcileInterruptedTasks_marksPersistedTransientStatesAsTerminal() = runTest {
        val running = testProcessingResult(id = "running").copy(
            status = ProcessingStatus.RUNNING,
            followUpTurns = listOf(FollowUpTurn(userText = "pending", pendingAssistantText = "partial"))
        )
        val completed = testProcessingResult(id = "completed").copy(
            status = ProcessingStatus.SUCCESS,
            followUpTurns = listOf(FollowUpTurn(userText = "pending follow-up"))
        )
        val harness = ManagerHarness(
            testScope = TestScope(testScheduler),
            initialSettings = `fun`.kirari.hanako.core.data.AppSettings(
                history = listOf(running, completed),
                lastResult = running
            )
        )

        harness.manager.reconcileInterruptedTasks()

        val reconciledRunning = harness.repository.settings.history.first { it.id == "running" }
        val reconciledCompleted = harness.repository.settings.history.first { it.id == "completed" }
        assertEquals(ProcessingStatus.ERROR, reconciledRunning.status)
        assertEquals("应用进程中断，任务未能完成", reconciledRunning.detail)
        assertTrue(reconciledRunning.followUpTurns.single().completed)
        assertEquals("", reconciledRunning.followUpTurns.single().pendingAssistantText)
        assertEquals("应用进程中断，任务未能完成", reconciledRunning.followUpTurns.single().errorMessage)
        assertEquals(ProcessingStatus.SUCCESS, reconciledCompleted.status)
        assertTrue(reconciledCompleted.followUpTurns.single().completed)
        assertEquals(ProcessingStatus.ERROR, harness.repository.settings.lastResult?.status)
    }
}

private class ManagerHarness(
    testScope: TestScope,
    initialSettings: `fun`.kirari.hanako.core.data.AppSettings = `fun`.kirari.hanako.core.data.AppSettings()
) {
    val repository = InMemoryWorkflowHistoryRepository(initialSettings)
    val resultStore = WorkflowResultStore(
        repository = repository,
        scope = testScope,
        persistDelayMillis = 250L
    )
    val engine = FakeHanakoWorkflowEngine()
    val conversation = FakeConversationWorkflow()
    val taskRegistry = WorkflowTaskRegistry()
    val manager = WorkflowTaskManager(
        repository = repository,
        resultStore = resultStore,
        taskRegistry = taskRegistry,
        workflowFactory = engine,
        conversationWorkflow = conversation,
        scope = testScope,
        processingTimeoutMillis = 90_000L
    )
}

private class FakeConversationWorkflow : ConversationWorkflowEngine {
    var deltas: List<String> = emptyList()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun prepareTurn(
        existingResult: ProcessingResult,
        models: ProcessingPipeline.ResolvedModels,
        intent: ConversationIntent,
        turnId: String
    ): PreparedConversationTurn {
        val (prompt, retainedTurns, retainedVersions) = when (intent) {
            is ConversationIntent.NewTurn -> Triple(intent.prompt, existingResult.followUpTurns, emptyList())
            ConversationIntent.RegenerateLatest -> {
                val retriedTurn = existingResult.followUpTurns.last()
                Triple(
                    retriedTurn.userText,
                    existingResult.followUpTurns.dropLast(1),
                    retriedTurn.displayedAssistantVersions()
                )
            }
        }
        val started = existingResult.copy(
            followUpTurns = retainedTurns + FollowUpTurn(
                id = turnId,
                userText = prompt,
                assistantVersions = retainedVersions
            )
        )
        return PreparedConversationTurn(
            historyId = existingResult.id,
            turnId = turnId,
            startedResult = started,
            provider = requireNotNull(models.textProvider),
            model = models.textModel,
            messages = emptyList(),
            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = false
        )
    }

    override suspend fun runTurn(
        turn: PreparedConversationTurn,
        onAnswerDelta: suspend (String) -> Unit
    ): String {
        deltas.forEach { onAnswerDelta(it) }
        gate?.await()
        return deltas.joinToString("")
    }
}

private class FakeHanakoWorkflowEngine : HanakoWorkflowEngine {
    val generatedHistoryIds = ArrayDeque<String>()
    val answerDeltas = mutableMapOf<String, List<String>>()
    val finalAnswers = mutableMapOf<String, String>()
    var answerGate: CompletableDeferred<Unit>? = null

    override suspend fun prepareBaseResult(
        models: ProcessingPipeline.ResolvedModels,
        bitmaps: List<Bitmap>,
        detail: String
    ): Pair<ProcessingResult, CapturedImages> {
        val historyId = generatedHistoryIds.removeFirstOrNull() ?: "history-${generatedHistoryIds.size + 1}"
        return baseResult(historyId, models, detail) to CapturedImages(
            historyId = historyId,
            bitmaps = bitmaps,
            screenshotPaths = listOf("$historyId.png")
        )
    }

    override fun prepareRegenerationBaseResult(
        existingResult: ProcessingResult,
        models: ProcessingPipeline.ResolvedModels,
        bitmaps: List<Bitmap>,
        detail: String
    ): Pair<ProcessingResult, CapturedImages> {
        return existingResult.copy(
            assistantName = models.assistant.name,
            route = models.route,
            status = ProcessingStatus.RUNNING,
            modelSummary = "test-model",
            detail = detail,
            extractedText = "",
            checkpoints = emptyList()
        ) to CapturedImages(
            historyId = existingResult.id,
            bitmaps = bitmaps,
            screenshotPaths = existingResult.allScreenshotPaths
        )
    }

    override suspend fun runAnswerWorkflow(
        models: ProcessingPipeline.ResolvedModels,
        capturedImages: CapturedImages,
        onOcrDelta: suspend (String) -> Unit,
        onAnswerDelta: suspend (String) -> Unit,
        onProgressEvent: suspend (ProcessingEvent) -> Unit
    ): AnswerWorkflowOutput {
        val historyId = capturedImages.historyId
        onOcrDelta("ocr-$historyId")
        answerDeltas[historyId].orEmpty().forEach { onAnswerDelta(it) }
        answerGate?.await()
        val finalAnswer = finalAnswers[historyId] ?: answerDeltas[historyId].orEmpty().joinToString("")
        return AnswerWorkflowOutput(
            capturedImages = capturedImages,
            ocrOutput = OcrNodeOutput(text = "ocr-$historyId", pageTexts = listOf("ocr-$historyId"), providerInfo = "fake"),
            answerOutput = AnswerNodeOutput(answer = finalAnswer, searchOutcome = null, messageTraceSummary = "fake"),
            checkpoints = emptyList()
        )
    }

    override suspend fun runAutomationWorkflow(
        models: ProcessingPipeline.ResolvedModels,
        capturedImages: CapturedImages,
        onOcrDelta: suspend (String) -> Unit,
        onThoughtDelta: suspend (String) -> Unit,
        onProgressEvent: suspend (ProcessingEvent) -> Unit
    ): AutomationWorkflowOutput {
        return AutomationWorkflowOutput(
            capturedImages = capturedImages,
            ocrOutput = null,
            automationOutput = AutomationNodeOutput(
                automationResult = AutomationResult(
                    thought = "",
                    action = AutomationActionRecord(AutomationActionType.SET_CLIPBOARD, "")
                ),
                searchOutcome = null,
                toolTraceSummary = "fake"
            ),
            checkpoints = emptyList()
        )
    }

    override fun buildAnswerResult(
        base: ProcessingResult,
        models: ProcessingPipeline.ResolvedModels,
        output: AnswerWorkflowOutput,
        progressEvents: List<ProcessingEvent>
    ): ProcessingResult {
        return base.copy(
            status = ProcessingStatus.SUCCESS,
            detail = "done",
            extractedText = output.ocrOutput?.text.orEmpty(),
            answer = output.answerOutput.answer,
            answerVersions = listOf(AnswerVersion(output.answerOutput.answer)),
            events = base.events + progressEvents
        )
    }

    override fun buildAutomationResult(
        base: ProcessingResult,
        models: ProcessingPipeline.ResolvedModels,
        output: AutomationWorkflowOutput,
        progressEvents: List<ProcessingEvent>
    ): Pair<AutomationActionRecord?, ProcessingResult> {
        val action = output.automationOutput.automationResult.action
        return action to base.copy(
            status = ProcessingStatus.SUCCESS,
            automationAction = action,
            automationActionDelivery = AutomationActionDelivery.PENDING,
            automationThought = output.automationOutput.automationResult.thought
        )
    }

    private fun baseResult(
        historyId: String,
        models: ProcessingPipeline.ResolvedModels,
        detail: String
    ): ProcessingResult {
        return ProcessingResult(
            id = historyId,
            assistantName = models.assistant.name,
            route = models.route,
            status = ProcessingStatus.RUNNING,
            modelSummary = "test-model",
            detail = detail,
            screenshotPaths = listOf("$historyId.png")
        )
    }
}

private fun testModels(): ProcessingPipeline.ResolvedModels {
    val provider = defaultProvider()
    return ProcessingPipeline.ResolvedModels(
        assistant = defaultAssistant(),
        ocrProvider = provider,
        ocrModel = "ocr",
        textProvider = provider,
        textModel = "text",
        visionProvider = provider,
        visionModel = "vision",
        firstDeltaTimeoutMillis = 1_000L,
        route = ProcessingRoute.OCR_THEN_LLM,
        usingLocalOcr = false,
        trustAllHttpsCertificates = false,
        webSearchSettings = WebSearchSettings()
    )
}
