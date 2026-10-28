package `fun`.kirari.hanako.feature.settings.presentation

import `fun`.kirari.hanako.core.network.update.AppUpdateApi
import `fun`.kirari.hanako.core.network.update.AppUpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUpdateUiState(
    val availableUpdate: AppUpdateInfo? = null,
    val dialogVisible: Boolean = false
)

internal class AppUpdateController(
    private val scope: CoroutineScope,
    private val appUpdateApi: AppUpdateApi
) {
    private val _state = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()
    private var checkedThisProcess = false

    fun checkOnceSilently() {
        if (checkedThisProcess) return
        checkedThisProcess = true
        scope.launch {
            val update = appUpdateApi.checkForUpdate()
            if (update != null) {
                _state.update { it.copy(availableUpdate = update) }
            }
        }
    }

    fun showDialog() {
        _state.update {
            if (it.availableUpdate == null) it else it.copy(dialogVisible = true)
        }
    }

    fun dismissDialog() {
        _state.update { it.copy(dialogVisible = false) }
    }
}
