package `fun`.kirari.hanako.feature.history.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.model.FollowUpTurn
import `fun`.kirari.hanako.core.model.QuotedFragment
import `fun`.kirari.hanako.core.model.displayedAssistantVersions
import `fun`.kirari.hanako.core.model.currentAssistantText
import `fun`.kirari.hanako.core.ui.components.AnimatedAnswerVersionContent
import `fun`.kirari.hanako.core.ui.components.AnswerActionBar
import `fun`.kirari.hanako.core.ui.components.AnswerSwitchDirection
import `fun`.kirari.hanako.core.ui.components.CopyFeedbackAction
import `fun`.kirari.hanako.core.ui.components.HanakoTextFieldShape
import `fun`.kirari.hanako.core.ui.components.ResultContentCard
import `fun`.kirari.hanako.platform.clipboard.copyToClipboardWithToast

@Composable
internal fun HistoryChatTurn(
    historyId: String,
    turn: FollowUpTurn,
    sending: Boolean,
    isLatest: Boolean,
    retryEnabled: Boolean,
    onSelectText: (String) -> Unit,
    onRetry: () -> Unit,
    highlightedBlockId: String? = null,
    underlinedBlockIds: Set<String> = emptySet(),
    onBlockFocused: (HistoryRenderedBlock) -> Unit = {},
    onBlockPositioned: (HistoryRenderedBlock) -> Unit = {},
    onQuoteClick: (QuotedFragment) -> Unit = {}
) {
    val context = LocalContext.current
    val versions = remember(turn.id, turn.assistantVersions, turn.assistantText, turn.completed) {
        turn.displayedAssistantVersions()
    }
    var currentVersionIndex by remember(turn.id) {
        mutableStateOf((versions.size - 1).coerceAtLeast(0))
    }
    var switchDirection by remember(turn.id) { mutableStateOf(AnswerSwitchDirection.NONE) }
    LaunchedEffect(versions.size, sending) {
        if (versions.isNotEmpty() && (!sending || currentVersionIndex !in versions.indices)) {
            currentVersionIndex = versions.lastIndex
        }
    }
    val displayedText = when {
        turn.pendingAssistantText.isNotBlank() -> turn.pendingAssistantText
        versions.isNotEmpty() -> versions.getOrNull(currentVersionIndex)?.text.orEmpty()
        else -> turn.currentAssistantText()
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)) + slideInVertically(tween(300)) { it / 8 }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            UserMessageBubble(turn = turn, onQuoteClick = onQuoteClick)
            ResultContentCard(
                title = "Hanako",
                supportingText = turn.modelSummary.takeIf(String::isNotBlank),
                modifier = Modifier.fillMaxWidth(),
                actions = {
                    AnswerActionBar(
                        versionCount = if (isLatest && !sending) versions.size else 1,
                        currentVersionIndex = if (isLatest && !sending) currentVersionIndex else 0,
                        canRegenerate = isLatest && (retryEnabled || sending),
                        regenerating = sending,
                        onPreviousVersion = {
                            if (currentVersionIndex > 0) {
                                switchDirection = AnswerSwitchDirection.PREVIOUS
                                currentVersionIndex -= 1
                            }
                        },
                        onNextVersion = {
                            if (currentVersionIndex < versions.lastIndex) {
                                switchDirection = AnswerSwitchDirection.NEXT
                                currentVersionIndex += 1
                            }
                        },
                        onSelectSource = { onSelectText(displayedText) },
                        sourceSelectionEnabled = displayedText.isNotBlank(),
                        onCopy = {
                            copyToClipboardWithToast(context, "Hanako 追问回答", displayedText, "已复制回答")
                        },
                        onRegenerate = onRetry
                    )
                }
            ) {
                AssistantTurnContent(
                    text = displayedText,
                    sending = sending,
                    completed = turn.completed,
                    errorMessage = turn.errorMessage,
                    switchDirection = switchDirection,
                    historyId = historyId,
                    messageId = turn.id,
                    answerVersionId = versions.getOrNull(currentVersionIndex)?.id,
                    sourceRevision = turn.createdAtMillis,
                    highlightedBlockId = highlightedBlockId,
                    underlinedBlockIds = underlinedBlockIds,
                    onBlockFocused = onBlockFocused,
                    onBlockPositioned = onBlockPositioned
                )
            }
        }
    }
}

