package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.core.data.AnswerStyle
import `fun`.kirari.hanako.core.model.ProcessingRoute
import kotlinx.coroutines.delay

@Composable
internal fun ModeModelRow(
    route: ProcessingRoute,
    answerStyle: AnswerStyle,
    ocrModel: String,
    ocrProviderName: String?,
    llmModel: String,
    llmProviderName: String?,
    onToggleProcessingRoute: () -> Unit,
    onToggleAnswerStyle: () -> Unit,
    onPickModel: (ModelPurpose) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = route,
            transitionSpec = { verticalTextTransform() },
            label = "modeModelRow"
        ) { currentRoute ->
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (currentRoute == ProcessingRoute.OCR_THEN_LLM) "OCR模式" else "多模态模式",
                        modifier = Modifier.clickable(onClick = onToggleProcessingRoute),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    if (currentRoute == ProcessingRoute.OCR_THEN_LLM) {
                        ModelConfigCarousel(
                            items = listOf(
                                ModelConfigItem("OCR", ocrModel, ocrProviderName, ModelPurpose.OCR),
                                ModelConfigItem("LLM", llmModel, llmProviderName, ModelPurpose.TEXT)
                            ),
                            onClick = onPickModel
                        )
                    } else {
                        ModelConfigCarousel(
                            items = listOf(ModelConfigItem("LLM", llmModel, llmProviderName, ModelPurpose.VISION)),
                            onClick = onPickModel
                        )
                    }
                }
                Text(
                    text = if (answerStyle == AnswerStyle.CHALLENGE) "✦ 先试试看 · 只给提示" else "✦ 标准解答",
                    modifier = Modifier.clickable(onClick = onToggleAnswerStyle),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (answerStyle == AnswerStyle.CHALLENGE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ModelConfigCarousel(
    items: List<ModelConfigItem>,
    onClick: (ModelPurpose) -> Unit
) {
    var index by remember(items) { mutableStateOf(0) }
    LaunchedEffect(items) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(3_000)
            index = (index + 1) % items.size
        }
    }
    Box(
        modifier = Modifier
            .width(220.dp)
            .height(20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = items[index % items.size],
            transitionSpec = { verticalTextTransform() },
            label = "modelConfigCarousel"
        ) { item ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                ModelConfigText(
                    label = item.label,
                    model = item.model,
                    providerName = item.providerName,
                    onClick = { onClick(item.purpose) }
                )
            }
        }
    }
}

private data class ModelConfigItem(
    val label: String,
    val model: String,
    val providerName: String?,
    val purpose: ModelPurpose
)

@Composable
private fun ModelConfigText(
    label: String,
    model: String,
    providerName: String?,
    onClick: () -> Unit
) {
    val text = buildString {
        append(label)
        append("：")
        append(model.ifBlank { "未配置模型" })
        providerName?.takeIf { it.isNotBlank() }?.let {
            append("（")
            append(it)
            append("）")
        }
    }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        textAlign = TextAlign.End,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 20.sp
    )
}

private fun verticalTextTransform() = (
    androidx.compose.animation.slideInVertically(
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 240,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        )
    ) { it / 2 } + androidx.compose.animation.fadeIn(
        animationSpec = androidx.compose.animation.core.tween(180)
    )
).togetherWith(
    androidx.compose.animation.slideOutVertically(
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 240,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        )
    ) { -it / 2 } + androidx.compose.animation.fadeOut(
        animationSpec = androidx.compose.animation.core.tween(180)
    )
)
