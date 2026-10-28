package `fun`.kirari.hanako.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun AnswerActionButtons(
    regenerating: Boolean,
    canRegenerate: Boolean,
    onSelectSource: (() -> Unit)?,
    sourceSelectionEnabled: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onSelectSource != null) {
            AnswerIconAction(
                icon = Icons.Default.TextFields,
                contentDescription = "选区复制原文",
                enabled = sourceSelectionEnabled,
                onClick = onSelectSource
            )
        }
        CopyFeedbackAction(onCopy = onCopy)
        if (canRegenerate) {
            AnswerIconAction(
                icon = Icons.Default.Refresh,
                contentDescription = "重新生成",
                enabled = !regenerating,
                onClick = onRegenerate,
                content = { RegenerateIcon(regenerating) }
            )
        }
    }
}

@Composable
private fun AnswerIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content?.invoke() ?: Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun CopyFeedbackAction(
    onCopy: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val colors = MaterialTheme.colorScheme
    val height = if (compact) 22.dp else 32.dp
    val horizontalPadding = if (compact) 5.dp else 8.dp
    val iconSize = if (compact) 12.dp else 16.dp
    Surface(
        onClick = {
            onCopy()
            copied = true
        },
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (compact) colors.surfaceContainerHighest.copy(alpha = 0.32f) else colors.surfaceContainerHighest,
        modifier = modifier.height(height)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)
        ) {
            CopyIcon(copied, iconSize)
            AnimatedVisibility(
                visible = copied,
                enter = expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = tween(280, easing = EmphasizedDecelerate)
                ) + fadeIn(tween(200, easing = EmphasizedDecelerate)),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.Start,
                    animationSpec = tween(200, easing = EmphasizedAccelerate)
                ) + fadeOut(tween(120, easing = EmphasizedAccelerate))
            ) {
                Text(
                    text = "已复制",
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CopyIcon(copied: Boolean, size: Dp) {
    val colors = MaterialTheme.colorScheme
    val scale = remember { Animatable(1f) }
    LaunchedEffect(copied) {
        if (copied) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
    }
    Box(
        modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = copied,
            transitionSpec = {
                fadeIn(tween(180, easing = EmphasizedDecelerate))
                    .togetherWith(fadeOut(tween(120, easing = EmphasizedAccelerate)))
            },
            label = "copy-icon-morph"
        ) {
            Icon(
                imageVector = if (it) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(size),
                tint = if (it) colors.primary else colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RegenerateIcon(regenerating: Boolean) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    if (regenerating) {
        val transition = rememberInfiniteTransition(label = "regen-spin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "regen-angle"
        )
        Icon(
            Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = angle },
            tint = tint
        )
    } else {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
    }
}

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
