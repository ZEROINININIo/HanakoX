package `fun`.kirari.hanako.feature.overlay.presentation

import `fun`.kirari.hanako.feature.overlay.state.AutoRunState
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import `fun`.kirari.hanako.feature.overlay.state.OverlaySheetMode
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState

import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.core.data.AnswerStyle
import `fun`.kirari.hanako.core.data.ModelSelection
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class OverlaySettingsController(
    private val scope: CoroutineScope,
    private val repository: SettingsRepository,
    private val uiState: StateFlow<OverlayUiState>
) {
    fun toggleWebSearch() {
        scope.launch {
            repository.update { current ->
                current.copy(
                    webSearch = current.webSearch.copy(enabled = !current.webSearch.enabled)
                )
            }
        }
    }

    fun selectAssistant(assistantId: String) = repository.selectAssistant(scope, assistantId)

    fun selectPreviousAssistant() {
        val current = uiState.value.settings
        val assistants = current.assistants
        if (assistants.isEmpty()) return
        val selectedIndex = assistants.indexOfFirst { it.id == current.selectedAssistantId }.takeIf { it >= 0 } ?: 0
        val previousIndex = if (selectedIndex == 0) assistants.lastIndex else selectedIndex - 1
        selectAssistant(assistants[previousIndex].id)
    }

    fun selectNextAssistant() {
        val current = uiState.value.settings
        val assistants = current.assistants
        if (assistants.isEmpty()) return
        val selectedIndex = assistants.indexOfFirst { it.id == current.selectedAssistantId }.takeIf { it >= 0 } ?: 0
        val nextIndex = if (selectedIndex == assistants.lastIndex) 0 else selectedIndex + 1
        selectAssistant(assistants[nextIndex].id)
    }

    fun updateModelSelection(purpose: ModelPurpose, selection: ModelSelection) =
        repository.updateModelSelection(scope, purpose, selection)

    fun updateModelSelectionWithFavorite(purpose: ModelPurpose, selection: ModelSelection, favoriteModel: Boolean = false) =
        repository.updateModelSelectionWithFavorite(scope, purpose, selection, favoriteModel)

    fun toggleFavoriteModel(providerId: String, modelId: String) =
        repository.toggleFavoriteModel(scope, providerId, modelId)

    fun toggleProcessingRoute() {
        scope.launch {
            repository.update { current ->
                current.copy(
                    processingRoute = when (current.processingRoute) {
                        ProcessingRoute.OCR_THEN_LLM -> ProcessingRoute.MULTIMODAL_DIRECT
                        ProcessingRoute.MULTIMODAL_DIRECT -> ProcessingRoute.OCR_THEN_LLM
                    }
                )
            }
        }
    }

    fun toggleAnswerStyle() {
        scope.launch {
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
}
