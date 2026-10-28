package `fun`.kirari.hanako.feature.settings.presentation

import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.SearchProviderKind
import `fun`.kirari.hanako.core.network.search.TavilyUsageApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class WebSearchQuotaStatus {
    IDLE, LOADING, SUCCESS, FAILED
}

data class WebSearchQuotaState(
    val status: WebSearchQuotaStatus = WebSearchQuotaStatus.IDLE,
    val summary: String = "",
    val detail: String = "",
    val errorMessage: String = ""
)

internal class WebSearchQuotaController(
    private val scope: CoroutineScope,
    private val settings: StateFlow<AppSettings>,
    private val tavilyUsageApi: TavilyUsageApi
) {
    private val _state = MutableStateFlow(WebSearchQuotaState())
    val state: StateFlow<WebSearchQuotaState> = _state.asStateFlow()
    private var job: Job? = null

    fun query() {
        val provider = settings.value.webSearch.provider
        if (provider.kind != SearchProviderKind.TAVILY) {
            _state.value = WebSearchQuotaState(
                status = WebSearchQuotaStatus.FAILED,
                errorMessage = "当前搜索引擎不支持余额查询"
            )
            return
        }
        if (provider.apiKey.isBlank()) {
            _state.value = WebSearchQuotaState(
                status = WebSearchQuotaStatus.FAILED,
                errorMessage = "请先填写 Tavily API Key"
            )
            return
        }
        job?.cancel()
        _state.value = WebSearchQuotaState(status = WebSearchQuotaStatus.LOADING)
        job = scope.launch {
            val trustAll = settings.value.trustAllHttpsCertificates
            val result = runCatching {
                tavilyUsageApi.getUsage(
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    trustAllHttps = trustAll
                )
            }
            if (!isActive) return@launch
            _state.value = result.fold(
                onSuccess = { usage ->
                    val remaining = usage.keyRemaining?.toString() ?: "未知"
                    val limit = usage.keyLimit?.toString() ?: "未知"
                    val detail = buildString {
                        append("已用 ${usage.keyUsage ?: "未知"} / $limit")
                        usage.accountPlan?.takeIf { it.isNotBlank() }?.let { plan ->
                            append(" · 套餐 $plan")
                        }
                    }
                    WebSearchQuotaState(
                        status = WebSearchQuotaStatus.SUCCESS,
                        summary = "剩余 $remaining",
                        detail = detail
                    )
                },
                onFailure = { error ->
                    WebSearchQuotaState(
                        status = WebSearchQuotaStatus.FAILED,
                        errorMessage = error.message ?: "余额查询失败"
                    )
                }
            )
        }
    }

    fun reset() {
        job?.cancel()
        job = null
        _state.value = WebSearchQuotaState()
    }
}
