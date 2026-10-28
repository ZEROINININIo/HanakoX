package `fun`.kirari.hanako.solve.runtime

import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute

internal class InMemoryWorkflowHistoryRepository(
    initialSettings: AppSettings = AppSettings()
) : WorkflowHistoryRepository {
    var settings: AppSettings = initialSettings
        private set

    override suspend fun read(): AppSettings = settings

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
    }
}

internal fun testProcessingResult(
    id: String,
    answer: String = "",
    screenshotPaths: List<String> = emptyList()
): ProcessingResult {
    return ProcessingResult(
        id = id,
        assistantName = "assistant",
        route = ProcessingRoute.OCR_THEN_LLM,
        answer = answer,
        screenshotPaths = screenshotPaths
    )
}
