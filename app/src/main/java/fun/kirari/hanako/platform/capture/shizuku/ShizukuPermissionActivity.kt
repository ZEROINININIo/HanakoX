package `fun`.kirari.hanako.platform.capture.shizuku

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import `fun`.kirari.hanako.platform.capture.CaptureLaunchContract
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import rikka.shizuku.Shizuku

class ShizukuPermissionActivity : ComponentActivity() {
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != REQUEST_CODE) return@OnRequestPermissionResultListener
        val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
        ShizukuAuthorizationManager.setAuthorized(granted)
        if (granted) {
            startService(CaptureLaunchContract.overlayServiceIntent(this, launchMode))
            setResult(Activity.RESULT_OK)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    private lateinit var launchMode: CaptureLaunchMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchMode = intent.getStringExtra(CaptureLaunchContract.EXTRA_LAUNCH_MODE)
            ?.let { runCatching { CaptureLaunchMode.valueOf(it) }.getOrNull() }
            ?: CaptureLaunchMode.NORMAL

        if (!Shizuku.pingBinder()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        when {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                ShizukuAuthorizationManager.setAuthorized(true)
                startService(CaptureLaunchContract.overlayServiceIntent(this, launchMode))
                setResult(Activity.RESULT_OK)
                finish()
            }

            Shizuku.shouldShowRequestPermissionRationale() -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }

            else -> {
                Shizuku.addRequestPermissionResultListener(permissionListener)
                Shizuku.requestPermission(REQUEST_CODE)
            }
        }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE = 1001
    }
}
