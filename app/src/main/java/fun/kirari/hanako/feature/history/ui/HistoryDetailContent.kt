package `fun`.kirari.hanako.feature.history.ui

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.model.AnswerVersion
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.model.QuotedFragment
import `fun`.kirari.hanako.core.ui.components.AnimatedAnswerVersionContent
import `fun`.kirari.hanako.core.ui.components.AnswerActionBar
import `fun`.kirari.hanako.core.ui.components.AnswerSwitchDirection
import `fun`.kirari.hanako.core.ui.components.ResultContentCard
import `fun`.kirari.hanako.platform.clipboard.copyToClipboardWithToast

@Composable
internal fun HistoryDetailContent(
    result: ProcessingResult,
    screenshots: List<Bitmap>,
    answerVersions: List<AnswerVersion>,
    displayedAnswer: String,
    currentVersionIndex: Int,
    switchDirection: AnswerSwitchDirection,
    regenerating: Boolean,
    chatSending: Boolean,
    bottomInset: Dp,
    listState: LazyListState,
    followUpDraft: String,
    conversationModelLabel: String,
    onPreviousAnswerVersion: () -> Unit,
    onNextAnswerVersion: () -> Unit,
    onRegenerate: (() -> Unit)?,
    onSelectRawText: (String) -> Unit,
    onSingleImagePositioned: (Rect) -> Unit,
    onPreviewImage: (Int) -> Unit,
    onSelectConversationModel: (() -> Unit)?,
    onFollowUpDraftChange: (String) -> Unit,
    onSendFollowUp: (() -> Unit)?,
    onRetryFollowUp: (() -> Unit)?
    ,draftQuotes: List<QuotedFragment> = emptyList()
    ,highlightedBlockId: String? = null
    ,underlinedBlockIds: Set<String> = emptySet()
    ,onBlockFocused: (HistoryRenderedBlock) -> Unit = {}
    ,onBlockPositioned: (HistoryRenderedBlock) -> Unit = {}
    ,onRemoveDraftQuote: (String) -> Unit = {}
    ,onQuoteClick: (QuotedFragment) -> Unit = {}
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 148.dp + bottomInset
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = formatHistoryDetailHeader(result),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (screenshots.isNotEmpty()) {
                item {
                    HistoryScreenshots(screenshots, onSingleImagePositioned, onPreviewImage)
                }
            }
            if (result.route == ProcessingRoute.OCR_THEN_LLM) {
                item {
                    ResultContentCard(
                        title = "OCR 结果",
                        actions = {
                            CopyTextButton(
                                enabled = result.extractedText.isNotBlank(),
                                label = "复制原文",
                                onClick = {
                                    copyToClipboardWithToast(
                                        context,
                                        "Hanako OCR 原文",
                                        result.extractedText,
                                        "已复制 OCR 原文"
                                    )
                                }
                            )
                        }
                    ) {
                        HistoryMarkdownOrEmpty(
                            result.extractedText,
                            historyId = result.id,
                            messageId = "${result.id}:initial-question",
                            highlightedBlockId = highlightedBlockId,
                            underlinedBlockIds = underlinedBlockIds,
                            onBlockFocused = onBlockFocused,
                            onBlockPositioned = onBlockPositioned
                        )
                    }
                }
            }
            if (result.detail.isNotBlank()) {
                item { ResultContentCard(title = "请求详情") { Text(result.detail) } }
            }
            if (result.automationAction != null || result.automationThought.isNotBlank()) {
                item {
                    ResultContentCard(title = "思考过程") {
                        HistoryMarkdownOrEmpty(result.automationThought)
                    }
                }
                result.automationAction?.let { action ->
                    item {
                        ResultContentCard(
                            title = "工具调用",
                            actions = {
                                CopyTextButton(
                                    enabled = action.text.isNotBlank(),
                                    label = "复制内容",
                                    onClick = {
                                        copyToClipboardWithToast(
                                            context,
                                            "Hanako 自动模式工具内容",
                                            action.text,
                                            "已复制工具内容"
                                        )
                                    }
                                )
                            }
                        ) {
                            Text("调用了工具：${automationActionLabel(result)}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(action.text.ifBlank { "暂无内容" })
                        }
                    }
                }
            } else {
                item {
                    ResultContentCard(
                        title = "答案",
                        actions = {
                            AnswerActionBar(
                                    versionCount = answerVersions.size,
                                    currentVersionIndex = currentVersionIndex,
                                    canRegenerate = onRegenerate != null && !chatSending,
                                    regenerating = regenerating,
                                    onPreviousVersion = onPreviousAnswerVersion,
                                    onNextVersion = onNextAnswerVersion,
                                    onSelectSource = { onSelectRawText(displayedAnswer) },
                                    sourceSelectionEnabled = displayedAnswer.isNotBlank(),
                                    onCopy = {
                                        copyToClipboardWithToast(
                                            context,
                                            "Hanako 原始答案",
                                            displayedAnswer,
                                            "已复制原文"
                                        )
                                    },
                                    onRegenerate = { onRegenerate?.invoke() }
                                )
                        }
                    ) {
                        searchStatusText(result.events)?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        if (regenerating) {
                            HistoryMarkdownOrEmpty(
                                displayedAnswer,
                                historyId = result.id,
                                messageId = "${result.id}:initial-answer",
                                answerVersionId = answerVersions.getOrNull(currentVersionIndex)?.id,
                                sourceRevision = result.createdAtMillis,
                                highlightedBlockId = highlightedBlockId,
                                underlinedBlockIds = underlinedBlockIds,
                                onBlockFocused = onBlockFocused,
                                onBlockPositioned = onBlockPositioned
                            )
                        } else {
                            AnimatedAnswerVersionContent(displayedAnswer, switchDirection) {
                                HistoryMarkdownOrEmpty(
                                    it,
                                    historyId = result.id,
                                    messageId = "${result.id}:initial-answer",
                                    answerVersionId = answerVersions.getOrNull(currentVersionIndex)?.id,
                                    sourceRevision = result.createdAtMillis,
                                    highlightedBlockId = highlightedBlockId,
                                    underlinedBlockIds = underlinedBlockIds,
                                    onBlockFocused = onBlockFocused,
                                    onBlockPositioned = onBlockPositioned
                                )
                            }
                        }
                    }
                }
            }
            if (result.followUpTurns.isNotEmpty()) {
                item {
                    Text(
                        text = "继续对话",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                itemsIndexed(result.followUpTurns, key = { _, turn -> turn.id }) { index, turn ->
                    HistoryChatTurn(
                        historyId = result.id,
                        turn = turn,
                        sending = chatSending && index == result.followUpTurns.lastIndex,
                        isLatest = index == result.followUpTurns.lastIndex,
                        retryEnabled = !chatSending && onRetryFollowUp != null,
                        onSelectText = onSelectRawText,
                        onRetry = { onRetryFollowUp?.invoke() }
                        ,highlightedBlockId = highlightedBlockId
                        ,underlinedBlockIds = underlinedBlockIds
                        ,onBlockFocused = onBlockFocused
                        ,onBlockPositioned = onBlockPositioned
                        ,onQuoteClick = onQuoteClick
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        HistoryChatComposer(
            value = followUpDraft,
            quotedFragments = draftQuotes,
            onRemoveQuote = onRemoveDraftQuote,
            enabled = !chatSending && !regenerating && onSendFollowUp != null,
            sending = chatSending,
            modelLabel = conversationModelLabel,
            onSelectModel = onSelectConversationModel,
            onValueChange = onFollowUpDraftChange,
            onSend = { onSendFollowUp?.invoke() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
