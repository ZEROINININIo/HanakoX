package `fun`.kirari.hanako.app.navigation

import `fun`.kirari.hanako.feature.settings.presentation.ConnectionTestManager
import `fun`.kirari.hanako.feature.settings.presentation.KirariAccountState
import `fun`.kirari.hanako.feature.settings.presentation.KirariAuthController
import `fun`.kirari.hanako.feature.settings.presentation.ProviderMetaState
import `fun`.kirari.hanako.feature.settings.presentation.ProviderRuntimeController
import `fun`.kirari.hanako.feature.settings.presentation.WebSearchQuotaController
import `fun`.kirari.hanako.feature.settings.presentation.WebSearchQuotaState
import `fun`.kirari.hanako.feature.settings.presentation.SettingsEditorController
import `fun`.kirari.hanako.feature.settings.presentation.AppUpdateController
import `fun`.kirari.hanako.feature.settings.presentation.AppUpdateUiState

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `fun`.kirari.hanako.app.HanakoApplication
import `fun`.kirari.hanako.BuildConfig
import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.AnswerStyle
import `fun`.kirari.hanako.core.data.HistoryCommandResult
import `fun`.kirari.hanako.core.data.HistoryMarkerColor
import `fun`.kirari.hanako.core.model.QuotedFragment
import `fun`.kirari.hanako.core.data.QuestionCardArtifact
import `fun`.kirari.hanako.core.data.AssistantPreset
import `fun`.kirari.hanako.core.data.AutomationSettings
import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.data.ModelSelection
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.data.ScreenCaptureMethod
import `fun`.kirari.hanako.core.data.SettingsRepository
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.core.data.KirariSettings
import `fun`.kirari.hanako.core.data.WebSearchSettings
import `fun`.kirari.hanako.platform.capture.ocr.LocalOcrManager
import `fun`.kirari.hanako.feature.history.presentation.HistoryWorkflowController
import `fun`.kirari.hanako.feature.history.application.QuestionCardExporter
import `fun`.kirari.hanako.feature.history.presentation.HistoryDetailUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "HanakoMainViewModel"
    private val container = (application as HanakoApplication).container
    private val repository: SettingsRepository = container.settingsRepository
    private val localOcrManager: LocalOcrManager = container.localOcrManager
    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )
    private val historyWorkflowController = HistoryWorkflowController(
        scope = viewModelScope,
        settings = settings,
        solveOperations = container.workflow.operations,
        settingsRepository = repository,
        questionCardExporter = QuestionCardExporter(application, repository)
    )
    val historyDetailStates: StateFlow<Map<String, HistoryDetailUiState>> =
        historyWorkflowController.historyDetailStates
    val mergedHistory: StateFlow<List<ProcessingResult>> =
        historyWorkflowController.mergedHistory
    private val providerRuntimeController = ProviderRuntimeController(
        scope = viewModelScope,
        settings = settings,
        providerModelsApi = container.providerModelsApi,
        refreshKirariSession = { syncKirariSessionStatus(force = true) }
    )
    val connectionTestManager: ConnectionTestManager =
        providerRuntimeController.connectionTestManager
    val providerMetaState: StateFlow<ProviderMetaState> =
        providerRuntimeController.providerMetaState
    private val settingsEditorController = SettingsEditorController(
        scope = viewModelScope,
        repository = repository,
        providerMetaState = providerMetaState
    )
    private val webSearchQuotaController = WebSearchQuotaController(
        scope = viewModelScope,
        settings = settings,
        tavilyUsageApi = container.tavilyUsageApi
    )
    val webSearchQuotaState: StateFlow<WebSearchQuotaState> =
        webSearchQuotaController.state
    private val appUpdateController = AppUpdateController(
        scope = viewModelScope,
        appUpdateApi = container.appUpdateApi
    )
    val appUpdateState: StateFlow<AppUpdateUiState> = appUpdateController.state
    private val kirariAuthController = KirariAuthController(
        scope = viewModelScope,
        settingsStore = container.settingsStore,
        kirariAuthManager = container.kirariAuthManager,
        settingsProvider = { settings.value },
        clearProviderMeta = { providerRuntimeController.clearProviderMeta() }
    )
    val kirariAuthMessage: StateFlow<String?> = kirariAuthController.message
    val kirariAccountState: StateFlow<KirariAccountState> = kirariAuthController.accountState
    val kirariAuthenticatedProviderId: StateFlow<String?> = kirariAuthController.authenticatedProviderId

    init {
        syncLocalOcrInstallation()
        if (BuildConfig.SHOW_KIRARI_ENTRY) {
            syncKirariSessionStatus()
        }
        appUpdateController.checkOnceSilently()
    }

    fun updateProvider(provider: ModelProviderConfig) {
        settingsEditorController.updateProvider(provider)
    }

    fun addProvider() {
        settingsEditorController.addProvider()
    }

    fun selectProvider(providerId: String) {
        settingsEditorController.selectProvider(providerId)
    }

    fun deleteProvider(providerId: String) {
        settingsEditorController.deleteProvider(providerId)
    }

    fun updateAssistant(assistant: AssistantPreset) {
        settingsEditorController.updateAssistant(assistant)
    }

    fun addAssistant() {
        settingsEditorController.addAssistant()
    }

    fun selectAssistant(assistantId: String) = settingsEditorController.selectAssistant(assistantId)

    fun deleteAssistant(assistantId: String) {
        settingsEditorController.deleteAssistant(assistantId)
    }

    fun setRoute(route: ProcessingRoute) {
        settingsEditorController.setRoute(route)
    }

    fun toggleAnswerStyle() {
        viewModelScope.launch {
            repository.update { current ->
                current.copy(
                    answerStyle = when (current.answerStyle) {
                        AnswerStyle.DIRECT -> AnswerStyle.CHALLENGE
                        AnswerStyle.CHALLENGE -> AnswerStyle.DIRECT
                    }
                )
            }
        }
    }

    fun setScreenCaptureMethod(method: ScreenCaptureMethod) {
        settingsEditorController.setScreenCaptureMethod(method)
    }

    fun updateModelSelection(purpose: ModelPurpose, selection: ModelSelection) =
        settingsEditorController.updateModelSelection(purpose, selection)

    fun syncLocalOcrInstallation() {
        viewModelScope.launch {
            AppDebugLogStore.i("LocalOcrUi", "syncLocalOcrInstallation start")
            val status = withContext(Dispatchers.IO) { localOcrManager.installationStatus() }
            AppDebugLogStore.i("LocalOcrUi", "syncLocalOcrInstallation done installed=${status.installed}")
            repository.update { current ->
                current.copy(
                    localOcr = current.localOcr.copy(
                        installed = status.installed,
                        lastMessage = if (status.installed) "本地 ML Kit 已内置，可直接使用" else "本地 ML Kit 当前不可用"
                    )
                )
            }
        }
    }

    fun updateModelSelectionWithFavorite(
        purpose: ModelPurpose,
        selection: ModelSelection,
        favoriteModel: Boolean = false
    ) = settingsEditorController.updateModelSelectionWithFavorite(purpose, selection, favoriteModel)

    fun toggleFavoriteModel(providerId: String, modelId: String) =
        settingsEditorController.toggleFavoriteModel(providerId, modelId)

    fun selectHistoryConversationModel(
        resultId: String,
        selection: ModelSelection,
        addToFavorites: Boolean = false
    ) {
        historyWorkflowController.selectConversationModel(resultId, selection)
        if (addToFavorites) {
            selection.providerId?.let { providerId ->
                settingsEditorController.addFavoriteModel(providerId, selection.model)
            }
        }
    }

    fun removeFavoriteModel(providerId: String, modelId: String) =
        settingsEditorController.removeFavoriteModel(providerId, modelId)

    fun updateAutomationSettings(transform: (AutomationSettings) -> AutomationSettings) {
        settingsEditorController.updateAutomationSettings(transform)
    }

    fun setTrustAllHttpsCertificates(enabled: Boolean) {
        settingsEditorController.setTrustAllHttpsCertificates(enabled)
    }

    fun updateKirariSettings(transform: (KirariSettings) -> KirariSettings) {
        settingsEditorController.updateKirariSettings(transform)
    }

    fun updateWebSearchSettings(transform: (WebSearchSettings) -> WebSearchSettings) {
        settingsEditorController.updateWebSearchSettings(transform)
    }

    fun queryWebSearchQuota() {
        webSearchQuotaController.query()
    }

    fun resetWebSearchQuotaState() {
        webSearchQuotaController.reset()
    }

    fun startKirariLogin(onReady: (String) -> Unit) {
        kirariAuthController.startLogin(onReady)
    }

    fun handleKirariRedirect(uri: Uri) {
        kirariAuthController.handleRedirect(uri)
    }

    fun consumeKirariAuthenticatedProvider() {
        kirariAuthController.consumeAuthenticatedProvider()
    }

    fun logoutKirari() {
        kirariAuthController.logout()
    }

    fun consumeKirariAuthMessage() {
        kirariAuthController.consumeMessage()
    }

    fun clearHistory() {
        historyWorkflowController.clearHistory()
    }

    fun deleteHistoryItem(resultId: String) {
        historyWorkflowController.deleteHistoryItem(resultId)
    }

    fun regenerateHistoryResult(resultId: String) {
        historyWorkflowController.regenerateHistoryResult(resultId)
    }

    fun sendHistoryFollowUp(resultId: String, prompt: String, quotedFragments: List<QuotedFragment> = emptyList()) {
        historyWorkflowController.sendHistoryFollowUp(resultId, prompt, quotedFragments)
    }

    fun retryLatestHistoryFollowUp(resultId: String) {
        historyWorkflowController.retryLatestHistoryFollowUp(resultId)
    }

    fun createHistoryGroup(name: String, onResult: (HistoryCommandResult) -> Unit = {}) =
        historyWorkflowController.createGroup(name, onResult)

    fun renameHistoryGroup(id: String, name: String, onResult: (HistoryCommandResult) -> Unit = {}) =
        historyWorkflowController.renameGroup(id, name, onResult)

    fun deleteHistoryGroup(id: String, onResult: (HistoryCommandResult) -> Unit = {}) =
        historyWorkflowController.deleteGroup(id, onResult)

    fun setHistoryGroups(recordIds: Set<String>, groupIds: Set<String>, onResult: (HistoryCommandResult) -> Unit = {}) =
        historyWorkflowController.setGroups(recordIds, groupIds, onResult)

    fun setHistoryMarkerColor(recordIds: Set<String>, color: HistoryMarkerColor?, onResult: (HistoryCommandResult) -> Unit = {}) =
        historyWorkflowController.setMarkerColor(recordIds, color, onResult)

    fun createQuestionCard(resultId: String, onComplete: (QuestionCardArtifact?) -> Unit = {}) =
        historyWorkflowController.createQuestionCard(resultId, onComplete)

    fun testProviderConnection(provider: ModelProviderConfig) {
        providerRuntimeController.testProviderConnection(provider)
    }

    fun resetConnectionTest(providerId: String) {
        providerRuntimeController.resetConnectionTest(providerId)
    }

    fun loadProviderMeta(provider: ModelProviderConfig) {
        providerRuntimeController.loadProviderMeta(provider)
    }

    fun resetProviderMeta() {
        providerRuntimeController.resetProviderMeta()
    }

    fun shouldSuggestKirariAutoSetup(settings: AppSettings, providerMetaState: ProviderMetaState): Boolean {
        return settingsEditorController.shouldSuggestKirariAutoSetup(settings, providerMetaState)
    }

    fun applyKirariAutoSetup() {
        settingsEditorController.applyKirariAutoSetup()
    }

    fun clearDebugLogs() {
        AppDebugLogStore.clear()
    }

    fun showUpdateDialog() {
        appUpdateController.showDialog()
    }

    fun checkForUpdates() {
        appUpdateController.checkManually()
    }

    fun dismissUpdateDialog() {
        appUpdateController.dismissDialog()
    }

    fun hasKirariClientId(): Boolean = BuildConfig.KIRARI_OIDC_CLIENT_ID.isNotBlank()

    fun syncKirariSessionStatus(force: Boolean = false) {
        kirariAuthController.syncSessionStatus(force)
    }
}
