package `fun`.kirari.hanako.feature.overlay.window

import `fun`.kirari.hanako.feature.overlay.state.PanelHandleYOffset
import `fun`.kirari.hanako.feature.overlay.state.SheetAnimationDurationMs
import `fun`.kirari.hanako.feature.overlay.state.SheetDockOffset

import `fun`.kirari.hanako.feature.overlay.presentation.OverlayViewModel
import `fun`.kirari.hanako.feature.overlay.ui.OverlayPanel

import `fun`.kirari.hanako.feature.overlay.state.AutoRunState
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import `fun`.kirari.hanako.feature.overlay.state.OverlaySheetMode
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import `fun`.kirari.hanako.core.common.easeOutCubic
import `fun`.kirari.hanako.feature.overlay.window.AntiScreenshotHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal class PanelWindowController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val scope: CoroutineScope,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val viewModel: OverlayViewModel
) {
    private var panelView: FrameLayout? = null
    private var panelContentView: ComposeView? = null
    private var panelHandleView: FrameLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelHandleParams: WindowManager.LayoutParams? = null
    private var panelScreenHeightPx: Int = 0
    private var panelHeightPx: Int = 0
    private var panelDockHeightPx: Int = 0
    private var panelCurrentHeightPx: Int = 0
    private var panelHandleHeightPx: Int = 0
    private var panelHandleWidthPx: Int = 0
    private var panelAnimationJob: Job? = null
    private var panelClosing = false

    fun showOrUpdate(mode: OverlaySheetMode) {
        panelClosing = false
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val screenHeightPx = metrics.heightPixels
        val density = context.resources.displayMetrics.density
        val dockHeightPx = density.times(SheetDockOffset.value).roundToInt()
        val targetHeightPx = (screenHeightPx * if (mode == OverlaySheetMode.CROP) 0.88f else 0.92f)
            .roundToInt()
            .coerceAtLeast(dockHeightPx)

        panelScreenHeightPx = screenHeightPx
        panelHeightPx = targetHeightPx
        panelDockHeightPx = dockHeightPx
        panelHandleHeightPx = (28f * density).roundToInt()
        panelHandleWidthPx = (88f * density).roundToInt()
        panelCurrentHeightPx = if (panelView == null) {
            dockHeightPx
        } else {
            panelCurrentHeightPx.coerceIn(dockHeightPx, targetHeightPx)
        }

        val params = buildPanelParams(screenHeightPx)
        val handleParams = buildHandleParams(screenHeightPx)

        if (panelView == null) {
            val composeView = createComposeView(targetHeightPx)
            val panelRoot = createPanelRoot(composeView, targetHeightPx)
            val handleView = createPanelHandleView()
            panelView = panelRoot
            panelContentView = composeView
            panelHandleView = handleView
            windowManager.addView(panelRoot, params)
            windowManager.addView(handleView, handleParams)
            AntiScreenshotHelper.applyTo(panelRoot)
            AntiScreenshotHelper.applyTo(handleView)
            applyPanelHeight(panelCurrentHeightPx)
            animatePanelHeight(
                fromHeightPx = panelCurrentHeightPx,
                toHeightPx = targetHeightPx
            )
        } else {
            runCatching { windowManager.updateViewLayout(panelView, params) }
            runCatching { windowManager.updateViewLayout(panelHandleView, handleParams) }
            applyPanelHeight(panelCurrentHeightPx)
        }
    }

    fun hideWithAnimation() {
        if (panelClosing) return
        val view = panelView
        if (view == null) {
            removePanelNow()
            return
        }
        panelClosing = true
        animatePanelHeight(
            fromHeightPx = panelCurrentHeightPx,
            toHeightPx = 0
        ) {
            if (panelView === view) {
                removePanelNow()
            }
        }
    }

    fun dismiss() {
        panelAnimationJob?.cancel()
        panelAnimationJob = null
        panelClosing = false
        removePanelNow()
    }

    private fun buildPanelParams(screenHeightPx: Int): WindowManager.LayoutParams {
        val params = panelParams ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            panelCurrentHeightPx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = screenHeightPx - panelCurrentHeightPx
        }
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = panelCurrentHeightPx
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = (screenHeightPx - panelCurrentHeightPx).coerceAtLeast(0)
        panelParams = params
        return params
    }

    private fun buildHandleParams(screenHeightPx: Int): WindowManager.LayoutParams {
        val handleParams = panelHandleParams ?: WindowManager.LayoutParams(
            panelHandleWidthPx,
            panelHandleHeightPx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = screenHeightPx - panelCurrentHeightPx
        }
        handleParams.width = panelHandleWidthPx
        handleParams.height = panelHandleHeightPx
        handleParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        handleParams.x = 0
        handleParams.y = panelHandleY(screenHeightPx, panelCurrentHeightPx)
        panelHandleParams = handleParams
        return handleParams
    }

    private fun createComposeView(targetHeightPx: Int): ComposeView {
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            setContent {
                `fun`.kirari.hanako.core.ui.theme.HanakoTheme {
                    OverlayPanel(
                        viewModel = viewModel,
                        onDismiss = { viewModel.closeSheet() },
                        panelHeightPx = targetHeightPx
                    )
                }
            }
        }
    }

    private fun createPanelRoot(composeView: ComposeView, targetHeightPx: Int): FrameLayout {
        return FrameLayout(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            clipChildren = true
            clipToPadding = true
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    targetHeightPx
                )
            )
        }
    }

    private fun createPanelHandleView(): FrameLayout {
        var dragStartRawY = 0f
        var dragStartHeightPx = 0
        return FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                FrameLayout(context).apply {
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(86, 86, 86))
                        cornerRadius = 999f * context.resources.displayMetrics.density
                    }
                },
                FrameLayout.LayoutParams(
                    (68f * context.resources.displayMetrics.density).roundToInt(),
                    (8f * context.resources.displayMetrics.density).roundToInt()
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = (10f * context.resources.displayMetrics.density).roundToInt()
                }
            )
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        panelAnimationJob?.cancel()
                        dragStartRawY = event.rawY
                        dragStartHeightPx = panelCurrentHeightPx
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val nextHeight = (dragStartHeightPx - (event.rawY - dragStartRawY)).roundToInt()
                        updatePanelHeight(nextHeight)
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> true
                    else -> false
                }
            }
        }
    }

    private fun updatePanelHeight(heightPx: Int) {
        if (panelDockHeightPx <= 0 || panelHeightPx <= 0) return
        applyPanelHeight(heightPx.coerceIn(panelDockHeightPx, panelHeightPx))
    }

    private fun applyPanelHeight(heightPx: Int) {
        val view = panelView ?: return
        val params = panelParams
        val handleParams = panelHandleParams
        if (panelHeightPx <= 0 || panelScreenHeightPx <= 0) return
        panelCurrentHeightPx = heightPx.coerceIn(0, panelHeightPx)
        if (params != null) {
            params.y = (panelScreenHeightPx - panelCurrentHeightPx).coerceAtLeast(0)
            params.height = panelCurrentHeightPx.coerceAtLeast(1)
            runCatching { windowManager.updateViewLayout(view, params) }
        }
        if (handleParams != null) {
            handleParams.y = panelHandleY(panelScreenHeightPx, panelCurrentHeightPx)
            runCatching { windowManager.updateViewLayout(panelHandleView, handleParams) }
        }
    }

    private fun panelHandleY(screenHeightPx: Int, currentHeightPx: Int): Int {
        val offsetPx = (PanelHandleYOffset.value * context.resources.displayMetrics.density).roundToInt()
        return (screenHeightPx - currentHeightPx - offsetPx).coerceAtLeast(0)
    }

    private fun animatePanelHeight(
        fromHeightPx: Int,
        toHeightPx: Int,
        onEnd: (() -> Unit)? = null
    ) {
        panelAnimationJob?.cancel()
        panelAnimationJob = scope.launch {
            val start = fromHeightPx.coerceIn(0, panelHeightPx.coerceAtLeast(fromHeightPx))
            val end = toHeightPx.coerceIn(0, panelHeightPx.coerceAtLeast(toHeightPx))
            animatePanelHeightSegment(
                startHeightPx = start,
                endHeightPx = end,
                durationMs = SheetAnimationDurationMs,
                easing = ::easeOutCubic
            )
            applyPanelHeight(end)
            onEnd?.invoke()
        }
    }

    private suspend fun animatePanelHeightSegment(
        startHeightPx: Int,
        endHeightPx: Int,
        durationMs: Int,
        easing: (Float) -> Float
    ) {
        val startTimeMs = android.os.SystemClock.uptimeMillis()
        while (currentCoroutineContext().isActive) {
            val elapsed = android.os.SystemClock.uptimeMillis() - startTimeMs
            val fraction = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            val eased = easing(fraction)
            val height = (startHeightPx + (endHeightPx - startHeightPx) * eased).roundToInt()
            applyPanelHeight(height)
            if (fraction >= 1f) break
            kotlinx.coroutines.delay(16L)
        }
        applyPanelHeight(endHeightPx)
    }

    private fun removePanelNow() {
        removeWindowImmediately("handle", panelHandleView)
        removeWindowImmediately("panel", panelView)
        panelHandleView = null
        panelContentView = null
        panelView = null
        panelHandleParams = null
        panelParams = null
        panelCurrentHeightPx = 0
        panelClosing = false
    }

    private fun removeWindowImmediately(name: String, view: android.view.View?) {
        if (view == null || !view.isAttachedToWindow) return
        runCatching { windowManager.removeViewImmediate(view) }
            .onFailure { error -> Log.e(LOG_TAG, "Failed to remove $name window", error) }
    }

    private companion object {
        const val LOG_TAG = "HanakoPanelWindow"
    }
}
