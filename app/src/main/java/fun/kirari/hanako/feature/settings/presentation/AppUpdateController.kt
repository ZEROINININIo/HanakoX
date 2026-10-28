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
    val dialogVisible: Boolean = false,
    val checking: Boolean = false,
    val statusMessage: String? = null
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
                _state.update { it.copy(availableUpdate = update, dialogVisible = true) }
            }
        }
    }

    fun checkManually() {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, statusMessage = null) }
        scope.launch {
            val update = appUpdateApi.checkForUpdate()
            _state.update {
                if (update != null) {
                    it.copy(
                        availableUpdate = update,
                        dialogVisible = true,
                        checking = false,
                        statusMessage = "发现新版本 ${update.version}"
                    )
                } else {
                    it.copy(
                        checking = false,
                        statusMessage = "暂未发现新版本；如果网络无法访问 GitHub，也可能无法完成检查。"
                    )
                }
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
