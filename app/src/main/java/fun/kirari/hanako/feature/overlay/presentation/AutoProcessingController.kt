package `fun`.kirari.hanako.feature.overlay.presentation

import `fun`.kirari.hanako.feature.overlay.state.AutoRunState
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import `fun`.kirari.hanako.feature.overlay.state.OverlaySheetMode
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import `fun`.kirari.hanako.feature.overlay.state.BubbleEvent
import `fun`.kirari.hanako.feature.overlay.state.BubbleState
import `fun`.kirari.hanako.feature.overlay.state.BubbleStateMachine
import `fun`.kirari.hanako.platform.capture.ScreenCaptureManager
import `fun`.kirari.hanako.core.model.AutomationActionType
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.loadHistoryBitmaps
import `fun`.kirari.hanako.core.model.latestAnswerText
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.solve.application.SolveOperations
import `fun`.kirari.hanako.solve.application.SolveTaskHandle
import `fun`.kirari.hanako.solve.application.SolveTaskSnapshot
import `fun`.kirari.hanako.solve.model.WorkflowTaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AutoProcessingController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<OverlayUiState>,
    private val solveOperations: SolveOperations,
    private val bubbleStateMachine: BubbleStateMachine
) {
    private val tag = "HanakoAutoProcessing"
    private var activeJob: Job? = null
    private var activeWorkflowTaskId: String? = null
    private var activeWorkflowObservation: Job? = null
    private var activeWorkflowBitmap: Bitmap? = null

    fun processFullScreen() {
        AppDebugLogStore.i(tag, "processFullScreen start launchMode=${uiState.value.launchMode}")
        activeJob?.cancel()
        val job = scope.launch {
            uiState.update {
                it.copy(
                    liveOcrText = "",
                    liveAnswerText = "",
                    result = null,
                    error = null,
                    working = true,
                    sheetVisible = false,
                    autoRunState = AutoRunState.RUNNING,
                    autoCopiedLabel = null,
                    pendingVibrationLetters = null
                )
            }
            bubbleStateMachine.dispatch(BubbleEvent.StartProcessing)

            runCatching {
                withContext(Dispatchers.IO) {
                    ScreenCaptureManager.captureLatestBitmap(appContext, uiState.value.settings.screenCaptureMethod)
                }
            }.onSuccess { bitmap ->
                AppDebugLogStore.i(tag, "processFullScreen capture success width=${bitmap.width} height=${bitmap.height}")
                processBitmapsNow(listOf(bitmap))
            }.onFailure { error ->
                if (error is CancellationException) {
                    AppDebugLogStore.i(tag, "processFullScreen cancelled")
                    return@onFailure
                }
                AppDebugLogStore.e(tag, "processFullScreen failed", error)
                uiState.update {
                    it.copy(
                        working = false,
                        autoRunState = AutoRunState.IDLE,
                        pendingVibrationLetters = null,
                        error = error.message ?: "截屏失败"
                    )
                }
                bubbleStateMachine.forceState(BubbleState.Idle)
            }
        }
        activeJob = job
        job.invokeOnCompletion {
            if (activeJob === job) activeJob = null
        }
    }

    fun processBitmaps(bitmaps: List<Bitmap>) {
        activeJob?.cancel()
        val job = scope.launch {
            processBitmapsNow(bitmaps)
        }
        activeJob = job
        job.invokeOnCompletion {
            if (activeJob === job) activeJob = null
        }
    }

    fun cancelActiveProcessing() {
        AppDebugLogStore.i(tag, "cancelActiveProcessing")
        activeJob?.cancel()
        activeJob = null
        activeWorkflowTaskId?.let(solveOperations::cancelTask)
        activeWorkflowTaskId = null
        activeWorkflowObservation?.cancel()
        activeWorkflowObservation = null
        activeWorkflowBitmap = null
        uiState.update {
            it.copy(
                working = false,
                autoRunState = AutoRunState.IDLE,
                autoCopiedLabel = null,
                pendingVibrationLetters = null,
                error = null
            )
        }
        bubbleStateMachine.dispatch(BubbleEvent.CancelProcessing)
        if (bubbleStateMachine.currentState !is BubbleState.Idle) {
            bubbleStateMachine.forceState(BubbleState.Idle)
        }
    }

    private suspend fun processBitmapsNow(bitmaps: List<Bitmap>) {
        val state = uiState.value
        val firstBitmap = bitmaps.firstOrNull() ?: return
        AppDebugLogStore.i(tag, "processBitmaps start launchMode=${state.launchMode} route=${state.settings.processingRoute} bitmapCount=${bitmaps.size}")
        val handle = runCatching {
            if (state.launchMode == CaptureLaunchMode.QUICK_SOLVE) {
                solveOperations.startAnswer(state.settings, bitmaps)
            } else {
                solveOperations.startAutomation(state.settings, bitmaps)
            }
        }.getOrElse { error ->
            uiState.update { it.copy(working = false, autoRunState = AutoRunState.IDLE, error = error.message) }
            bubbleStateMachine.forceState(BubbleState.Idle)
            return
        }
        observeWorkflow(handle, firstBitmap)
    }

    private fun observeWorkflow(handle: SolveTaskHandle, firstBitmap: Bitmap) {
        activeWorkflowObservation?.cancel()
        activeWorkflowTaskId = handle.taskId
        activeWorkflowBitmap = firstBitmap
        val job = scope.launch {
            handle.updates
                .onEach(::applyWorkflowSnapshot)
                .takeWhile { it.task.status == WorkflowTaskStatus.RUNNING }
                .collect {}
        }
        activeWorkflowObservation = job
        job.invokeOnCompletion {
            if (activeWorkflowObservation === job) activeWorkflowObservation = null
        }
    }

    private fun applyWorkflowSnapshot(snapshot: SolveTaskSnapshot) {
        if (snapshot.task.taskId != activeWorkflowTaskId) return
        snapshot.result?.let { result ->
            uiState.update { current ->
                current.copy(
                    result = result,
                    liveOcrText = result.extractedText,
                    liveAnswerText = result.automationThought.ifBlank { result.latestAnswerText() }
                )
            }
        }
        when (snapshot.task.status) {
            WorkflowTaskStatus.RUNNING -> Unit
            WorkflowTaskStatus.SUCCESS -> {
                val result = snapshot.result ?: return
                val firstBitmap = activeWorkflowBitmap ?: return
                finishWorkflowObservation()
                AppDebugLogStore.i(
                    tag,
                    "processBitmaps success resultId=${result.id} action=${result.automationAction?.type}"
                )
                if (result.automationAction == null) {
                    applyAutomationResultWithoutAction(result, firstBitmap)
                } else {
                    uiState.update {
                        it.copy(
                            screenshot = firstBitmap,
                            selectedBitmap = firstBitmap,
                            working = false,
                            result = result,
                            liveAnswerText = result.automationThought,
                            error = null
                        )
                    }
                }
            }
            WorkflowTaskStatus.ERROR -> {
                val message = snapshot.task.errorMessage ?: snapshot.result?.detail ?: "处理失败"
                finishWorkflowObservation()
                AppDebugLogStore.e(tag, "processBitmaps failed: $message")
                uiState.update {
                    it.copy(
                        working = false,
                        autoRunState = AutoRunState.IDLE,
                        pendingVibrationLetters = null,
                        error = message
                    )
                }
                bubbleStateMachine.forceState(BubbleState.Idle)
            }
            WorkflowTaskStatus.CANCELLED -> {
                finishWorkflowObservation()
                AppDebugLogStore.i(tag, "processBitmaps cancelled")
            }
        }
    }

    private fun finishWorkflowObservation() {
        activeWorkflowTaskId = null
        activeWorkflowBitmap = null
    }

    fun presentAutomationEffect(result: ProcessingResult) {
        val action = result.automationAction ?: return
        val firstBitmap = result.loadHistoryBitmaps().firstOrNull() ?: uiState.value.selectedBitmap
        automationTrace(
            "applyAutomationAction type=${action.type} text=${action.text} thought=${result.automationThought} resultId=${result.id}"
        )
        when (action.type) {
            AutomationActionType.SET_CLIPBOARD -> {
                val clipboardText = action.text.takeIf { it.isNotBlank() }
                automationTrace("apply SET_CLIPBOARD clipboardText=${clipboardText.orEmpty()} willCopy=${clipboardText != null}")
                uiState.update {
                    it.copy(
                        screenshot = firstBitmap ?: it.screenshot,
                        selectedBitmap = firstBitmap ?: it.selectedBitmap,
                        working = false,
                        result = result,
                        liveAnswerText = result.automationThought,
                        autoRunState = AutoRunState.COMPLETED,
                        autoCopiedLabel = clipboardText,
                        pendingVibrationLetters = null,
                        error = null
                    )
                }
                bubbleStateMachine.dispatch(BubbleEvent.CopyComplete(action.text))
            }
            AutomationActionType.SHOW_BUBBLE_LETTERS -> {
                automationTrace("apply SHOW_BUBBLE_LETTERS letters=${action.text}")
                uiState.update {
                    it.copy(
                        screenshot = firstBitmap ?: it.screenshot,
                        selectedBitmap = firstBitmap ?: it.selectedBitmap,
                        working = false,
                        result = result,
                        liveAnswerText = result.automationThought,
                        autoRunState = AutoRunState.COMPLETED,
                        autoCopiedLabel = null,
                        pendingVibrationLetters = action.text,
                        error = null
                    )
                }
                bubbleStateMachine.dispatch(BubbleEvent.LettersComplete(action.text))
            }
        }
    }

    private fun applyAutomationResultWithoutAction(
        result: ProcessingResult,
        firstBitmap: Bitmap
    ) {
        automationTrace(
            "applyAutomationResultWithoutAction thought=${result.automationThought} resultId=${result.id}"
        )
        uiState.update {
            it.copy(
                screenshot = firstBitmap,
                selectedBitmap = firstBitmap,
                working = false,
                result = result,
                liveAnswerText = result.automationThought,
                sheetVisible = it.launchMode == CaptureLaunchMode.QUICK_SOLVE,
                sheetMode = if (it.launchMode == CaptureLaunchMode.QUICK_SOLVE) OverlaySheetMode.RESULT else it.sheetMode,
                autoRunState = AutoRunState.COMPLETED,
                autoCopiedLabel = null,
                pendingVibrationLetters = null,
                error = null
            )
        }
        bubbleStateMachine.forceState(BubbleState.Idle)
    }

    private fun automationTrace(message: String) {
        val tag = "HanakoAutomationTrace"
        message.chunked(3500).forEach { chunk ->
            Log.i(tag, chunk)
            AppDebugLogStore.i(tag, chunk)
        }
    }
}
