package `fun`.kirari.hanako.feature.settings.presentation

import `fun`.kirari.hanako.feature.settings.presentation.ProviderMetaState

import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.AssistantPreset
import `fun`.kirari.hanako.core.data.AutomationSettings
import `fun`.kirari.hanako.core.data.KIRARI_PROVIDER_ID
import `fun`.kirari.hanako.core.data.KirariModelTag
import `fun`.kirari.hanako.core.data.KirariSettings
import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.data.ModelSelection
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.data.ScreenCaptureMethod
import `fun`.kirari.hanako.core.data.SettingsRepository
import `fun`.kirari.hanako.core.data.WebSearchSettings
import `fun`.kirari.hanako.core.data.availableProviders
import `fun`.kirari.hanako.core.data.creatableProviderKinds
import `fun`.kirari.hanako.core.data.defaultAssistant
import `fun`.kirari.hanako.core.data.defaultProvider
import `fun`.kirari.hanako.core.data.isProtectedKirariProvider
import `fun`.kirari.hanako.core.data.modelSelectionFor
import `fun`.kirari.hanako.core.data.toKirariModelTag
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.llm.core.ProviderKind
import `fun`.kirari.llm.core.RemoteModelOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

internal class SettingsEditorController(
    private val scope: CoroutineScope,
    private val repository: SettingsRepository,
    private val providerMetaState: StateFlow<ProviderMetaState>
) {
    private val tag = "HanakoSettingsEditor"

    fun updateProvider(provider: ModelProviderConfig) {
        if (isProtectedKirariProvider(provider.id)) return
        val sanitizedProvider = if (provider.kind in creatableProviderKinds()) {
            provider
        } else {
            provider.copy(
                kind = ProviderKind.OPENAI_COMPATIBLE,
                baseUrl = "https://api.openai.com/v1"
            )
        }
        scope.launch {
            repository.update { current ->
                current.copy(
                    providers = current.providers.map { if (it.id == provider.id) sanitizedProvider else it }
                )
            }
        }
    }

    fun addProvider() {
        scope.launch {
            repository.update { current ->
                val provider = ModelProviderConfig(name = "自定义提供方 ${current.providers.size + 1}")
                current.copy(
                    providers = current.providers + provider,
                    selectedProviderId = provider.id
                )
            }
        }
    }

    fun selectProvider(providerId: String) {
        scope.launch {
            repository.update { it.copy(selectedProviderId = providerId) }
        }
    }

    fun deleteProvider(providerId: String) {
        if (isProtectedKirariProvider(providerId)) return
        scope.launch {
            repository.update { current ->
                val remaining = current.providers.filterNot { it.id == providerId }
                val providers = if (remaining.isEmpty()) listOf(defaultProvider()) else remaining
                val selectedProviderId = providers.firstOrNull()?.id
                val fallbackProvider = current.copy(providers = providers).availableProviders().firstOrNull()
                current.copy(
                    providers = providers,
                    selectedProviderId = selectedProviderId,
                    textModelSelection = remapSelection(
                        current.modelSelectionFor(ModelPurpose.TEXT),
                        providers,
                        fallbackProvider
                    ),
                    visionModelSelection = remapSelection(
                        current.modelSelectionFor(ModelPurpose.VISION),
                        providers,
                        fallbackProvider
                    ),
                    ocrModelSelection = remapSelection(
                        current.modelSelectionFor(ModelPurpose.OCR),
                        providers,
                        fallbackProvider
                    )
                )
            }
        }
    }

    fun updateAssistant(assistant: AssistantPreset) {
        scope.launch {
            repository.update { current ->
                current.copy(
                    assistants = current.assistants.map { if (it.id == assistant.id) assistant else it }
                )
            }
        }
    }

    fun addAssistant() {
        scope.launch {
            repository.update { current ->
                val assistant = AssistantPreset(
                    id = UUID.randomUUID().toString(),
                    name = "自定义助手 ${current.assistants.size + 1}",
                    ocrPrompt = "请准确提取图片中的全部文字，按原有结构输出，不要解释。",
                    textPrompt = "你是一个乐于助人的中文助手。",
                    visionPrompt = "你是一个乐于助人的中文助手。请直接根据图片内容完成用户任务。"
                )
                current.copy(
                    assistants = current.assistants + assistant,
                    selectedAssistantId = assistant.id
                )
            }
        }
    }

    fun selectAssistant(assistantId: String) = repository.selectAssistant(scope, assistantId)

    fun deleteAssistant(assistantId: String) {
        scope.launch {
            repository.update { current ->
                val remaining = current.assistants.filterNot { it.id == assistantId }
                val assistants = if (remaining.isEmpty()) listOf(defaultAssistant()) else remaining
                val selectedAssistantId = assistants.firstOrNull()?.id
                current.copy(
                    assistants = assistants,
                    selectedAssistantId = selectedAssistantId
                )
            }
        }
    }

    fun setRoute(route: ProcessingRoute) {
        scope.launch {
            repository.update { it.copy(processingRoute = route) }
        }
    }

    fun setScreenCaptureMethod(method: ScreenCaptureMethod) {
        scope.launch {
            repository.update { it.copy(screenCaptureMethod = method) }
        }
    }

    fun updateModelSelection(purpose: ModelPurpose, selection: ModelSelection) =
        repository.updateModelSelection(scope, purpose, selection)

    fun updateModelSelectionWithFavorite(
        purpose: ModelPurpose,
        selection: ModelSelection,
        favoriteModel: Boolean = false
    ) = repository.updateModelSelectionWithFavorite(scope, purpose, selection, favoriteModel)

    fun toggleFavoriteModel(providerId: String, modelId: String) =
        repository.toggleFavoriteModel(scope, providerId, modelId)

    fun addFavoriteModel(providerId: String, modelId: String) =
        repository.addFavoriteModel(scope, providerId, modelId)

    fun removeFavoriteModel(providerId: String, modelId: String) =
        repository.removeFavoriteModel(scope, providerId, modelId)

    fun updateAutomationSettings(transform: (AutomationSettings) -> AutomationSettings) {
        scope.launch {
            repository.update { current ->
                val next = transform(current.automation)
                current.copy(
                    automation = next.copy(autoModeTimeoutSeconds = next.autoModeTimeoutSeconds.coerceAtLeast(1))
                )
            }
        }
    }

    fun setTrustAllHttpsCertificates(enabled: Boolean) {
        scope.launch {
            repository.update { it.copy(trustAllHttpsCertificates = enabled) }
        }
    }

    fun updateKirariSettings(transform: (KirariSettings) -> KirariSettings) {
        scope.launch {
            repository.update { current ->
                val updatedSettings = current.copy(kirari = transform(current.kirari))
                AppDebugLogStore.i(tag, "updateKirariSettings serverUrl=${updatedSettings.kirari.serverUrl}")
                updatedSettings
            }
        }
    }

    fun updateWebSearchSettings(transform: (WebSearchSettings) -> WebSearchSettings) {
        scope.launch {
            repository.update { current ->
                current.copy(webSearch = transform(current.webSearch))
            }
        }
    }

    fun shouldSuggestKirariAutoSetup(settings: AppSettings, providerMetaState: ProviderMetaState): Boolean {
        val expected = expectedKirariSelections(providerMetaState.models)
        if (expected.size != 3) {
            return false
        }
        return expected.any { (purpose, selection) -> settings.modelSelectionFor(purpose) != selection }
    }

    fun applyKirariAutoSetup() {
        val expected = expectedKirariSelections(providerMetaState.value.models)
        if (expected.size != 3) {
            return
        }
        scope.launch {
            repository.update { current ->
                current.copy(
                    textModelSelection = expected[ModelPurpose.TEXT] ?: current.textModelSelection,
                    ocrModelSelection = expected[ModelPurpose.OCR] ?: current.ocrModelSelection,
                    visionModelSelection = expected[ModelPurpose.VISION] ?: current.visionModelSelection
                )
            }
        }
    }

    private fun remapSelection(
        selection: ModelSelection,
        providers: List<ModelProviderConfig>,
        fallbackProvider: ModelProviderConfig?
    ): ModelSelection {
        val providerExists = providers.any { it.id == selection.providerId }
        return when {
            providerExists -> selection
            fallbackProvider != null -> selection.copy(providerId = fallbackProvider.id)
            else -> selection.copy(providerId = null, model = "")
        }
    }

    private fun expectedKirariSelections(models: List<RemoteModelOption>): Map<ModelPurpose, ModelSelection> {
        val byTag = models.mapNotNull { option ->
            option.tag?.toKirariModelTag()?.let { tag -> tag to option }
        }.toMap()
        val expected = linkedMapOf<ModelPurpose, ModelSelection>()
        byTag[KirariModelTag.TEXT]?.let { expected[ModelPurpose.TEXT] = ModelSelection(KIRARI_PROVIDER_ID, it.id) }
        byTag[KirariModelTag.OCR]?.let { expected[ModelPurpose.OCR] = ModelSelection(KIRARI_PROVIDER_ID, it.id) }
        byTag[KirariModelTag.MULTIMODAL]?.let { expected[ModelPurpose.VISION] = ModelSelection(KIRARI_PROVIDER_ID, it.id) }
        if (expected.size != 3) {
            return emptyMap()
        }
        return expected
    }
}
