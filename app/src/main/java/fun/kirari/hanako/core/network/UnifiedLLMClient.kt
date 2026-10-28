package `fun`.kirari.hanako.core.network

import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.data.SettingsStore
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.llm.core.ChatMessage
import `fun`.kirari.llm.core.LlmClient
import `fun`.kirari.llm.core.LlmEvent
import `fun`.kirari.llm.core.StreamRequest
import `fun`.kirari.llm.core.ToolDef
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

internal class UnifiedLLMClient(
    private val clientProvider: NetworkClientProvider = NetworkClientProvider(),
    private val kirariAuthManager: KirariAuthManager? = null,
    private val settingsStore: SettingsStore? = null,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val tag = "HanakoUnifiedLLM"
    private val coreClient = LlmClient(clientProvider, HanakoLlmLogger)
    private val providerResolver = ProviderConfigResolver(kirariAuthManager, settingsStore)

    suspend fun stream(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imagesBase64: List<String> = emptyList(),
        tools: List<ToolDef>? = null,
        firstDeltaTimeoutMillis: Long,
        trustAllHttpsCertificates: Boolean = false
    ): Flow<LlmEvent> {
        AppDebugLogStore.i(tag, "stream provider=${provider.kind} model=$model imageCount=${imagesBase64.size} hasTools=${tools != null} trustAllHttps=$trustAllHttpsCertificates")
        val resolvedProvider = providerResolver.resolve(provider, trustAllHttpsCertificates)

        return coreClient.stream(
            StreamRequest(
                provider = resolvedProvider,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imagesBase64 = imagesBase64,
                tools = tools,
                firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
                trustAllHttpsCertificates = trustAllHttpsCertificates
            )
        )
    }

    suspend fun streamMessages(
        provider: ModelProviderConfig,
        model: String,
        messages: List<ChatMessage>,
        tools: List<ToolDef>? = null,
        firstDeltaTimeoutMillis: Long,
        trustAllHttpsCertificates: Boolean = false
    ): Flow<LlmEvent> {
        AppDebugLogStore.i(tag, "streamMessages provider=${provider.kind} model=$model messageCount=${messages.size} hasTools=${tools != null} trustAllHttps=$trustAllHttpsCertificates")
        val resolvedProvider = providerResolver.resolve(provider, trustAllHttpsCertificates)
        return coreClient.stream(
            StreamRequest(
                provider = resolvedProvider,
                model = model,
                messages = messages,
                tools = tools,
                firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
                trustAllHttpsCertificates = trustAllHttpsCertificates
            )
        )
    }

}
