package `fun`.kirari.hanako.solve.runtime

import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.SettingsRepository

internal interface WorkflowHistoryRepository {
    suspend fun read(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

internal class SettingsWorkflowHistoryRepository(
    private val repository: SettingsRepository
) : WorkflowHistoryRepository {
    override suspend fun read(): AppSettings = repository.read()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        repository.update(transform)
    }
}
