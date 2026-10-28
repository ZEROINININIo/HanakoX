package `fun`.kirari.hanako.core.network.search

import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.core.network.NetworkClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.net.SocketTimeoutException

internal data class TavilyUsageSummary(
    val keyUsage: Int? = null,
    val keyLimit: Int? = null,
    val accountPlan: String? = null,
    val accountUsage: Int? = null,
    val accountLimit: Int? = null
) {
    val keyRemaining: Int?
        get() = if (keyLimit != null && keyUsage != null) {
            (keyLimit - keyUsage).coerceAtLeast(0)
        } else {
            null
        }
}

internal class TavilyUsageApi(
    private val clientProvider: NetworkClientProvider,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val tag = "HanakoTavilyUsage"

    suspend fun getUsage(
        baseUrl: String,
        apiKey: String,
        trustAllHttps: Boolean
    ): TavilyUsageSummary = try {
        withContext(Dispatchers.IO) {
            val usageUrl = baseUrl.toTavilyUsageUrl()
            val request = Request.Builder()
                .url(usageUrl)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            clientProvider.clientWithTimeout(trustAllHttps, TIMEOUT_MILLIS)
                .newCall(request)
                .execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        val message = errorCodeToMessage(response.code)
                        AppDebugLogStore.e(tag, "usage failed: ${response.code} $message")
                        error(message)
                    }
                    val body = response.body?.string().orEmpty()
                    parseUsage(body)
                }
        }
    } catch (_: SocketTimeoutException) {
        error("查询超时，请检查代理或网络连接")
    }

    private fun parseUsage(body: String): TavilyUsageSummary {
        if (body.isBlank()) return TavilyUsageSummary()
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val key = root["key"]?.jsonObject
            val account = root["account"]?.jsonObject
            TavilyUsageSummary(
                keyUsage = key?.get("usage")?.jsonPrimitive?.content?.toIntOrNull(),
                keyLimit = key?.get("limit")?.jsonPrimitive?.content?.toIntOrNull(),
                accountPlan = account?.get("current_plan")?.jsonPrimitive?.content,
                accountUsage = account?.get("plan_usage")?.jsonPrimitive?.content?.toIntOrNull(),
                accountLimit = account?.get("plan_limit")?.jsonPrimitive?.content?.toIntOrNull()
            )
        } catch (e: Exception) {
            AppDebugLogStore.e(tag, "parse usage failed: ${e.message}")
            error("额度响应解析失败")
        }
    }

    private fun String.toTavilyUsageUrl(): String {
        val parsed = trim().toHttpUrlOrNull()
            ?: error("API URL 无效")
        return parsed.newBuilder()
            .encodedPath("/usage")
            .query(null)
            .build()
            .toString()
    }

    private fun errorCodeToMessage(code: Int): String = when (code) {
        401, 403 -> "API Key 无效或权限不足"
        429 -> "请求频率超限，请稍后再试"
        in 500..599 -> "Tavily 服务暂时不可用"
        else -> "余额查询失败 (HTTP $code)"
    }

    private companion object {
        private const val TIMEOUT_MILLIS = 20_000L
    }
}
