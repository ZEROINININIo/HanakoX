package `fun`.kirari.hanako.feature.overlay.presentation

import android.graphics.Bitmap
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.feature.overlay.state.AutoRunState
import `fun`.kirari.hanako.feature.overlay.state.BubbleState
import `fun`.kirari.hanako.feature.overlay.state.BubbleStateMachine
import `fun`.kirari.hanako.feature.overlay.state.OverlaySheetMode
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState
import `fun`.kirari.hanako.solve.application.SolveOperations
import `fun`.kirari.hanako.solve.application.SolveTaskHandle
import `fun`.kirari.hanako.solve.application.SolveTaskSnapshot
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.solve.model.WorkflowTaskStatus
import `fun`.kirari.hanako.core.model.loadHistoryBitmaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

internal class OverlayAnswerController(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<OverlayUiState>,
    private val solveOperations: SolveOperations,
    private val bubbleStateMachine: BubbleStateMachine
) {
    private val tag = "HanakoOverlayAnswer"
    private var activeObservation: Job? = null

    fun process(bitmap: Bitmap) {
        process(listOf(bitmap))
    }

    fun process(bitmaps: List<Bitmap>) {
        val firstBitmap = bitmaps.firstOrNull() ?: return
        val state = uiState.value
        AppDebugLogStore.i(tag, "process start route=${state.settings.processingRoute} bitmapCount=${bitmaps.size}")
        uiState.update {
            it.copy(
                selectedBitmap = firstBitmap,
                liveOcrText = "",
                liveAnswerText = "",
                result = null,
                error = null,
                working = true,
                sheetVisible = true,
                sheetMode = OverlaySheetMode.RESULT
            )
        }
        val handle = runCatching {
            solveOperations.startAnswer(state.settings, bitmaps)
        }.onFailure(::showStartFailure).getOrNull() ?: return
        observe(handle)
    }

    fun regenerateCurrentResult() {
        val existingResult = uiState.value.result ?: return
        if (existingResult.automationAction != null || uiState.value.working) return
        val bitmaps = existingResult.loadHistoryBitmaps()
        val firstBitmap = bitmaps.firstOrNull()
        if (firstBitmap == null) {
            uiState.update { it.copy(error = "找不到原始截图，无法重新生成") }
            return
        }
        uiState.update {
            it.copy(
                selectedBitmap = firstBitmap,
                liveOcrText = "",
                liveAnswerText = "",
                error = null,
                working = true,
                sheetVisible = true,
                sheetMode = OverlaySheetMode.RESULT,
                result = existingResult.copy(detail = "正在重新生成")
            )
        }
        val handle = runCatching {
            solveOperations.regenerate(uiState.value.settings, existingResult, bitmaps)
        }.onFailure(::showStartFailure).getOrNull() ?: return
        observe(handle)
    }

    private fun observe(handle: SolveTaskHandle) {
        activeObservation?.cancel()
        val job = scope.launch {
            handle.updates
                .onEach(::applySnapshot)
                .takeWhile { it.task.status == WorkflowTaskStatus.RUNNING }
                .collect {}
        }
        activeObservation = job
        job.invokeOnCompletion {
            if (activeObservation === job) activeObservation = null
        }
    }

    private fun applySnapshot(snapshot: SolveTaskSnapshot) {
        snapshot.result?.let(::showResultProgress)
        when (snapshot.task.status) {
            WorkflowTaskStatus.RUNNING -> Unit
            WorkflowTaskStatus.SUCCESS -> {
                val result = snapshot.result ?: return
                AppDebugLogStore.i(tag, "task success resultId=${result.id} answerLength=${result.answer.length}")
                uiState.update {
                    it.copy(
                        working = false,
                        result = result,
                        liveOcrText = result.extractedText,
                        liveAnswerText = result.answer,
                        autoRunState = AutoRunState.IDLE,
                        autoCopiedLabel = null,
                        pendingVibrationLetters = null
                    )
                }
                bubbleStateMachine.forceState(BubbleState.Idle)
            }
            WorkflowTaskStatus.ERROR -> {
                val message = snapshot.task.errorMessage ?: snapshot.result?.detail ?: "处理失败"
                AppDebugLogStore.e(tag, "task failed: $message")
                uiState.update {
                    it.copy(
                        working = false,
                        autoRunState = AutoRunState.IDLE,
                        pendingVibrationLetters = null,
                        error = message
                    )
                }
            }
            WorkflowTaskStatus.CANCELLED -> {
                uiState.update {
                    it.copy(
                        working = false,
                        autoRunState = AutoRunState.IDLE,
                        pendingVibrationLetters = null
                    )
                }
                bubbleStateMachine.forceState(BubbleState.Idle)
            }
        }
    }

    private fun showResultProgress(result: ProcessingResult) {
        uiState.update { current ->
            current.copy(
                result = result,
                liveOcrText = result.extractedText,
                liveAnswerText = result.answer
            )
        }
    }

    private fun showStartFailure(error: Throwable) {
        AppDebugLogStore.e(tag, "task start failed", error)
        uiState.update { it.copy(working = false, error = error.message ?: "处理失败") }
    }
}
