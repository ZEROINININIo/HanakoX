package `fun`.kirari.hanako.feature.home.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.platform.capture.ScreenCaptureManager
import `fun`.kirari.hanako.platform.capture.ScreenCaptureStartResult
import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.AnswerStyle
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import `fun`.kirari.hanako.feature.home.ui.components.HeroSection

@Composable
internal fun HanakoHomeScreen(
    settings: AppSettings,
    overlayEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onOpenOverlayPermission: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onSelectRoute: (ProcessingRoute) -> Unit,
    onToggleAnswerStyle: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroSection(
                overlayEnabled = overlayEnabled,
                hasOverlayPermission = hasOverlayPermission,
                captureMethod = settings.screenCaptureMethod,
                staticModeEnabled = settings.automation.staticModeEnabled,
                route = settings.processingRoute,
                answerStyle = settings.answerStyle,
                onSelectRoute = onSelectRoute,
                onToggleAnswerStyle = onToggleAnswerStyle,
                onOpenOverlayPermission = onOpenOverlayPermission,
                onToggleOverlay = onToggleOverlay,
                onStartAutoMode = {
                    if (!hasOverlayPermission) return@HeroSection
                    when (
                        val result = ScreenCaptureManager.requestStart(
                            context = context,
                            method = settings.screenCaptureMethod,
                            launchMode = CaptureLaunchMode.AUTO
                        )
                    ) {
                        ScreenCaptureStartResult.Started -> Unit
                        is ScreenCaptureStartResult.UserActionRequired -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }
                        is ScreenCaptureStartResult.Failed -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onStartQuickSolve = {
                    if (!hasOverlayPermission) return@HeroSection
                    when (
                        val result = ScreenCaptureManager.requestStart(
                            context = context,
                            method = settings.screenCaptureMethod,
                            launchMode = CaptureLaunchMode.QUICK_SOLVE
                        )
                    ) {
                        ScreenCaptureStartResult.Started -> Unit
                        is ScreenCaptureStartResult.UserActionRequired -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }
                        is ScreenCaptureStartResult.Failed -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
        item {
            HistoryEntryCard(onOpenHistory = onOpenHistory)
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun HistoryEntryCard(onOpenHistory: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenHistory),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "历史记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "查看悬浮窗处理过的历史记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
