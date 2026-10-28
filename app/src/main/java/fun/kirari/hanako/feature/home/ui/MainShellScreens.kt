package `fun`.kirari.hanako.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val title: String, val icon: ImageVector) {
    Hanako("Hanako X", Icons.Default.Home),
    Settings("设置", Icons.Default.Settings)
}

@Composable
internal fun MainShellScreen(
    currentScreen: Screen,
    onScreenChange: (Screen) -> Unit,
    hanakoContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = currentScreen.ordinal,
        pageCount = { Screen.entries.size }
    )
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(currentScreen) {
        val target = currentScreen.ordinal
        if (target != pagerState.currentPage) {
            isAnimating = true
            try {
                pagerState.animateScrollToPage(target)
            } finally {
                isAnimating = false
            }
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (isAnimating) return@LaunchedEffect
        val settledScreen = Screen.entries[pagerState.settledPage]
        if (currentScreen != settledScreen) {
            onScreenChange(settledScreen)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = true
    ) { page ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (Screen.entries[page]) {
                Screen.Hanako -> {
                    HanakoIllustratedBackdrop()
                    hanakoContent()
                }
                Screen.Settings -> {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
                    settingsContent()
                }
            }
        }
    }
}

@Composable
private fun HanakoIllustratedBackdrop() {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(`fun`.kirari.hanako.R.drawable.bg_hanako_wallpaper),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterStart
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0x44FFF6FA),
                            0.42f to Color(0x1AFFF7FA),
                            1f to Color(0xBFFFF7FA)
                        )
                    )
                )
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(240.dp)
                .blur(34.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent)))
        )
    }
}
