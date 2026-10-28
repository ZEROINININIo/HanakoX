package `fun`.kirari.hanako.platform.capture.shizuku

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal object ShizukuAuthorizationManager {
    private val _authorized = MutableStateFlow(false)
    val authorized: StateFlow<Boolean> = _authorized

    fun setAuthorized(authorized: Boolean) {
        _authorized.value = authorized
    }
}
