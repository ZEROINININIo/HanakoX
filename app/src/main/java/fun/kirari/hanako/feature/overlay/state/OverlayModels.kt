package `fun`.kirari.hanako.feature.overlay.state

import android.graphics.Bitmap
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.feature.overlay.state.BubbleState
import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode

internal val SheetDockOffset = 88.dp
internal val PanelHandleYOffset = 38.dp
internal const val SheetAnimationDurationMs = 260

internal enum class OverlaySheetMode {
    CROP,
    RESULT
}

internal enum class AutoRunState {
    IDLE,
    RUNNING,
    COMPLETED
}

internal data class OverlayUiState(
    val settings: AppSettings = AppSettings(),
    val screenshot: Bitmap? = null,
    val selectedBitmap: Bitmap? = null,
    val liveOcrText: String = "",
    val liveAnswerText: String = "",
    val result: ProcessingResult? = null,
    val error: String? = null,
    val working: Boolean = false,
    val sheetVisible: Boolean = false,
    val sheetMode: OverlaySheetMode = OverlaySheetMode.CROP,
    val launchMode: CaptureLaunchMode = CaptureLaunchMode.NORMAL,
    val autoRunState: AutoRunState = AutoRunState.IDLE,
    val autoCopiedLabel: String? = null,
    val pendingVibrationLetters: String? = null,
    val bubbleState: BubbleState = BubbleState.Idle
)
