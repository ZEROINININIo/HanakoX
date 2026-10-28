package `fun`.kirari.hanako.feature.overlay.presentation

import `fun`.kirari.hanako.feature.overlay.state.AutoRunState
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import `fun`.kirari.hanako.feature.overlay.state.OverlaySheetMode
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState

import `fun`.kirari.hanako.feature.overlay.state.BubbleEvent
import `fun`.kirari.hanako.feature.overlay.state.BubbleMenuItem
import `fun`.kirari.hanako.feature.overlay.state.BubbleState
import `fun`.kirari.hanako.feature.overlay.state.BubbleStateMachine
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class OverlayInteractionController(
    private val uiState: MutableStateFlow<OverlayUiState>,
    private val bubbleStateMachine: BubbleStateMachine,
    private val openCropSheet: () -> Unit,
    private val capturePage: () -> Unit,
    private val sendCaptures: () -> Unit,
    private val exitMultiPageCaptureMode: () -> Unit,
    private val cancelActiveProcessing: () -> Unit,
    private val enterMultiPageCaptureMode: () -> Unit,
    private val isBubbleMenuEnabled: () -> Boolean,
    private val toggleProcessingRoute: () -> Unit,
    private val toggleWebSearch: () -> Unit,
    private val openSettings: () -> Unit
) {
    private val tag = "HanakoOverlayInteraction"

    fun consumeAutoCompletedState() {
        val currentState = bubbleStateMachine.currentState
        AppDebugLogStore.d(tag, "consumeAutoCompletedState state=${uiState.value.autoRunState} bubble=${currentState::class.simpleName}")

        if (uiState.value.launchMode != CaptureLaunchMode.NORMAL && uiState.value.autoRunState == AutoRunState.COMPLETED) {
            if (currentState is BubbleState.Copied ||
                (uiState.value.settings.automation.staticModeEnabled && currentState is BubbleState.ShowingLetters)
            ) {
                uiState.update { it.copy(autoRunState = AutoRunState.IDLE, pendingVibrationLetters = null) }
                bubbleStateMachine.forceState(BubbleState.Idle)
            } else {
                uiState.update { it.copy(autoRunState = AutoRunState.IDLE, pendingVibrationLetters = null) }
            }
        }
    }

    fun consumePendingVibrationLetters() {
        uiState.update { it.copy(pendingVibrationLetters = null) }
    }

    fun onBubbleTappedAfterLettersShown() {
        val currentState = bubbleStateMachine.currentState
        AppDebugLogStore.i(tag, "onBubbleTappedAfterLettersShown launchMode=${uiState.value.launchMode} bubble=${currentState::class.simpleName}")

        if (uiState.value.launchMode == CaptureLaunchMode.AUTO && currentState is BubbleState.ShowingLetters) {
            AppDebugLogStore.i(tag, "onBubbleTappedAfterLettersShown clearing letters and entering pending reset")
            bubbleStateMachine.dispatch(BubbleEvent.SingleTap)
        }
    }

    fun handleSingleTap() {
        val currentState = bubbleStateMachine.currentState
        AppDebugLogStore.i(tag, "handleSingleTap state=${currentState::class.simpleName}")

        when (currentState) {
            is BubbleState.MenuExpanded -> {
                bubbleStateMachine.dispatch(BubbleEvent.CloseMenu)
            }
            is BubbleState.MultiPageCapture -> {
                capturePage()
            }
            is BubbleState.MultiPageCapturing -> {
                AppDebugLogStore.i(tag, "handleSingleTap ignored, capturing in progress")
            }
            is BubbleState.MultiPageCaptureSuccess -> {
                AppDebugLogStore.i(tag, "handleSingleTap ignored, showing capture success")
            }
            is BubbleState.Copied -> {
                bubbleStateMachine.dispatch(BubbleEvent.SingleTap)
            }
            is BubbleState.Error -> {
                bubbleStateMachine.dispatch(BubbleEvent.SingleTap)
            }
            else -> {
                openCropSheet()
            }
        }
    }

    fun handleLongPress(anchorX: Int = 0, anchorY: Int = 0) {
        val currentState = bubbleStateMachine.currentState
        AppDebugLogStore.i(tag, "handleLongPress state=${currentState::class.simpleName} anchor=($anchorX,$anchorY)")

        when (currentState) {
            is BubbleState.MultiPageCapture -> {
                if (currentState.capturedBitmaps.isNotEmpty()) {
                    sendCaptures()
                }
            }
            is BubbleState.MultiPageCapturing -> {
                AppDebugLogStore.i(tag, "handleLongPress ignored, capturing in progress")
            }
            is BubbleState.MultiPageCaptureSuccess -> {
                if (currentState.capturedBitmaps.isNotEmpty()) {
                    sendCaptures()
                }
            }
            is BubbleState.MenuExpanded -> {
                bubbleStateMachine.dispatch(BubbleEvent.CloseMenu)
            }
            is BubbleState.Idle, is BubbleState.ShowingLetters,
            is BubbleState.Copied, is BubbleState.Error -> {
                enterMultiPageCaptureMode()
            }
            else -> {
                if (isBubbleMenuEnabled()) {
                    bubbleStateMachine.dispatch(BubbleEvent.LongPress(anchorX, anchorY))
                }
            }
        }
    }

    fun handleDoubleTap(anchorX: Int = 0, anchorY: Int = 0) {
        val currentState = bubbleStateMachine.currentState
        AppDebugLogStore.i(tag, "handleDoubleTap state=${currentState::class.simpleName}")

        when (currentState) {
            is BubbleState.MultiPageCapture,
            is BubbleState.MultiPageCapturing,
            is BubbleState.MultiPageCaptureSuccess -> {
                exitMultiPageCaptureMode()
            }
            is BubbleState.Processing -> {
                cancelActiveProcessing()
            }
            is BubbleState.Idle,
            is BubbleState.ShowingLetters,
            is BubbleState.Copied,
            is BubbleState.Error -> {
                if (isBubbleMenuEnabled()) {
                    bubbleStateMachine.dispatch(BubbleEvent.LongPress(anchorX, anchorY))
                }
            }
            is BubbleState.MenuExpanded -> {
                bubbleStateMachine.dispatch(BubbleEvent.CloseMenu)
            }
            else -> {}
        }
    }

    fun onMenuDismissed() {
        val currentState = bubbleStateMachine.currentState
        if (currentState is BubbleState.MenuExpanded) {
            bubbleStateMachine.dispatch(BubbleEvent.CloseMenu)
        }
    }

    fun handleMenuSelect(item: BubbleMenuItem) {
        AppDebugLogStore.i(tag, "handleMenuSelect item=$item")
        when (item) {
            BubbleMenuItem.ToggleRoute -> toggleProcessingRoute()
            BubbleMenuItem.ToggleSearch -> toggleWebSearch()
            BubbleMenuItem.Settings -> openSettings()
            BubbleMenuItem.VoiceRecognition -> {
                // 语音功能暂未实现
            }
        }
        // 不在此处 dispatch CloseMenu。
        // 状态恢复统一由退场动画结束后的 onMenuDismissed() 处理。
    }
}
