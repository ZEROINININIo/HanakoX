package `fun`.kirari.hanako.core.network

import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.data.SettingsStore
import `fun`.kirari.llm.core.ConnectionTestResult
import `fun`.kirari.llm.core.ProviderCatalog
import `fun`.kirari.llm.core.RemoteModelOption

internal class ProviderModelsApi(
    private val clientProvider: NetworkClientProvider = NetworkClientProvider(),
    private val kirariAuthManager: KirariAuthManager? = null,
    private val settingsStore: SettingsStore? = null
) {
    private val coreApi = `fun`.kirari.llm.core.ProviderModelsApi(clientProvider)
    private val providerResolver = ProviderConfigResolver(kirariAuthManager, settingsStore)

    suspend fun getCatalog(
        provider: ModelProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): ProviderCatalog = coreApi.getCatalog(
        provider = providerResolver.resolve(provider, trustAllHttpsCertificates),
        trustAllHttpsCertificates = trustAllHttpsCertificates
    )

    suspend fun listModels(
        provider: ModelProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): List<RemoteModelOption> = coreApi.listModels(
        provider = providerResolver.resolve(provider, trustAllHttpsCertificates),
        trustAllHttpsCertificates = trustAllHttpsCertificates
    )

    suspend fun testConnection(
        provider: ModelProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): ConnectionTestResult = coreApi.testConnection(
        provider = providerResolver.resolve(provider, trustAllHttpsCertificates),
        trustAllHttpsCertificates = trustAllHttpsCertificates
    )

}
