package `fun`.kirari.hanako.feature.settings.presentation

import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.KIRARI_PROVIDER_ID
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.network.ProviderModelsApi
import `fun`.kirari.llm.core.ProviderUsageSummary
import `fun`.kirari.llm.core.RemoteModelOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConnectionTestStatus {
    IDLE, TESTING, SUCCESS, FAILED
}

data class ConnectionTestState(
    val status: ConnectionTestStatus = ConnectionTestStatus.IDLE,
    val latencyMs: Long = 0,
    val errorMessage: String = ""
)

data class ProviderMetaState(
    val loading: Boolean = false,
    val models: List<RemoteModelOption> = emptyList(),
    val usageSummary: ProviderUsageSummary? = null,
    val errorMessage: String? = null
)

internal class ProviderRuntimeController(
    private val scope: CoroutineScope,
    private val settings: StateFlow<AppSettings>,
    private val providerModelsApi: ProviderModelsApi,
    private val refreshKirariSession: () -> Unit
) {
    val connectionTestManager = ConnectionTestManager()
    private val connectionTestJobs = mutableMapOf<String, Job>()
    private val _providerMetaState = MutableStateFlow(ProviderMetaState())
    val providerMetaState: StateFlow<ProviderMetaState> = _providerMetaState.asStateFlow()
    private var providerMetaJob: Job? = null

    fun testProviderConnection(provider: ModelProviderConfig) {
        val providerId = provider.id
        connectionTestJobs[providerId]?.cancel()
        connectionTestManager.setState(providerId, ConnectionTestState(status = ConnectionTestStatus.TESTING))
        connectionTestJobs[providerId] = scope.launch {
            val trustAll = settings.value.trustAllHttpsCertificates
            val result = runCatching {
                providerModelsApi.testConnection(provider, trustAll)
            }
            if (!isActive) return@launch
            connectionTestManager.setState(
                providerId,
                result.fold(
                    onSuccess = { testResult ->
                        if (testResult.success) {
                            ConnectionTestState(
                                status = ConnectionTestStatus.SUCCESS,
                                latencyMs = testResult.latencyMs
                            )
                        } else {
                            ConnectionTestState(
                                status = ConnectionTestStatus.FAILED,
                                latencyMs = testResult.latencyMs,
                                errorMessage = testResult.errorMessage
                            )
                        }
                    },
                    onFailure = { error ->
                        val message = when (error.message) {
                            "请先登录 The Kirari Network" -> "请先登录"
                            else -> error.message ?: "连接测试失败"
                        }
                        ConnectionTestState(
                            status = ConnectionTestStatus.FAILED,
                            errorMessage = message
                        )
                    }
                )
            )
        }
    }

    fun resetConnectionTest(providerId: String) {
        connectionTestJobs[providerId]?.cancel()
        connectionTestJobs.remove(providerId)
        connectionTestManager.reset(providerId)
    }

    fun loadProviderMeta(provider: ModelProviderConfig) {
        providerMetaJob?.cancel()
        val kirariAuth = settings.value.kirari.auth
        if (
            provider.id == KIRARI_PROVIDER_ID &&
            kirariAuth.accessToken.isBlank() &&
            kirariAuth.refreshToken.isBlank()
        ) {
            _providerMetaState.value = ProviderMetaState()
            return
        }
        _providerMetaState.value = ProviderMetaState(loading = true)
        providerMetaJob = scope.launch {
            val trustAll = settings.value.trustAllHttpsCertificates
            if (provider.id == KIRARI_PROVIDER_ID) {
                refreshKirariSession()
            }
            val result = runCatching {
                providerModelsApi.getCatalog(provider, trustAll)
            }
            if (!isActive) return@launch
            _providerMetaState.value = result.fold(
                onSuccess = { catalog ->
                    ProviderMetaState(
                        loading = false,
                        models = catalog.models,
                        usageSummary = catalog.usageSummary
                    )
                },
                onFailure = { error ->
                    if (provider.id == KIRARI_PROVIDER_ID) {
                        refreshKirariSession()
                    }
                    ProviderMetaState(
                        loading = false,
                        errorMessage = error.message ?: "加载提供方信息失败"
                    )
                }
            )
        }
    }

    fun resetProviderMeta() {
        providerMetaJob?.cancel()
        providerMetaJob = null
        _providerMetaState.value = ProviderMetaState()
    }

    fun clearProviderMeta() {
        providerMetaJob?.cancel()
        _providerMetaState.value = ProviderMetaState()
    }
}
