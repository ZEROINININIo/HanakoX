package `fun`.kirari.hanako.platform.capture.projection

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import `fun`.kirari.hanako.platform.capture.CaptureLaunchContract
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode

class ProjectionPermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val launchMode = remember {
                intent.getStringExtra(CaptureLaunchContract.EXTRA_LAUNCH_MODE)
                    ?.let { runCatching { CaptureLaunchMode.valueOf(it) }.getOrNull() }
                    ?: CaptureLaunchMode.NORMAL
            }
            val projectionManager = remember {
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            }
            var launched by remember { mutableStateOf(false) }
            val launcher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    ContextCompat.startForegroundService(
                        this,
                        Intent(this, MediaProjectionForegroundService::class.java).apply {
                            action = MediaProjectionForegroundService.ACTION_START_SESSION
                            putExtra(MediaProjectionForegroundService.EXTRA_RESULT_CODE, result.resultCode)
                            putExtra(MediaProjectionForegroundService.EXTRA_RESULT_DATA, result.data)
                        }
                    )
                    startService(CaptureLaunchContract.overlayServiceIntent(this, launchMode))
                    setResult(Activity.RESULT_OK)
                } else {
                    stopService(Intent(this, MediaProjectionForegroundService::class.java))
                    setResult(Activity.RESULT_CANCELED)
                }
                finish()
            }

            LaunchedEffect(Unit) {
                if (!launched) {
                    launched = true
                    ContextCompat.startForegroundService(
                        this@ProjectionPermissionActivity,
                        Intent(this@ProjectionPermissionActivity, MediaProjectionForegroundService::class.java)
                    )
                    launcher.launch(projectionManager.createScreenCaptureIntent())
                }
            }
        }
    }

}
