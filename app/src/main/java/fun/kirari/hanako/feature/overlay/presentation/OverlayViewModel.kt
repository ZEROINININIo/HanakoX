package `fun`.kirari.hanako.feature.overlay.presentation

import `fun`.kirari.hanako.feature.overlay.state.AutoRunState
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import `fun`.kirari.hanako.feature.overlay.state.OverlaySheetMode
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `fun`.kirari.hanako.feature.overlay.state.BubbleMenuItem
import `fun`.kirari.hanako.feature.overlay.state.BubbleState
import `fun`.kirari.hanako.feature.overlay.state.BubbleStateMachine
import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.core.data.ModelSelection
import `fun`.kirari.hanako.core.data.SettingsRepository
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.core.network.ProviderModelsApi
import `fun`.kirari.hanako.solve.application.SolveOperations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OverlayViewModel(
    private val appContext: Context,
    private val repository: SettingsRepository,
    private val solveOperations: SolveOperations,
    val providerModelsApi: ProviderModelsApi,
    private val mainActivityIntent: (Context) -> Intent
) : ViewModel() {
    private val tag = "HanakoOverlayVM"
    
    // 新的状态机
    val bubbleStateMachine = BubbleStateMachine()
    
    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()
    private val multiPageCaptureController = MultiPageCaptureController(
        appContext = appContext,
        scope = viewModelScope,
        uiState = _uiState,
        bubbleStateMachine = bubbleStateMachine
    )
    private val autoProcessingController = AutoProcessingController(
        appContext = appContext,
        scope = viewModelScope,
        uiState = _uiState,
        solveOperations = solveOperations,
        bubbleStateMachine = bubbleStateMachine
    )
    private val answerController = OverlayAnswerController(
        scope = viewModelScope,
        uiState = _uiState,
        solveOperations = solveOperations,
        bubbleStateMachine = bubbleStateMachine
    )
    private val settingsController = OverlaySettingsController(
        scope = viewModelScope,
        repository = repository,
        uiState = _uiState
    )
    private val interactionController = OverlayInteractionController(
        uiState = _uiState,
        bubbleStateMachine = bubbleStateMachine,
        openCropSheet = ::openCropSheet,
        capturePage = ::capturePage,
        sendCaptures = ::sendCaptures,
        exitMultiPageCaptureMode = ::exitMultiPageCaptureMode,
        cancelActiveProcessing = autoProcessingController::cancelActiveProcessing,
        enterMultiPageCaptureMode = ::enterMultiPageCaptureMode,
        isBubbleMenuEnabled = { _uiState.value.settings.automation.bubbleMenuEnabled },
        toggleProcessingRoute = ::toggleProcessingRoute,
        toggleWebSearch = ::toggleWebSearch,
        openSettings = {
            appContext.startActivity(
                mainActivityIntent(appContext).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
        }
    )

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        
        // 监听状态机变化，同步到 UI 状态
        viewModelScope.launch {
            bubbleStateMachine.state.collect { bubbleState ->
                _uiState.update { it.copy(bubbleState = bubbleState) }
            }
        }
    }

    fun setLaunchMode(mode: CaptureLaunchMode) {
        AppDebugLogStore.i(tag, "setLaunchMode mode=$mode")
        _uiState.update { state ->
            state.copy(
                launchMode = mode,
                autoRunState = if (mode == CaptureLaunchMode.NORMAL) AutoRunState.IDLE else state.autoRunState,
                autoCopiedLabel = if (mode == CaptureLaunchMode.NORMAL) null else state.autoCopiedLabel,
                pendingVibrationLetters = if (mode == CaptureLaunchMode.NORMAL) null else state.pendingVibrationLetters,
                error = null
            )
        }
        // 普通模式重置状态机
        if (mode == CaptureLaunchMode.NORMAL) {
            bubbleStateMachine.forceState(BubbleState.Idle)
        }
    }

    fun openCropSheet() {
        AppDebugLogStore.i(tag, "openCropSheet launchMode=${_uiState.value.launchMode}")
        if (_uiState.value.launchMode != CaptureLaunchMode.NORMAL) {
            AppDebugLogStore.i(tag, "openCropSheet delegated to processFullScreen for ${_uiState.value.launchMode}")
            processFullScreen()
            return
        }
        viewModelScope.launch {
            runCatching {
                `fun`.kirari.hanako.platform.capture.ScreenCaptureManager.captureLatestBitmap(appContext, _uiState.value.settings.screenCaptureMethod)
            }.onSuccess { bitmap ->
                AppDebugLogStore.i(tag, "openCropSheet capture success width=${bitmap.width} height=${bitmap.height}")
                _uiState.update {
                    it.copy(
                        screenshot = bitmap,
                        selectedBitmap = null,
                        liveOcrText = "",
                        liveAnswerText = "",
                        result = null,
                        error = null,
                        working = false,
                        sheetVisible = true,
                        sheetMode = OverlaySheetMode.CROP,
                        autoRunState = AutoRunState.IDLE,
                        autoCopiedLabel = null,
                        pendingVibrationLetters = null
                    )
                }
                bubbleStateMachine.forceState(BubbleState.Idle)
            }.onFailure { error ->
                AppDebugLogStore.e(tag, "openCropSheet failed", error)
                _uiState.update {
                    it.copy(
                        error = error.message ?: "截屏失败",
                        sheetVisible = true,
                        sheetMode = OverlaySheetMode.CROP
                    )
                }
            }
        }
    }

    fun processFullScreen() {
        autoProcessingController.processFullScreen()
    }

    fun process(bitmap: Bitmap) {
        answerController.process(bitmap)
    }

    fun process(bitmaps: List<Bitmap>) {
        answerController.process(bitmaps)
    }

    fun regenerateCurrentResult() {
        answerController.regenerateCurrentResult()
    }

    fun closeSheet() {
        _uiState.update { it.copy(sheetVisible = false, error = null) }
    }

    fun consumeAutoCompletedState() {
        interactionController.consumeAutoCompletedState()
    }

    fun consumePendingVibrationLetters() {
        interactionController.consumePendingVibrationLetters()
    }

    fun presentAutomationEffect(result: `fun`.kirari.hanako.core.model.ProcessingResult) {
        autoProcessingController.presentAutomationEffect(result)
    }

    fun onBubbleTappedAfterLettersShown() {
        interactionController.onBubbleTappedAfterLettersShown()
    }

    // 多页截图相关方法
    
    /**
     * 进入多页截图模式
     */
    fun enterMultiPageCaptureMode() {
        multiPageCaptureController.enter()
    }

    /**
     * 截图一次
     */
    fun capturePage() {
        multiPageCaptureController.capturePage()
    }

    /**
     * 发送截图给 AI
     */
    fun sendCaptures() {
        AppDebugLogStore.i(tag, "sendCaptures called state=${bubbleStateMachine.currentState::class.simpleName}")
        if (!multiPageCaptureController.canSendCaptures()) {
            AppDebugLogStore.i(tag, "sendCaptures called but cannot send")
            return
        }
        
        val bitmaps = multiPageCaptureController.capturedBitmaps()
        AppDebugLogStore.i(tag, "sendCaptures count=${bitmaps.size}")
        
        if (bitmaps.isEmpty()) {
            AppDebugLogStore.i(tag, "sendCaptures no bitmaps available")
            return
        }
        
        multiPageCaptureController.markSendCaptures()
        AppDebugLogStore.i(tag, "sendCaptures dispatched SendCaptures, new state=${bubbleStateMachine.currentState::class.simpleName}")
        autoProcessingController.processBitmaps(bitmaps)
    }

    /**
     * 退出多页截图模式
     */
    fun exitMultiPageCaptureMode() {
        multiPageCaptureController.exit()
    }

    /**
     * 处理单击事件（根据当前状态决定行为）
     */
    fun handleSingleTap() {
        interactionController.handleSingleTap()
    }

    /**
     * 处理长按事件
     * - 多图模式下：发送已截图片
     * - Idle/ShowingLetters/Copied/Error：进入多图截图模式
     * - Processing 等：展开扇形菜单
     */
    fun handleLongPress(anchorX: Int = 0, anchorY: Int = 0) {
        interactionController.handleLongPress(anchorX, anchorY)
    }

    /**
     * 处理双击事件
     * - 多图模式：退出多图
     * - Processing：取消处理
     * - Idle/ShowingLetters/Copied/Error：展开扇形菜单
     */
    fun handleDoubleTap(anchorX: Int = 0, anchorY: Int = 0) {
        interactionController.handleDoubleTap(anchorX, anchorY)
    }

    /**
     * 菜单关闭后的回调
     */
    fun onMenuDismissed() {
        interactionController.onMenuDismissed()
    }

    /**
     * 处理菜单项点击
     */
    fun handleMenuSelect(item: BubbleMenuItem) {
        interactionController.handleMenuSelect(item)
    }

    fun toggleWebSearch() {
        settingsController.toggleWebSearch()
    }

    fun selectAssistant(assistantId: String) = settingsController.selectAssistant(assistantId)

    fun selectPreviousAssistant() {
        settingsController.selectPreviousAssistant()
    }

    fun selectNextAssistant() {
        settingsController.selectNextAssistant()
    }

    fun updateModelSelection(purpose: ModelPurpose, selection: ModelSelection) =
        settingsController.updateModelSelection(purpose, selection)

    fun updateModelSelectionWithFavorite(purpose: ModelPurpose, selection: ModelSelection, favoriteModel: Boolean = false) =
        settingsController.updateModelSelectionWithFavorite(purpose, selection, favoriteModel)

    fun toggleFavoriteModel(providerId: String, modelId: String) =
        settingsController.toggleFavoriteModel(providerId, modelId)

    fun toggleProcessingRoute() {
        settingsController.toggleProcessingRoute()
    }

    fun toggleAnswerStyle() {
        settingsController.toggleAnswerStyle()
    }

    companion object {
        fun factory(
            appContext: Context,
            dependencies: `fun`.kirari.hanako.feature.overlay.OverlayDependencies
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OverlayViewModel(
                        appContext = appContext,
                        repository = dependencies.settingsRepository,
                        solveOperations = dependencies.solveOperations,
                        providerModelsApi = dependencies.providerModelsApi,
                        mainActivityIntent = dependencies.mainActivityIntent
                    ) as T
                }
            }
        }
    }
}
