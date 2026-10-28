package `fun`.kirari.hanako.feature.home.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Stable
internal class ScrollToTopController {
    private val handlers = mutableStateMapOf<String, () -> Unit>()

    fun register(route: String, handler: () -> Unit) {
        handlers[route] = handler
    }

    fun unregister(route: String, handler: () -> Unit) {
        if (handlers[route] === handler) {
            handlers.remove(route)
        }
    }

    fun canScrollToTop(route: String?): Boolean {
        return route != null && handlers.containsKey(route)
    }

    fun scrollToTop(route: String?) {
        if (route == null) return
        handlers[route]?.invoke()
    }
}

internal val LocalScrollToTopController = compositionLocalOf<ScrollToTopController?> { null }

@Composable
internal fun rememberScrollToTopController(): ScrollToTopController {
    return remember { ScrollToTopController() }
}

@Composable
internal fun RegisterScrollToTopHandler(
    route: String,
    onScrollToTop: () -> Unit
) {
    val controller = LocalScrollToTopController.current
    val currentHandler = rememberUpdatedState(onScrollToTop)

    DisposableEffect(controller, route) {
        if (controller == null) {
            onDispose { }
        } else {
            val handler = { currentHandler.value() }
            controller.register(route, handler)
            onDispose {
                controller.unregister(route, handler)
            }
        }
    }
}
