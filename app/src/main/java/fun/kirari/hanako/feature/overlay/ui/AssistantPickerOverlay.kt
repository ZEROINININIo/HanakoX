package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.AssistantPreset
import `fun`.kirari.hanako.core.data.previewPrompt
import kotlinx.coroutines.delay

@Composable
internal fun AssistantPickerOverlay(
    assistants: List<AssistantPreset>,
    selectedAssistantId: String?,
    closing: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onSelect: (String) -> Unit
) {
    val density = LocalDensity.current.density
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    LaunchedEffect(closing) {
        if (closing) {
            visible = false
            delay(220)
            onDismissFinished()
        }
    }
    val overlayAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "assistantPickerOverlayAlpha"
    )
    val cardAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "assistantPickerCardAlpha"
    )
    val cardTranslationY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 0f else if (closing) -20f else 20f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "assistantPickerCardTranslationY"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f * overlayAlpha))
            .clickable(onClick = {
                if (!closing) onDismiss()
            }),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .width(300.dp)
                .heightIn(max = 460.dp)
                .graphicsLayer {
                    alpha = cardAlpha
                    translationY = cardTranslationY * density
                }
                .clickable(enabled = false, onClick = { })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("选择助手", style = MaterialTheme.typography.titleMedium)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(assistants, key = { it.id }) { assistant ->
                        AssistantPickerItem(
                            assistant = assistant,
                            selected = assistant.id == selectedAssistantId,
                            onSelect = onSelect
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (!closing) onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun AssistantPickerItem(
    assistant: AssistantPreset,
    selected: Boolean,
    onSelect: (String) -> Unit
) {
    Surface(
        onClick = { onSelect(assistant.id) },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(assistant.name, style = MaterialTheme.typography.titleSmall)
            Text(
                assistant.previewPrompt().replace('\n', ' '),
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
