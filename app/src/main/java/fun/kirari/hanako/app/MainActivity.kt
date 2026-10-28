package `fun`.kirari.hanako.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import `fun`.kirari.hanako.app.navigation.HanakoApp
import `fun`.kirari.hanako.app.navigation.AppViewModel
import `fun`.kirari.hanako.core.ui.theme.HanakoTheme
import `fun`.kirari.hanako.feature.settings.ui.settings.DisclaimerConfirmationGate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()

    private fun consumeKirariRedirect(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        viewModel.handleKirariRedirect(data)
        intent.setData(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("HanakoAI", "Uncaught exception in thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        consumeKirariRedirect(intent)
        setContent {
            HanakoTheme {
                val disclaimerPreferences = remember {
                    this@MainActivity.getSharedPreferences(
                        "hanako_x_disclaimer",
                        android.content.Context.MODE_PRIVATE
                    )
                }
                var disclaimerAccepted by remember {
                    mutableStateOf(disclaimerPreferences.getBoolean("accepted", false))
                }
                if (disclaimerAccepted) {
                    HanakoApp(viewModel)
                } else {
                    DisclaimerConfirmationGate {
                        disclaimerPreferences.edit().putBoolean("accepted", true).apply()
                        disclaimerAccepted = true
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeKirariRedirect(intent)
    }
}
