package `fun`.kirari.hanako.feature.history.ui

import android.graphics.Rect
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.rememberLazyListState
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.QuotedFragment
import `fun`.kirari.hanako.core.model.decodeHistoryBitmap
import `fun`.kirari.hanako.core.model.displayedAnswerVersions
import `fun`.kirari.hanako.core.model.latestAnswerText
import `fun`.kirari.hanako.core.model.loadHistoryBitmap
import `fun`.kirari.hanako.core.model.currentAssistantText
import `fun`.kirari.hanako.core.ui.components.AnswerSwitchDirection
import `fun`.kirari.hanako.core.ui.richtext.MarkdownLatexText
import `fun`.kirari.hanako.core.ui.image.ImagePreviewOverlay
import `fun`.kirari.hanako.feature.home.presentation.RegisterScrollToTopHandler
import kotlinx.coroutines.launch
import `fun`.kirari.hanako.platform.clipboard.copyToClipboardWithToast

private data class HistoryQuoteViewer(
    val quote: QuotedFragment,
    val question: String,
    val answer: String
)

private fun historyQuoteViewer(
    result: ProcessingResult,
    displayedAnswer: String,
    quote: QuotedFragment
): HistoryQuoteViewer {
    val followUp = result.followUpTurns.firstOrNull { it.id == quote.anchor.messageId }
    return if (followUp == null) {
        HistoryQuoteViewer(quote, result.extractedText.ifBlank { "图片题目" }, displayedAnswer.ifBlank { "暂无回答" })
    } else {
        HistoryQuoteViewer(quote, followUp.userText.ifBlank { "暂无问题" }, followUp.currentAssistantText().ifBlank { "暂无回答" })
    }
}

