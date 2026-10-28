package `fun`.kirari.hanako.core.network

import `fun`.kirari.hanako.core.data.KIRARI_PROVIDER_ID
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.data.SettingsStore
import `fun`.kirari.llm.core.ProviderConfig
import `fun`.kirari.llm.core.ProviderKind

/** Owns the provider -> transport configuration projection for every network API. */
internal class ProviderConfigResolver(
    private val kirariAuthManager: KirariAuthManager? = null,
    private val settingsStore: SettingsStore? = null
) {
    suspend fun resolve(
        provider: ModelProviderConfig,
        trustAllHttpsCertificates: Boolean
    ): ProviderConfig {
        if (provider.kind != ProviderKind.KIRARI_NETWORK) {
            return ProviderConfig(kind = provider.kind, baseUrl = provider.baseUrl, apiKey = provider.apiKey)
        }
        val manager = requireNotNull(kirariAuthManager) { "KirariAuthManager is required for Kirari provider" }
        val store = requireNotNull(settingsStore) { "SettingsStore is required for Kirari provider" }
        val settings = store.read()
        val accessToken = manager.ensureValidAccessToken(settings, trustAllHttpsCertificates)
        require(accessToken.isNotBlank()) { "请先登录 The Kirari Network" }
        val baseUrl = settings.kirari.serverUrl.trim().ifBlank {
            settings.providers.firstOrNull { it.id == KIRARI_PROVIDER_ID }?.baseUrl?.trim().orEmpty()
        }
        return ProviderConfig(
            kind = ProviderKind.KIRARI_NETWORK,
            baseUrl = baseUrl.trimEnd('/') + "/api/llm",
            apiKey = accessToken,
            headers = mapOf("Accept" to "application/json, text/event-stream")
        )
    }
}
