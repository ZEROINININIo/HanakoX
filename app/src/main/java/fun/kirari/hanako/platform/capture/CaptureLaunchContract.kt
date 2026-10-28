package `fun`.kirari.hanako.platform.capture

import android.content.Context
import android.content.Intent

internal enum class CaptureLaunchMode {
    NORMAL,
    AUTO,
    QUICK_SOLVE
}

internal object CaptureLaunchContract {
    const val EXTRA_LAUNCH_MODE = "extra_launch_mode"
    private const val OVERLAY_SERVICE_CLASS =
        "fun.kirari.hanako.feature.overlay.service.OverlayService"

    fun overlayServiceIntent(context: Context, launchMode: CaptureLaunchMode): Intent {
        return Intent()
            .setClassName(context.packageName, OVERLAY_SERVICE_CLASS)
            .putExtra(EXTRA_LAUNCH_MODE, launchMode.name)
    }
}
