package `fun`.kirari.hanako.feature.settings.presentation

import android.net.Uri
import `fun`.kirari.hanako.core.data.KIRARI_PROVIDER_ID
import `fun`.kirari.hanako.core.data.KirariSettings
import `fun`.kirari.hanako.core.data.SettingsStore
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.core.network.KirariAuthHandleResult
import `fun`.kirari.hanako.core.network.KirariAuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class KirariAccountState(
    val displayName: String? = null,
    val email: String? = null,
    val subject: String? = null,
    val loggedIn: Boolean = false,
    val canRefresh: Boolean = false,
    val expiresAtMillis: Long = 0L,
    val loading: Boolean = false,
    val errorMessage: String? = null
)

internal class KirariAuthController(
    private val scope: CoroutineScope,
    private val settingsStore: SettingsStore,
    private val kirariAuthManager: KirariAuthManager,
    private val settingsProvider: () -> `fun`.kirari.hanako.core.data.AppSettings,
    private val clearProviderMeta: () -> Unit
) {
    private val tag = "HanakoKirariAuth"
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _accountState = MutableStateFlow(KirariAccountState())
    val accountState: StateFlow<KirariAccountState> = _accountState.asStateFlow()
    private val _authenticatedProviderId = MutableStateFlow<String?>(null)
    val authenticatedProviderId: StateFlow<String?> = _authenticatedProviderId.asStateFlow()
    private var accountJob: Job? = null

    fun startLogin(onReady: (String) -> Unit) {
        scope.launch {
            runCatching {
                val latestSettings = settingsStore.read()
                AppDebugLogStore.i(tag, "startKirariLogin serverUrl=${latestSettings.kirari.serverUrl}")
                kirariAuthManager.buildAuthorizationRequest(
                    serverUrl = latestSettings.kirari.serverUrl,
                    trustAllHttpsCertificates = latestSettings.trustAllHttpsCertificates
                )
            }.onSuccess { request ->
                onReady(request.authorizationUrl)
            }.onFailure { error ->
                _message.value = error.message ?: "Kirari 登录准备失败"
                updateAccountState()
            }
        }
    }

    fun handleRedirect(uri: Uri) {
        if (!kirariAuthManager.matchesRedirect(uri)) return
        scope.launch {
            if (!kirariAuthManager.hasPendingAuthorizationSession()) {
                AppDebugLogStore.i(tag, "ignoreKirariRedirect reason=no_pending_session")
                return@launch
            }
            val result = runCatching {
                val latestSettings = settingsStore.read()
                AppDebugLogStore.i(tag, "handleKirariRedirect serverUrl=${latestSettings.kirari.serverUrl}")
                kirariAuthManager.handleRedirect(
                    redirectUri = uri,
                    settings = latestSettings,
                    trustAllHttpsCertificates = latestSettings.trustAllHttpsCertificates
                )
            }.getOrElse { error ->
                KirariAuthHandleResult(
                    success = false,
                    message = error.message ?: "Kirari 登录失败"
                )
            }
            _message.value = result.message
            if (result.success) {
                syncSessionStatus(force = true)
                _authenticatedProviderId.value = KIRARI_PROVIDER_ID
            } else {
                updateAccountState()
            }
        }
    }

    fun consumeAuthenticatedProvider() {
        _authenticatedProviderId.value = null
    }

    fun logout() {
        scope.launch {
            kirariAuthManager.clearAuth()
            clearProviderMeta()
            updateAccountState()
            _message.value = "已退出 The Kirari Network"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun syncSessionStatus(force: Boolean = false) {
        accountJob?.cancel()
        val current = settingsProvider().kirari
        if (!force && current.auth.accessToken.isBlank() && current.auth.refreshToken.isBlank()) {
            updateAccountState()
            return
        }
        _accountState.value = current.toKirariAccountState(
            loading = true,
            errorMessage = null
        )
        accountJob = scope.launch {
            val settings = settingsProvider()
            val result = kirariAuthManager.refreshSessionStatus(
                settings = settings,
                trustAllHttpsCertificates = settings.trustAllHttpsCertificates
            )
            if (!isActive) return@launch
            if (result.authenticated) {
                updateAccountState(errorMessage = null)
            } else {
                _accountState.value = settingsProvider().kirari.toKirariAccountState(
                    loading = false,
                    errorMessage = result.errorMessage
                )
            }
        }
    }

    private fun updateAccountState(
        loading: Boolean = false,
        errorMessage: String? = null
    ) {
        _accountState.value = settingsProvider().kirari.toKirariAccountState(
            loading = loading,
            errorMessage = errorMessage
        )
    }
}

private fun KirariSettings.toKirariAccountState(
    loading: Boolean = false,
    errorMessage: String? = null
): KirariAccountState {
    val auth = auth
    val profile = profile
    val now = System.currentTimeMillis()
    return KirariAccountState(
        displayName = profile.name.ifBlank {
            profile.preferredUsername.ifBlank {
                profile.nickname.ifBlank { null }
            }
        },
        email = profile.email.ifBlank { null },
        subject = profile.subject.ifBlank { null },
        loggedIn = auth.accessToken.isNotBlank() && auth.accessTokenExpiresAtMillis > now,
        canRefresh = auth.refreshToken.isNotBlank(),
        expiresAtMillis = auth.accessTokenExpiresAtMillis,
        loading = loading,
        errorMessage = errorMessage
    )
}