@Composable
private fun UserMessageBubble(turn: FollowUpTurn, onQuoteClick: (QuotedFragment) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp).animateContentSize(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                turn.quotedFragments.forEach { quote ->
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 6.dp).clickable { onQuoteClick(quote) }
                    ) {
                        Text(quote.anchor.previewLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Text(text = turn.userText, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        CopyFeedbackAction(
            enabled = turn.userText.isNotBlank(),
            compact = true,
            modifier = Modifier.padding(top = 5.dp, end = 8.dp).offset(y = 2.dp),
            onCopy = {
                copyToClipboardWithToast(context, "Hanako 追问", turn.userText, "已复制问题")
            }
        )
    }
}

@Composable
private fun AssistantTurnContent(
    text: String,
    sending: Boolean,
    completed: Boolean,
    errorMessage: String?,
    switchDirection: AnswerSwitchDirection,
    historyId: String,
    messageId: String,
    answerVersionId: String?,
    sourceRevision: Long,
    highlightedBlockId: String?,
    underlinedBlockIds: Set<String>,
    onBlockFocused: (HistoryRenderedBlock) -> Unit,
    onBlockPositioned: (HistoryRenderedBlock) -> Unit
) {
    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            text.isNotBlank() && !sending -> AnimatedAnswerVersionContent(
                text = text,
                direction = switchDirection
            ) {
                HistoryMarkdownOrEmpty(
                    it,
                    historyId = historyId,
                    messageId = messageId,
                    answerVersionId = answerVersionId,
                    sourceRevision = sourceRevision,
                    highlightedBlockId = highlightedBlockId,
                    underlinedBlockIds = underlinedBlockIds,
                    onBlockFocused = onBlockFocused,
                    onBlockPositioned = onBlockPositioned
                )
            }
            text.isNotBlank() -> HistoryMarkdownOrEmpty(
                text,
                historyId = historyId,
                messageId = messageId,
                answerVersionId = answerVersionId,
                sourceRevision = sourceRevision,
                highlightedBlockId = highlightedBlockId,
                underlinedBlockIds = underlinedBlockIds,
                onBlockFocused = onBlockFocused,
                onBlockPositioned = onBlockPositioned
            )
            sending || !completed -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("正在回答", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Text("暂无内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = errorMessage != null) {
            errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun HistoryChatComposer(
    value: String,
    quotedFragments: List<QuotedFragment> = emptyList(),
    onRemoveQuote: (String) -> Unit = {},
    enabled: Boolean,
    sending: Boolean,
    modelLabel: String,
    onSelectModel: (() -> Unit)?,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
            AnimatedVisibility(
                visible = quotedFragments.isNotEmpty(),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150))
            ) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).animateContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(quotedFragments, key = { it.id }) { quote ->
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(quote.anchor.previewLabel, modifier = Modifier.padding(start = 8.dp, top = 5.dp, bottom = 5.dp))
                                IconButton(onClick = { onRemoveQuote(quote.id) }, modifier = Modifier.size(30.dp)) {
                                    Text("×")
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                label = { Text(modelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                placeholder = { Text("继续提问") },
                leadingIcon = {
                    IconButton(
                        onClick = { onSelectModel?.invoke() },
                        enabled = enabled && onSelectModel != null
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "选择会话模型，当前$modelLabel")
                    }
                },
                modifier = Modifier.weight(1f),
                maxLines = 5,
                shape = HanakoTextFieldShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            IconButton(onClick = onSend, enabled = enabled && value.isNotBlank(), modifier = Modifier.size(48.dp)) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                }
            }
            }
        }
    }
}
