package `fun`.kirari.hanako.feature.settings.ui.automation

import `fun`.kirari.hanako.feature.settings.ui.settings.SwitchSettingRow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import `fun`.kirari.hanako.R
import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.BubbleAppearanceSettings
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.data.WebSearchSettings
import `fun`.kirari.hanako.feature.overlay.ui.BubbleMenu
import kotlinx.coroutines.delay

private val PreviewHeight = 238.dp
private val PreviewPadding = 14.dp
private val PreviewBubbleAnchorXFraction = 0.17f
private val PreviewBubbleAnchorYFraction = 0.8f
private val PreviewTapDotOffsetXRatio = 0.0f
private val PreviewTapDotOffsetYRatio = 0.0f
private val PreviewTapDotSizeRatio = 0.6f
private const val PreviewLabelStartProgress = 0.9f
private const val PreviewFirstTapDelayMs = 100L
private const val PreviewDoubleTapIntervalMs = 0L
private const val PreviewOpenAfterSecondTapDelayMs = 20L
private const val PreviewExpandedHoldMs = 2650L
private const val PreviewCloseAfterSingleTapDelayMs = 30L
private const val PreviewCloseAnimationMs = 850L
private const val PreviewCycleGapMs = 0L
private const val PreviewTapFadeInMs = 20
private const val PreviewTapFadeOutMs = 190

@Composable
internal fun BubbleMenuSettingsCard(
    appearanceSettings: BubbleAppearanceSettings,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BubbleMenuPreview(settings = appearanceSettings)
        SwitchSettingRow(
            title = "启用扇形菜单",
            subtitle = "普通模式双击悬浮球时展开快捷菜单。",
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
    }
}

@Composable
private fun BubbleMenuPreview(settings: BubbleAppearanceSettings) {
    val density = LocalDensity.current
    var cycleKey by remember { mutableIntStateOf(0) }
    var closeSignal by remember { mutableIntStateOf(0) }
    var menuVisible by remember { mutableStateOf(true) }
    val tapPulse = remember { Animatable(0f) }
    val bubbleAlpha = (settings.overallOpacity / 100f).coerceIn(0f, 1f)
    val bubbleDiameter = settings.bubbleDiameterDp.dp.coerceAtLeast(28.dp)

    LaunchedEffect(Unit) {
        while (true) {
            closeSignal = 0
            cycleKey += 1
            tapPulse.snapTo(0f)
            menuVisible = false
            delay(PreviewFirstTapDelayMs)
            tapPulse.pulse()
            delay(PreviewDoubleTapIntervalMs)
            tapPulse.pulse()
            delay(PreviewOpenAfterSecondTapDelayMs)
            menuVisible = true
            delay(PreviewExpandedHoldMs)
            tapPulse.pulse()
            delay(PreviewCloseAfterSingleTapDelayMs)
            closeSignal += 1
            delay(PreviewCloseAnimationMs)
            menuVisible = false
            delay(PreviewCycleGapMs)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(PreviewHeight)
                .padding(PreviewPadding)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(14.dp)
                )
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.TopStart
        ) {
            val viewportWidth = with(density) { maxWidth.toPx() }
            val viewportHeight = with(density) { maxHeight.toPx() }
            val anchorX = (viewportWidth * PreviewBubbleAnchorXFraction).roundToInt()
            val anchorY = (viewportHeight * PreviewBubbleAnchorYFraction).roundToInt()
            val anchorXDp = with(density) { anchorX.toDp() }
            val anchorYDp = with(density) { anchorY.toDp() }
            if (menuVisible) {
                key(cycleKey) {
                    BubbleMenu(
                        anchorX = anchorX,
                        anchorY = anchorY,
                        settings = AppSettings(
                            processingRoute = ProcessingRoute.MULTIMODAL_DIRECT,
                            webSearch = WebSearchSettings(enabled = true)
                        ),
                        onItemClick = {},
                        onDismiss = {},
                        modifier = Modifier.matchParentSize(),
                        viewportWidthPx = viewportWidth,
                        viewportHeightPx = viewportHeight,
                        closeSignal = closeSignal,
                        showPreviewLabels = true,
                        previewLabelStartProgress = PreviewLabelStartProgress
                    )
                }
            }
            PreviewBubble(
                diameter = bubbleDiameter,
                alpha = bubbleAlpha,
                anchorX = anchorXDp,
                anchorY = anchorYDp,
                tapPulse = tapPulse.value
            )
        }
    }
}

private suspend fun Animatable<Float, androidx.compose.animation.core.AnimationVector1D>.pulse() {
    animateTo(1f, tween(durationMillis = PreviewTapFadeInMs))
    animateTo(0f, tween(durationMillis = PreviewTapFadeOutMs))
}

@Composable
private fun PreviewBubble(
    diameter: androidx.compose.ui.unit.Dp,
    alpha: Float,
    anchorX: androidx.compose.ui.unit.Dp,
    anchorY: androidx.compose.ui.unit.Dp,
    tapPulse: Float
) {
    Surface(
        modifier = Modifier
            .offset(
                x = anchorX - diameter / 2,
                y = anchorY - diameter / 2
            )
            .size(diameter)
            .alpha(alpha),
        shape = CircleShape,
        color = Color(0xFFD0BCFF)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_bubble_crop),
                contentDescription = null,
                tint = Color(0xFF381E72),
                modifier = Modifier.size(diameter * 0.45f)
            )
            Box(
                modifier = Modifier
                    .offset(
                        x = diameter * PreviewTapDotOffsetXRatio,
                        y = diameter * PreviewTapDotOffsetYRatio
                    )
                    .size(diameter * PreviewTapDotSizeRatio)
                    .alpha(tapPulse * 0.82f)
                    .background(Color.White, CircleShape)
            )
        }
    }
}
