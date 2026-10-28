package `fun`.kirari.hanako.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when (Screen.entries[page]) {
                Screen.Hanako -> hanakoContent()
                Screen.Settings -> settingsContent()
            }
        }
    }
}
