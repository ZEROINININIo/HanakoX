package `fun`.kirari.hanako.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import `fun`.kirari.hanako.core.model.ProcessingResult
import java.util.UUID

class SettingsRepository(private val store: SettingsStore) {

    val settings = store.settings

    suspend fun read(): AppSettings = store.read()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.update(transform)
    }

    suspend fun createHistoryGroup(name: String): HistoryCommandResult {
        val normalized = name.normalizedHistoryGroupName()
        if (normalized.isBlank() || normalized.codePointCount(0, normalized.length) > 40) {
            return HistoryCommandResult.Invalid("分组名称不能为空且不能超过 40 个字符")
        }
        var result: HistoryCommandResult = HistoryCommandResult.Success
        store.update { current ->
            if (current.historyGroups.hasNameConflict(normalized)) {
                result = HistoryCommandResult.NameConflict
                current
            } else {
                current.copy(historyGroups = current.historyGroups + HistoryGroup(id = UUID.randomUUID().toString(), name = normalized))
            }
        }
        return result
    }

    suspend fun renameHistoryGroup(id: String, name: String): HistoryCommandResult {
        val normalized = name.normalizedHistoryGroupName()
        if (normalized.isBlank() || normalized.codePointCount(0, normalized.length) > 40) {
            return HistoryCommandResult.Invalid("分组名称不能为空且不能超过 40 个字符")
        }
        var result: HistoryCommandResult = HistoryCommandResult.Success
        store.update { current ->
            if (current.historyGroups.none { it.id == id }) {
                result = HistoryCommandResult.NotFound
                current
            } else if (current.historyGroups.hasNameConflict(normalized, id)) {
                result = HistoryCommandResult.NameConflict
                current
            } else {
                current.copy(historyGroups = current.historyGroups.map { group ->
                    if (group.id == id) group.copy(name = normalized, updatedAtMillis = System.currentTimeMillis()) else group
                })
            }
        }
        return result
    }

    suspend fun deleteHistoryGroup(id: String): HistoryCommandResult {
        var result: HistoryCommandResult = HistoryCommandResult.Success
        store.update { current ->
            if (current.historyGroups.none { it.id == id }) {
                result = HistoryCommandResult.NotFound
                current
            } else {
                current.copy(
                    historyGroups = current.historyGroups.filterNot { it.id == id },
                    historyMetadata = current.historyMetadata.map { it.copy(groupIds = it.groupIds.filterNot { groupId -> groupId == id }) }
                )
            }
        }
        return result
    }

    suspend fun setHistoryGroups(recordIds: Set<String>, groupIds: Set<String>): HistoryCommandResult {
        if (recordIds.isEmpty()) return HistoryCommandResult.Success
        var result: HistoryCommandResult = HistoryCommandResult.Success
        store.update { current ->
            val existingIds = current.history.mapTo(mutableSetOf()) { it.id }
            val validRecords = recordIds intersect existingIds
            val validGroups = groupIds intersect current.historyGroups.mapTo(mutableSetOf()) { it.id }
            if (validRecords.isEmpty()) {
                result = HistoryCommandResult.NotFound
                current
            } else {
                val metadata = current.normalizedHistoryMetadata().map { entry ->
                    if (entry.historyId in validRecords) entry.copy(groupIds = validGroups.toList()) else entry
                }
                current.copy(historyMetadata = metadata)
            }
        }
        return result
    }

    suspend fun setHistoryMarkerColor(recordIds: Set<String>, color: HistoryMarkerColor?): HistoryCommandResult {
        if (recordIds.isEmpty()) return HistoryCommandResult.Success
        var result: HistoryCommandResult = HistoryCommandResult.Success
        store.update { current ->
            val existingIds = current.history.mapTo(mutableSetOf()) { it.id }
            val validRecords = recordIds intersect existingIds
            if (validRecords.isEmpty()) {
                result = HistoryCommandResult.NotFound
                current
            } else {
                current.copy(historyMetadata = current.normalizedHistoryMetadata().map { entry ->
                    if (entry.historyId in validRecords) entry.copy(markerColor = color) else entry
                })
            }
        }
        return result
    }

    suspend fun updateHistoryMetadata(
        historyId: String,
        transform: (HistoryRecordMetadata) -> HistoryRecordMetadata
    ): HistoryCommandResult {
        var result: HistoryCommandResult = HistoryCommandResult.Success
        store.update { current ->
            if (current.history.none { it.id == historyId }) {
                result = HistoryCommandResult.NotFound
                current
            } else {
                val metadata = current.normalizedHistoryMetadata().map { entry ->
                    if (entry.historyId == historyId) transform(entry) else entry
                }
                current.copy(historyMetadata = metadata)
            }
        }
        return result
    }

    fun updateModelSelection(
        scope: CoroutineScope,
        purpose: ModelPurpose,
        selection: ModelSelection
    ) {
        scope.launch {
            store.update { current ->
                when (purpose) {
                    ModelPurpose.TEXT -> current.copy(textModelSelection = selection)
                    ModelPurpose.VISION -> current.copy(visionModelSelection = selection)
                    ModelPurpose.OCR -> current.copy(ocrModelSelection = selection)
                }
            }
        }
    }

    fun updateModelSelectionWithFavorite(
        scope: CoroutineScope,
        purpose: ModelPurpose,
        selection: ModelSelection,
        favoriteModel: Boolean = false
    ) {
        scope.launch {
            store.update { current ->
                val next = when (purpose) {
                    ModelPurpose.TEXT -> current.copy(textModelSelection = selection)
                    ModelPurpose.VISION -> current.copy(visionModelSelection = selection)
                    ModelPurpose.OCR -> current.copy(ocrModelSelection = selection)
                }
                if (!favoriteModel || selection.providerId == null || selection.model.isBlank()) {
                    next
                } else {
                    next.updateProviderFavoriteModels(selection.providerId) { favorites ->
                        favorites.addIfMissing(selection.model)
                    }
                }
            }
        }
    }

    fun toggleFavoriteModel(
        scope: CoroutineScope,
        providerId: String,
        modelId: String
    ) {
        val trimmedModelId = modelId.trim()
        if (trimmedModelId.isBlank()) return
        scope.launch {
            store.update { current ->
                current.updateProviderFavoriteModels(providerId) { favorites ->
                    if (favorites.any { it.equals(trimmedModelId, ignoreCase = true) }) {
                        favorites.removeByName(trimmedModelId)
                    } else {
                        favorites + trimmedModelId
                    }
                }
            }
        }
    }

    fun addFavoriteModel(scope: CoroutineScope, providerId: String, modelId: String) {
        val trimmedModelId = modelId.trim()
        if (trimmedModelId.isBlank()) return
        scope.launch {
            store.update { current ->
                current.updateProviderFavoriteModels(providerId) { favorites ->
                    favorites.addIfMissing(trimmedModelId)
                }
            }
        }
    }

    fun selectAssistant(scope: CoroutineScope, assistantId: String) {
        scope.launch {
            store.update { current ->
                if (current.assistants.any { it.id == assistantId }) {
                    current.copy(selectedAssistantId = assistantId)
                } else {
                    current
                }
            }
        }
    }

    fun removeFavoriteModel(scope: CoroutineScope, providerId: String, modelId: String) {
        val trimmedModelId = modelId.trim()
        if (trimmedModelId.isBlank()) return
        scope.launch {
            store.update { current ->
                current.updateProviderFavoriteModels(providerId) { favorites ->
                    favorites.removeByName(trimmedModelId)
                }
            }
        }
    }
}

internal fun AppSettings.updateProviderFavoriteModels(
    providerId: String,
    transform: (List<String>) -> List<String>
): AppSettings {
    return copy(
        providers = providers.map { provider ->
            if (provider.id != providerId) {
                provider
            } else {
                provider.copy(
                    favoriteModels = transform(provider.favoriteModels)
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .distinctBy { it.lowercase() }
                )
            }
        }
    )
}

internal fun List<String>.addIfMissing(modelId: String): List<String> {
    return if (any { it.equals(modelId, ignoreCase = true) }) this else this + modelId
}

internal fun List<String>.removeByName(modelId: String): List<String> {
    return filterNot { it.equals(modelId, ignoreCase = true) }
}
