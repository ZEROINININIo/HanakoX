package `fun`.kirari.hanako.feature.settings.ui.update

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.network.update.AppUpdateInfo
import `fun`.kirari.hanako.core.ui.richtext.MarkdownLatexText

@Composable
internal fun AppUpdateDialog(
    update: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Upgrade, contentDescription = null)
        },
        title = {
            Text("发现新版本 ${update.version}")
        },
        text = {
            MarkdownLatexText(
                content = update.changelogMarkdown,
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            Toast.makeText(context, "无法打开 Release 页面", Toast.LENGTH_SHORT).show()
                        }
                }
            ) {
                Text("打开 Release 页面")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
