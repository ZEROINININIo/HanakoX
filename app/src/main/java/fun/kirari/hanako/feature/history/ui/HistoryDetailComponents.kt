package `fun`.kirari.hanako.feature.history.ui

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.model.ProcessingEvent
import `fun`.kirari.hanako.core.model.ContentAnchor
import `fun`.kirari.hanako.core.model.QuotedFragment
import `fun`.kirari.hanako.core.ui.richtext.MarkdownLatexText
import kotlin.math.roundToInt

@Composable
internal fun MissingHistoryDetail() {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("未找到该历史记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun HistoryScreenshots(
    screenshots: List<Bitmap>,
    onSingleImagePositioned: (Rect) -> Unit,
    onPreviewImage: (Int) -> Unit
) {
    if (screenshots.size == 1) {
        Image(
            bitmap = screenshots[0].asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInWindow()
                    val size = coordinates.size
                    onSingleImagePositioned(
                        Rect(
                            position.x.roundToInt(),
                            position.y.roundToInt(),
                            (position.x + size.width).roundToInt(),
                            (position.y + size.height).roundToInt()
                        )
                    )
                }
                .clickable { onPreviewImage(0) },
            contentScale = ContentScale.FillWidth
        )
    } else {
        Column {
            Text(
                text = "截图（${screenshots.size} 张）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                itemsIndexed(screenshots) { index, bitmap ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .width(280.dp)
                            .heightIn(min = 200.dp, max = 400.dp)
                            .clickable { onPreviewImage(index) }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "截图 ${index + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryMarkdownOrEmpty(
    content: String,
    historyId: String = "",
    messageId: String = "",
    answerVersionId: String? = null,
    sourceRevision: Long = 0L,
    highlightedBlockId: String? = null,
    underlinedBlockIds: Set<String> = emptySet(),
    onBlockFocused: ((HistoryRenderedBlock) -> Unit)? = null,
    onBlockPositioned: ((HistoryRenderedBlock) -> Unit)? = null
) {
    if (content.isNotBlank()) {
        if (historyId.isNotBlank() && messageId.isNotBlank() && onBlockFocused != null && onBlockPositioned != null) {
            HistoryInteractiveMarkdown(
                content = content,
                historyId = historyId,
                messageId = messageId,
                answerVersionId = answerVersionId,
                sourceRevision = sourceRevision,
                highlightedBlockId = highlightedBlockId,
                underlinedBlockIds = underlinedBlockIds,
                onBlockFocused = onBlockFocused,
                onBlockPositioned = onBlockPositioned,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            MarkdownLatexText(content = content, modifier = Modifier.fillMaxWidth())
        }
    } else {
        Text("暂无内容")
    }
}

internal fun searchStatusText(events: List<ProcessingEvent>): String? {
    val event = events.lastOrNull { it.title == "正在联网搜索" || it.title == "联网搜索完成" } ?: return null
    val keyword = Regex("关键词：([^，]+)").find(event.detail)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (keyword.isBlank()) return null
    val count = Regex("获取\\s*(\\d+)\\s*条结果").find(event.detail)?.groupValues?.getOrNull(1)
    return if (count.isNullOrBlank()) "已搜索 $keyword" else "已搜索 $keyword（共${count}条结果）"
}

@Composable
internal fun CopyTextButton(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(4.dp))
        Text(label)
    }
}
