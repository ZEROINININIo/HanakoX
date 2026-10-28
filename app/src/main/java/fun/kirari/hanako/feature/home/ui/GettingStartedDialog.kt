package `fun`.kirari.hanako.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class GuidePage(val title: String, val body: String)

private val guidePages = listOf(
    GuidePage("截图，交给 AI 解答", "启动悬浮球后，点击它并框选题目区域。Hanako X 会把截图交给你配置的 AI，并展示解题过程和可复制的答案。"),
    GuidePage("先配置一个模型", "打开「设置 → 模型提供方」添加 API 服务，再到「模型设置」选择文本、OCR 或多模态模型。DeepSeek API 的配置步骤也可以在提供方页面查看。"),
    GuidePage("按需授予权限", "悬浮窗权限用于显示悬浮球，截屏时 Android 会请求屏幕共享授权。截图和历史记录保存在应用本地；发送给模型的内容由所选服务提供方处理。")
)

@Composable
fun GettingStartedDialog(onDismiss: () -> Unit) {
    var page by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val current = guidePages[page]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("欢迎使用 Hanako X")
                Text(
                    "${page + 1} / ${guidePages.size}  ·  ${current.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = { Text(current.body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = { if (page < guidePages.lastIndex) page++ else onDismiss() }) {
                Text(if (page == guidePages.lastIndex) "开始使用" else "下一步")
            }
        },
        dismissButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDismiss) { Text("跳过") }
                if (page > 0) TextButton(onClick = { page-- }) { Text("上一步") }
            }
        }
    )
}
