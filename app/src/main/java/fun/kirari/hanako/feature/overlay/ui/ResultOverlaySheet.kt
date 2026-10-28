package `fun`.kirari.hanako.feature.overlay.ui

import `fun`.kirari.hanako.core.ui.richtext.MarkdownLatexText

import `fun`.kirari.hanako.feature.overlay.state.AutoRunState
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import `fun`.kirari.hanako.feature.overlay.state.OverlaySheetMode
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.platform.clipboard.copyToClipboardWithToast
import `fun`.kirari.hanako.core.model.ProcessingEvent
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.model.displayedAnswerVersions
import `fun`.kirari.hanako.core.model.latestAnswerText
import `fun`.kirari.hanako.core.ui.components.AnswerActionBar
import `fun`.kirari.hanako.core.ui.components.AnswerSwitchDirection
import `fun`.kirari.hanako.core.ui.components.AnimatedAnswerVersionContent
import `fun`.kirari.hanako.core.ui.components.ResultContentCard

@Composable
internal fun ResultOverlaySheet(
    uiState: OverlayUiState,
    onClose: () -> Unit,
    panelHeightPx: Int,
    onRegenerate: () -> Unit
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val panelMaxHeight = with(density) { panelHeightPx.toDp() }
    val answerVersions = remember(uiState.result?.id, uiState.result?.answerVersions, uiState.result?.answer) {
        uiState.result?.displayedAnswerVersions().orEmpty()
    }
    var currentVersionIndex by remember(uiState.result?.id, answerVersions.size) {
        mutableStateOf((answerVersions.size - 1).coerceAtLeast(0))
    }
    var switchDirection by remember { mutableStateOf(AnswerSwitchDirection.NONE) }
    val displayedAnswer = when {
        uiState.working -> uiState.liveAnswerText
        answerVersions.isNotEmpty() -> answerVersions.getOrNull(currentVersionIndex)?.text.orEmpty()
        else -> uiState.result?.latestAnswerText().orEmpty()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        var closeRequested by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelMaxHeight)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                SheetTitleRow(
                    title = { Text("Hanako") },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 8.dp),
                    onClose = {
                        if (closeRequested) return@SheetTitleRow
                        closeRequested = true
                        onClose()
                    }
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ResultImageCard(uiState)
                    if (uiState.settings.processingRoute == ProcessingRoute.OCR_THEN_LLM) {
                        OcrResultCard(uiState)
                    }
                    AnswerResultCard(
                        answerText = displayedAnswer,
                        versionCount = answerVersions.size,
                        currentVersionIndex = currentVersionIndex,
                        switchDirection = switchDirection,
                        working = uiState.working,
                        searchStatus = searchStatusText(uiState.result?.events.orEmpty()),
                        onPreviousVersion = {
                            if (currentVersionIndex > 0) {
                                switchDirection = AnswerSwitchDirection.PREVIOUS
                                currentVersionIndex -= 1
                            }
                        },
                        onNextVersion = {
                            if (currentVersionIndex < answerVersions.lastIndex) {
                                switchDirection = AnswerSwitchDirection.NEXT
                                currentVersionIndex += 1
                            }
                        },
                        onRegenerate = onRegenerate
                    )
                    uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        SideEffect { closeRequested }
    }
}

@Composable
private fun ResultImageCard(uiState: OverlayUiState) {
    ResultContentCard(title = "原图") {
        uiState.selectedBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

@Composable
private fun OcrResultCard(uiState: OverlayUiState) {
    val context = LocalContext.current
    ResultContentCard(
        title = "OCR 结果",
        actions = {
            if (!uiState.working && uiState.liveOcrText.isNotBlank()) {
                SmallHeaderAction(
                    label = "复制原文",
                    onClick = {
                        copyToClipboardWithToast(context, "Hanako OCR 原文", uiState.liveOcrText, "已复制 OCR 原文")
                    }
                )
            }
        }
    ) {
        if (uiState.liveOcrText.isBlank() && uiState.working) {
            LoadingLine("正在识别文字…")
        } else {
            val ocrText = uiState.liveOcrText
            if (ocrText.isNotBlank()) {
                MarkdownLatexText(
                    content = ocrText,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text("暂无内容")
            }
        }
    }
}

@Composable
private fun AnswerResultCard(
    answerText: String,
    versionCount: Int,
    currentVersionIndex: Int,
    switchDirection: AnswerSwitchDirection,
    working: Boolean,
    searchStatus: String?,
    onPreviousVersion: () -> Unit,
    onNextVersion: () -> Unit,
    onRegenerate: () -> Unit
) {
    val context = LocalContext.current
    ResultContentCard(
        title = "答案",
        actions = {
            AnswerActionBar(
                versionCount = versionCount,
                currentVersionIndex = currentVersionIndex,
                canRegenerate = true,
                regenerating = working,
                onPreviousVersion = onPreviousVersion,
                onNextVersion = onNextVersion,
                onCopy = {
                    copyToClipboardWithToast(context, "Hanako 原始答案", answerText, "已复制全文")
                },
                onRegenerate = onRegenerate
            )
        }
    ) {
        searchStatus?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            working && answerText.isBlank() -> LoadingLine("正在生成答案…")
            working -> MarkdownLatexText(
                content = answerText,
                modifier = Modifier.fillMaxWidth()
            )
            answerText.isNotBlank() -> AnimatedAnswerVersionContent(
                text = answerText,
                direction = switchDirection
            ) { currentText ->
                MarkdownLatexText(
                    content = currentText,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> Text("暂无内容")
        }
    }
}

@Composable
private fun SmallHeaderAction(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun searchStatusText(events: List<ProcessingEvent>): String? {
    val searchEvent = events.lastOrNull { it.title == "正在联网搜索" || it.title == "联网搜索完成" } ?: return null
    val keyword = Regex("关键词：([^，]+)").find(searchEvent.detail)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (keyword.isBlank()) return null
    val count = Regex("获取\\s*(\\d+)\\s*条结果").find(searchEvent.detail)?.groupValues?.getOrNull(1)
    return if (count.isNullOrBlank()) {
        "已搜索 $keyword"
    } else {
        "已搜索 $keyword（共${count}条结果）"
    }
}

@Composable
private fun LoadingLine(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp)
        Text(text)
    }
}