@Composable
fun HistoryDetailScreen(
    scrollRoute: String,
    result: ProcessingResult?,
    regenerating: Boolean = false,
    chatSending: Boolean = false,
    runningAnswerVersionIndex: Int? = null,
    conversationModelLabel: String = "选择模型",
    onRegenerate: ((ProcessingResult) -> Unit)? = null,
    onSelectConversationModel: (() -> Unit)? = null,
    onSendFollowUp: ((String, List<QuotedFragment>) -> Unit)? = null,
    onRetryFollowUp: (() -> Unit)? = null,
    onBlockFocusChanged: (Boolean) -> Unit = {}
) {
    if (result == null) {
        MissingHistoryDetail()
        return
    }

    val answerVersions = remember(result.id, result.answerVersions, result.answer) {
        result.displayedAnswerVersions()
    }
    val screenshots = remember(result.allScreenshotPaths, result.screenshotBase64) {
        result.allScreenshotPaths.mapNotNull { it.loadHistoryBitmap() }.toMutableList().apply {
            if (isEmpty()) result.screenshotBase64?.decodeHistoryBitmap()?.let(::add)
        }
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var currentVersionIndex by remember(result.id, answerVersions.size) {
        mutableStateOf((answerVersions.size - 1).coerceAtLeast(0))
    }
    var switchDirection by remember(result.id) { mutableStateOf(AnswerSwitchDirection.NONE) }
    var followUpDraft by remember(result.id) { mutableStateOf("") }
    var confirmOriginalRegeneration by remember { mutableStateOf(false) }
    var rawTextForSelection by remember(result.id) { mutableStateOf<String?>(null) }
    var previewImageIndex by remember { mutableStateOf(-1) }
    var imageBounds by remember { mutableStateOf<Rect?>(null) }
    var draftQuotes by remember(result.id) { mutableStateOf(emptyList<QuotedFragment>()) }
    var focusedBlock by remember(result.id) { mutableStateOf<HistoryRenderedBlock?>(null) }
    var blockRects by remember(result.id) { mutableStateOf<Map<String, HistoryRenderedBlock>>(emptyMap()) }
    var menuBlock by remember(result.id) { mutableStateOf<HistoryRenderedBlock?>(null) }
    var viewerQuote by remember(result.id) { mutableStateOf<HistoryQuoteViewer?>(null) }
    val context = LocalContext.current

    LaunchedEffect(menuBlock != null) {
        onBlockFocusChanged(menuBlock != null)
    }

    LaunchedEffect(regenerating, runningAnswerVersionIndex, answerVersions.size) {
        if (regenerating && answerVersions.isNotEmpty()) {
            currentVersionIndex = runningAnswerVersionIndex
                ?.coerceIn(0, answerVersions.lastIndex)
                ?: answerVersions.lastIndex
        }
    }
    RegisterScrollToTopHandler(route = scrollRoute) {
        coroutineScope.launch { listState.animateScrollToItem(0) }
    }

    val displayedAnswer = answerVersions.getOrNull(currentVersionIndex)?.text ?: result.latestAnswerText()
    val density = LocalDensity.current
    val bottomInset = with(density) {
        WindowInsets.navigationBars.union(WindowInsets.ime).getBottom(this).toDp()
    }
    val requestOriginalRegeneration = onRegenerate?.let { regenerate ->
        {
            if (result.followUpTurns.isEmpty()) regenerate(result)
            else confirmOriginalRegeneration = true
        }
    }

    HistoryDetailContent(
        result = result,
        screenshots = screenshots,
        answerVersions = answerVersions,
        displayedAnswer = displayedAnswer,
        currentVersionIndex = currentVersionIndex,
        switchDirection = switchDirection,
        regenerating = regenerating,
        chatSending = chatSending,
        bottomInset = bottomInset,
        listState = listState,
        followUpDraft = followUpDraft,
        conversationModelLabel = conversationModelLabel,
        onPreviousAnswerVersion = {
            if (currentVersionIndex > 0) {
                switchDirection = AnswerSwitchDirection.PREVIOUS
                currentVersionIndex -= 1
            }
        },
        onNextAnswerVersion = {
            if (currentVersionIndex < answerVersions.lastIndex) {
                switchDirection = AnswerSwitchDirection.NEXT
                currentVersionIndex += 1
            }
        },
        onRegenerate = requestOriginalRegeneration,
        onSelectRawText = { rawTextForSelection = it },
        onSingleImagePositioned = { imageBounds = it },
        onPreviewImage = { previewImageIndex = it },
        onSelectConversationModel = onSelectConversationModel,
        onFollowUpDraftChange = { followUpDraft = it },
        draftQuotes = draftQuotes,
        highlightedBlockId = focusedBlock?.anchor?.blockId,
        underlinedBlockIds = result.followUpTurns.flatMap { it.quotedFragments }.map { it.anchor.blockId }.toSet(),
        onBlockFocused = {
            focusedBlock = it
            menuBlock = it
        },
        onBlockPositioned = { positioned -> blockRects = blockRects + (positioned.anchor.blockId to positioned) },
        onRemoveDraftQuote = { id -> draftQuotes = draftQuotes.filterNot { it.id == id } },
        onQuoteClick = { quote ->
            val positioned = blockRects[quote.anchor.blockId] ?: HistoryRenderedBlock(quote.anchor, Rect())
            focusedBlock = positioned
            menuBlock = positioned
            historyMessageItemIndex(result, screenshots, quote.anchor.messageId)?.let { itemIndex ->
                coroutineScope.launch { listState.animateScrollToItem(itemIndex) }
            }
        },
        onSendFollowUp = onSendFollowUp?.let { send ->
            {
                val prompt = followUpDraft.trim()
                if (prompt.isNotBlank()) {
                    followUpDraft = ""
                    val quotes = draftQuotes
                    draftQuotes = emptyList()
                    send(prompt, quotes)
                }
            }
        },
        onRetryFollowUp = onRetryFollowUp
    )

    menuBlock?.let { block ->
        val density = LocalDensity.current
        val target = block.anchor
        val quoteCount = result.followUpTurns.sumOf { turn ->
            turn.quotedFragments.count { it.anchor.blockId == target.blockId }
        }
        val menuBottomOffsetPx = with(density) {
            ((if (quoteCount > 0) 190.dp else 142.dp) + bottomInset).roundToPx()
        }
        val menuProgress = remember(block.anchor.blockId) { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(block.anchor.blockId) {
            menuProgress.snapTo(0f)
            menuProgress.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(130))
        }
        Popup(
            alignment = androidx.compose.ui.Alignment.BottomCenter,
            offset = IntOffset(0, -menuBottomOffsetPx),
            onDismissRequest = {
                menuBlock = null
                focusedBlock = null
            },
            properties = PopupProperties(focusable = true)
        ) {
            Surface(
                modifier = Modifier.alpha(menuProgress.value),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                shape = androidx.compose.material3.MaterialTheme.shapes.medium
            ) {
                Column {
                    TextButton(
                        modifier = Modifier.width(120.dp),
                        onClick = {
                        copyToClipboardWithToast(context, "Hanako 引用片段", target.rawMarkdown, "已复制引用")
                        menuBlock = null
                        focusedBlock = null
                    }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Text("复制")
                        }
                    }
                    TextButton(
                        modifier = Modifier.width(120.dp),
                        onClick = {
                        block.toQuotedFragment().let { quote ->
                            if (draftQuotes.none { it.anchor.blockId == quote.anchor.blockId }) {
                                draftQuotes = draftQuotes + quote
                            }
                        }
                        menuBlock = null
                        focusedBlock = null
                    }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FormatQuote, contentDescription = null)
                            Text("引用")
                        }
                    }
                    if (quoteCount > 0) {
                        TextButton(
                            modifier = Modifier.width(120.dp),
                            onClick = {
                            viewerQuote = historyQuoteViewer(result, displayedAnswer, QuotedFragment(anchor = target))
                            menuBlock = null
                            focusedBlock = null
                        }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FormatQuote, contentDescription = null)
                            Text("查看引用（$quoteCount）")
                            }
                        }
                    }
                }
            }
        }
    }

    menuBlock?.let { block ->
        val cardProgress = remember(block.anchor.blockId) { androidx.compose.animation.core.Animatable(0.4f) }
        LaunchedEffect(block.anchor.blockId) {
            cardProgress.snapTo(0.7f)
            cardProgress.animateTo(
                1f,
                animationSpec = androidx.compose.animation.core.tween(
                    90,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            )
        }
        Popup(
            alignment = androidx.compose.ui.Alignment.Center,
            properties = PopupProperties(focusable = false, dismissOnClickOutside = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .graphicsLayer {
                        alpha = cardProgress.value
                        val scale = 0.9f + 0.1f * ((cardProgress.value - 0.7f) / 0.3f).coerceIn(0f, 1f)
                        scaleX = scale
                        scaleY = scale
                    },
                tonalElevation = 10.dp,
                shadowElevation = 18.dp,
                shape = androidx.compose.material3.MaterialTheme.shapes.large
            ) {
                MarkdownLatexText(
                    block.anchor.rawMarkdown,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    displayMathFillMaxWidth = false
                )
            }
        }
    }

    if (confirmOriginalRegeneration) {
        AlertDialog(
            onDismissRequest = { confirmOriginalRegeneration = false },
            title = { Text("重新生成首轮回答？") },
            text = { Text("这会删除全部后续对话，然后使用当前模型配置重新生成首轮答案。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmOriginalRegeneration = false
                    onRegenerate?.invoke(result)
                }) { Text("删除并重试") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOriginalRegeneration = false }) { Text("取消") }
            }
        )
    }
    rawTextForSelection?.let {
        RawTextSelectionSheet(rawText = it, onDismiss = { rawTextForSelection = null })
    }
    if (previewImageIndex in screenshots.indices) {
        ImagePreviewOverlay(
            visible = true,
            bitmap = screenshots[previewImageIndex],
            fileName = "hanako_history_${result.id}_$previewImageIndex",
            onDismiss = { previewImageIndex = -1 },
            sourceBounds = imageBounds
        )
    }
    viewerQuote?.let { quote ->
        AlertDialog(
            onDismissRequest = { viewerQuote = null },
            confirmButton = {
                TextButton(onClick = { viewerQuote = null }) { Text("关闭") }
            },
            title = { Text("引用内容") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("问题", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    MarkdownLatexText(
                        quote.question,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text("回答", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
                    MarkdownLatexText(
                        quote.answer,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text("引用片段", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
                    MarkdownLatexText(quote.quote.anchor.rawMarkdown, modifier = Modifier.padding(top = 8.dp))
                }
            }
        )
    }
}

private fun historyMessageItemIndex(
    result: ProcessingResult,
    screenshots: List<android.graphics.Bitmap>,
    messageId: String
): Int? {
    var index = 1 // header
    if (screenshots.isNotEmpty()) index++
    if (result.route == `fun`.kirari.hanako.core.model.ProcessingRoute.OCR_THEN_LLM) {
        if (messageId == "${result.id}:initial-question") return index
        index++
    }
    if (result.detail.isNotBlank()) index++
    if (result.automationAction != null || result.automationThought.isNotBlank()) {
        index++
        if (result.automationAction != null) index++
    } else {
        if (messageId == "${result.id}:initial-answer") return index
        index++
    }
    if (result.followUpTurns.isNotEmpty()) {
        index++ // continuation title
        result.followUpTurns.forEachIndexed { turnIndex, turn ->
            if (turn.id == messageId) return index + turnIndex
        }
    }
    return null
}
