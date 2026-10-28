package `fun`.kirari.hanako.feature.history.presentation

import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.HistoryCommandResult
import `fun`.kirari.hanako.core.data.HistoryMarkerColor
import `fun`.kirari.hanako.core.data.QuestionCardArtifact
import `fun`.kirari.hanako.core.data.ModelSelection
import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.core.data.SettingsRepository
import `fun`.kirari.hanako.core.data.modelSelectionFor
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.QuotedFragment
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.solve.application.SolveOperations
import `fun`.kirari.hanako.feature.history.application.QuestionCardExporter
import `fun`.kirari.hanako.solve.application.SolveHistoryState
import `fun`.kirari.hanako.solve.model.WorkflowTaskKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HistoryDetailOperation {
    data object Idle : HistoryDetailOperation
    data class RegeneratingInitialAnswer(val answerVersionIndex: Int?) : HistoryDetailOperation
    data class SendingFollowUp(val activeTurnId: String?) : HistoryDetailOperation
}

data class HistoryDetailUiState(
    val result: ProcessingResult,
    val operation: HistoryDetailOperation,
    val conversationModelPurpose: ModelPurpose,
    val conversationModelSelection: ModelSelection,
    val conversationModelLabel: String
)

internal class HistoryWorkflowController(
    private val scope: CoroutineScope,
    private val settings: StateFlow<AppSettings>,
    private val solveOperations: SolveOperations,
    private val settingsRepository: SettingsRepository,
    private val questionCardExporter: QuestionCardExporter? = null
) {
    private val _conversationModelSelections = MutableStateFlow<Map<String, ModelSelection>>(emptyMap())
    private val historyState = solveOperations.observeHistory(settings.map { it.history })
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = `fun`.kirari.hanako.solve.application.SolveHistoryState(
                results = emptyList(),
                activeTasks = emptyMap()
            )
        )

    val historyDetailStates: StateFlow<Map<String, HistoryDetailUiState>> = combine(
        historyState,
        _conversationModelSelections,
        settings
    ) { state, modelOverrides, currentSettings ->
        buildHistoryDetailStates(state, modelOverrides, currentSettings)
    }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    val mergedHistory: StateFlow<List<ProcessingResult>> = historyState
        .map { it.results }
        .stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun clearHistory() {
        _conversationModelSelections.value = emptyMap()
        questionCardExporter?.deleteAllArtifacts()
        scope.launch {
            solveOperations.clearHistory()
        }
    }

    fun deleteHistoryItem(resultId: String) {
        _conversationModelSelections.value = _conversationModelSelections.value - resultId
        questionCardExporter?.deleteArtifacts(resultId)
        scope.launch {
            solveOperations.removeHistoryResult(resultId)
        }
    }

    fun regenerateHistoryResult(resultId: String) {
        scope.launch {
            solveOperations.regenerate(settings.value, resultId)
        }
    }

    fun sendHistoryFollowUp(resultId: String, prompt: String, quotedFragments: List<QuotedFragment> = emptyList()) {
        scope.launch {
            solveOperations.continueConversation(
                settings = settings.value,
                historyId = resultId,
                prompt = prompt,
                quotedFragments = quotedFragments,
                modelSelection = _conversationModelSelections.value[resultId]
            )
        }
    }

    fun retryLatestHistoryFollowUp(resultId: String) {
        scope.launch {
            solveOperations.retryLatestConversation(
                settings = settings.value,
                historyId = resultId,
                modelSelection = _conversationModelSelections.value[resultId]
            )
        }
    }

    fun selectConversationModel(resultId: String, selection: ModelSelection) {
        _conversationModelSelections.value = _conversationModelSelections.value + (resultId to selection)
    }

    fun createGroup(name: String, onResult: (HistoryCommandResult) -> Unit = {}) {
        scope.launch { onResult(settingsRepository.createHistoryGroup(name)) }
    }

    fun renameGroup(id: String, name: String, onResult: (HistoryCommandResult) -> Unit = {}) {
        scope.launch { onResult(settingsRepository.renameHistoryGroup(id, name)) }
    }

    fun deleteGroup(id: String, onResult: (HistoryCommandResult) -> Unit = {}) {
        scope.launch { onResult(settingsRepository.deleteHistoryGroup(id)) }
    }

    fun setGroups(recordIds: Set<String>, groupIds: Set<String>, onResult: (HistoryCommandResult) -> Unit = {}) {
        scope.launch { onResult(settingsRepository.setHistoryGroups(recordIds, groupIds)) }
    }

    fun setMarkerColor(recordIds: Set<String>, color: HistoryMarkerColor?, onResult: (HistoryCommandResult) -> Unit = {}) {
        scope.launch { onResult(settingsRepository.setHistoryMarkerColor(recordIds, color)) }
    }

    fun createQuestionCard(resultId: String, onComplete: (QuestionCardArtifact?) -> Unit = {}) {
        val exporter = questionCardExporter ?: return
        scope.launch {
            val result = settings.value.history.firstOrNull { it.id == resultId } ?: return@launch onComplete(null)
            onComplete(exporter.export(result).getOrNull())
        }
    }
}

internal fun buildHistoryDetailStates(
    state: SolveHistoryState,
    modelOverrides: Map<String, ModelSelection>,
    settings: AppSettings
): Map<String, HistoryDetailUiState> {
    return state.results.associate { result ->
        val purpose = when (result.route) {
            ProcessingRoute.OCR_THEN_LLM -> ModelPurpose.TEXT
            ProcessingRoute.MULTIMODAL_DIRECT -> ModelPurpose.VISION
        }
        val selection = modelOverrides[result.id] ?: settings.modelSelectionFor(purpose)
        val operation = when (val task = state.activeTasks[result.id]) {
            null -> HistoryDetailOperation.Idle
            else -> when (task.kind) {
                WorkflowTaskKind.REGENERATE_ANSWER ->
                    HistoryDetailOperation.RegeneratingInitialAnswer(task.answerVersionIndex)
                WorkflowTaskKind.CONVERSATION ->
                    HistoryDetailOperation.SendingFollowUp(task.conversationTurnId)
                else -> HistoryDetailOperation.Idle
            }
        }
        result.id to HistoryDetailUiState(
            result = result,
            operation = operation,
            conversationModelPurpose = purpose,
            conversationModelSelection = selection,
            conversationModelLabel = selection.model.takeIf(String::isNotBlank)
                ?: when (purpose) {
                    ModelPurpose.TEXT -> "选择文本模型"
                    ModelPurpose.VISION -> "选择多模态模型"
                    ModelPurpose.OCR -> "选择模型"
                }
        )
    }
}
