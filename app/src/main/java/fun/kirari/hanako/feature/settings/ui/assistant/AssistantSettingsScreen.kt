package `fun`.kirari.hanako.feature.settings.ui.assistant

import `fun`.kirari.hanako.feature.settings.ui.settings.DeleteConfirmDialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.AssistantPreset
import `fun`.kirari.hanako.core.data.previewPrompt
import `fun`.kirari.hanako.feature.settings.ui.assistant.components.AssistantSelector
import `fun`.kirari.hanako.core.ui.components.SectionCard

@Composable
fun AssistantSettingsScreen(
    settings: AppSettings,
    onAddAssistant: () -> Unit,
    onDeleteAssistant: (String) -> Unit,
    onOpenAssistant: (String) -> Unit,
    onSelectAssistant: (String) -> Unit
) {
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    val activeId = settings.selectedAssistantId

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "助手配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "点击卡片即可切换当前生效的助手",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onAddAssistant) {
                    Text("新增")
                }
            }
        }
        items(settings.assistants, key = { it.id }) { assistant ->
            AssistantListItem(
                assistant = assistant,
                isActive = assistant.id == activeId,
                onSelect = onSelectAssistant,
                onOpen = onOpenAssistant,
                onRequestDelete = { deleteTargetId = it }
            )
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    val deleteTarget = settings.assistants.firstOrNull { it.id == deleteTargetId }
    if (deleteTarget != null) {
        DeleteConfirmDialog(
            title = "删除助手",
            message = "确认删除 ${deleteTarget.name}？",
            onDismiss = { deleteTargetId = null },
            onConfirm = {
                onDeleteAssistant(deleteTarget.id)
                deleteTargetId = null
            }
        )
    }
}

@Composable
fun AssistantDetailScreen(
    assistant: AssistantPreset,
    onUpdateAssistant: (AssistantPreset) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "助手配置") {
                AssistantSelector(
                    assistant = assistant,
                    onChange = onUpdateAssistant
                )
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun AssistantListItem(
    assistant: AssistantPreset,
    isActive: Boolean,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRequestDelete: (String) -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "assistantContainer"
    )
    val dotColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        label = "assistantDot"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onSelect(assistant.id) },
                onLongClick = { onRequestDelete(assistant.id) }
            ),
        shape = RoundedCornerShape(20.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SelectIndicator(active = isActive, color = dotColor)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        assistant.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    AnimatedVisibility(
                        visible = isActive,
                        enter = fadeIn() + scaleIn(initialScale = 0.6f),
                        exit = fadeOut() + scaleOut(targetScale = 0.6f)
                    ) {
                        ActiveBadge()
                    }
                }
                Text(
                    assistant.previewPrompt().replace('\n', ' '),
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { onOpen(assistant.id) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "编辑助手",
                    tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectIndicator(active: Boolean, color: Color) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = active,
            enter = scaleIn(initialScale = 0.3f, animationSpec = spring(dampingRatio = 0.6f)),
            exit = scaleOut(targetScale = 0.3f)
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ActiveBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Text(
            "生效中",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
