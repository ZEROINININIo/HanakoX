package `fun`.kirari.hanako.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal enum class AnswerSwitchDirection {
    PREVIOUS, NEXT, NONE
}

@Composable
internal fun AnswerActionBar(
    versionCount: Int,
    currentVersionIndex: Int,
    canRegenerate: Boolean,
    regenerating: Boolean,
    onPreviousVersion: () -> Unit,
    onNextVersion: () -> Unit,
    onSelectSource: (() -> Unit)? = null,
    sourceSelectionEnabled: Boolean = true,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (versionCount > 1) {
            VersionSwitcher(
                versionCount = versionCount,
                currentIndex = currentVersionIndex,
                onPrevious = onPreviousVersion,
                onNext = onNextVersion
            )
        }
        AnswerActionButtons(
            regenerating = regenerating,
            canRegenerate = canRegenerate,
            onSelectSource = onSelectSource,
            sourceSelectionEnabled = sourceSelectionEnabled,
            onCopy = onCopy,
            onRegenerate = onRegenerate
        )
    }
}

@Composable
internal fun AnimatedAnswerVersionContent(
    text: String,
    direction: AnswerSwitchDirection,
    content: @Composable (String) -> Unit
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = { answerSwitchAnimationSpec(direction) },
        label = "answer-version-switch"
    ) { content(it) }
}

@Composable
private fun VersionSwitcher(
    versionCount: Int,
    currentIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        VersionNavButton(Icons.AutoMirrored.Filled.ArrowBack, onPrevious, currentIndex > 0)
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(versionCount) { VersionDot(selected = it == currentIndex) }
        }
        VersionNavButton(Icons.AutoMirrored.Filled.ArrowForward, onNext, currentIndex < versionCount - 1)
    }
}

@Composable
private fun VersionNavButton(
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier.size(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (enabled) tint else tint.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
private fun VersionDot(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val width by animateDpAsState(
        targetValue = if (selected) 16.dp else 5.dp,
        animationSpec = tween(280, easing = EmphasizedEasing),
        label = "version-dot-width"
    )
    val color by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.38f),
        animationSpec = tween(280, easing = EmphasizedEasing),
        label = "version-dot-color"
    )
    Box(
        modifier = Modifier
            .size(width = width, height = 5.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
}

private fun answerSwitchAnimationSpec(direction: AnswerSwitchDirection): ContentTransform {
    val enterFade = fadeIn(tween(300, delayMillis = 40, easing = EmphasizedDecelerate))
    val exitFade = fadeOut(tween(160, easing = EmphasizedAccelerate))
    return when (direction) {
        AnswerSwitchDirection.PREVIOUS ->
            (slideInHorizontally(tween(360, easing = EmphasizedDecelerate)) { it / 5 } + enterFade)
                .togetherWith(slideOutHorizontally(tween(200, easing = EmphasizedAccelerate)) { -it / 5 } + exitFade)
        AnswerSwitchDirection.NEXT ->
            (slideInHorizontally(tween(360, easing = EmphasizedDecelerate)) { -it / 5 } + enterFade)
                .togetherWith(slideOutHorizontally(tween(200, easing = EmphasizedAccelerate)) { it / 5 } + exitFade)
        AnswerSwitchDirection.NONE -> fadeIn(tween(220, easing = EmphasizedDecelerate))
            .togetherWith(fadeOut(tween(140, easing = EmphasizedAccelerate)))
    }
}

private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
