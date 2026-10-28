package `fun`.kirari.hanako.feature.overlay.window

import android.os.Build
import android.util.Log
import android.view.SurfaceControl
import android.view.View
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.ref.WeakReference

internal object AntiScreenshotHelper {
    private const val TAG = "AntiScreenshot"
    private var initialized = false

    @Volatile
    var enabled: Boolean = false

    private val trackedViews = mutableListOf<WeakReference<View>>()

    fun initialize() {
        if (initialized) return
        initialized = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }.onFailure {
            Log.w(TAG, "HiddenApiBypass initialization failed", it)
        }
    }

    /**
     * 对通过 [WindowManager.addView] 添加的悬浮窗 View 应用防截屏保护。
     *
     * 必须在 [WindowManager.addView] 之后调用。
     * 内部通过 [View.post] 延迟到 View 附着窗口后执行，
     * 通过反射获取 [ViewRootImpl.getSurfaceControl] 并调用
     * [SurfaceControl.Transaction.setSkipScreenshot]。
     *
     * 仅在 Android 12 (API 31) 及以上版本生效，且 [enabled] 为 true 时执行。
     */
    fun applyTo(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        trackedViews.add(WeakReference(view))
        view.post { attemptApply(view) }
    }

    /**
     * 当 [enabled] 从 false 变为 true 时调用，
     * 对已注册的所有视图重新尝试应用防截屏保护。
     */
    fun refreshAll() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        trackedViews.removeAll { it.get() == null }
        trackedViews.forEach { ref ->
            val view = ref.get() ?: return@forEach
            view.post { attemptApply(view) }
        }
    }

    private fun attemptApply(view: View) {
        if (!enabled) return
        runCatching {
            val getVri = View::class.java.getDeclaredMethod("getViewRootImpl")
            getVri.isAccessible = true
            val vri = getVri.invoke(view) ?: return

            val getSc = vri.javaClass.getMethod("getSurfaceControl")
            val sc = getSc.invoke(vri) as? SurfaceControl ?: return

            val txClass = Class.forName("android.view.SurfaceControl\$Transaction")
            val tx = txClass.getDeclaredConstructor().newInstance()
            val setSkipMethod = txClass.getMethod(
                "setSkipScreenshot",
                SurfaceControl::class.java,
                java.lang.Boolean.TYPE
            )
            setSkipMethod.invoke(tx, sc, true)
            val applyMethod = txClass.getMethod("apply")
            applyMethod.invoke(tx)
        }.onFailure {
            Log.w(TAG, "Failed to apply anti-screenshot", it)
        }
    }
}
