package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun CropHeaderRow(
    assistantName: String,
    hasMultipleAssistants: Boolean,
    switchDirection: AssistantSwitchDirection,
    onSelectPrevious: () -> Unit,
    onSelectNext: () -> Unit,
    onOpenPicker: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            AssistantSwitcher(
                assistantName = assistantName,
                hasMultipleAssistants = hasMultipleAssistants,
                switchDirection = switchDirection,
                onSelectPrevious = onSelectPrevious,
                onSelectNext = onSelectNext,
                onOpenPicker = onOpenPicker
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Text("×", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun AssistantSwitcher(
    assistantName: String,
    hasMultipleAssistants: Boolean,
    switchDirection: AssistantSwitchDirection,
    onSelectPrevious: () -> Unit,
    onSelectNext: () -> Unit,
    onOpenPicker: () -> Unit
) {
    Row(
        modifier = Modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasMultipleAssistants) {
            SwitchArrowButton(symbol = "〈", onClick = onSelectPrevious)
        }
        Box(
            modifier = Modifier
                .width(168.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onOpenPicker)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = assistantName,
                transitionSpec = {
                    assistantNameTransform(switchDirection)
                },
                label = "assistantName"
            ) { currentAssistantName ->
                Text(
                    text = "助手：$currentAssistantName",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
        }
        if (hasMultipleAssistants) {
            SwitchArrowButton(symbol = "〉", onClick = onSelectNext)
        }
    }
}

@Composable
private fun SwitchArrowButton(
    symbol: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 36.dp, height = 36.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
