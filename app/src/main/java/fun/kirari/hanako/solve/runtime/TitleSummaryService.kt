package `fun`.kirari.hanako.solve.runtime

import `fun`.kirari.hanako.core.data.HistoryTitle
import `fun`.kirari.hanako.core.data.historyMetadataFor
import `fun`.kirari.hanako.core.data.normalizedHistoryMetadata
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.latestAnswerText
import `fun`.kirari.hanako.core.network.UnifiedLLMClient
import `fun`.kirari.hanako.solve.workflow.ProcessingPipeline
import `fun`.kirari.llm.core.LlmEvent
import kotlinx.coroutines.flow.collect

internal class TitleSummaryService(
    private val repository: WorkflowHistoryRepository,
    private val client: UnifiedLLMClient
) {
    suspend fun generate(result: ProcessingResult, models: ProcessingPipeline.ResolvedModels) {
        val settings = repository.read()
        val preset = models.assistant
        if (!preset.titleSummary.enabled) return
        val metadata = settings.historyMetadataFor(result)
        val revision = metadata.contentRevision
        val provider = models.textProvider ?: models.visionProvider ?: return
        val model = models.textModel.ifBlank { models.visionModel }
        if (model.isBlank()) return
        val question = result.extractedText.ifBlank { result.lastSearchQuery.orEmpty() }
        val answer = result.latestAnswerText()
        if (question.isBlank() && answer.isBlank()) return
        val text = StringBuilder()
        runCatching {
            client.stream(
                provider = provider,
                model = model,
                systemPrompt = "你是历史记录标题生成器，只输出一个简短标题，不要引号、编号或解释。",
                userPrompt = "${preset.titleSummary.prompt}\n\n题目：\n$question\n\n解答：\n$answer",
                firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                trustAllHttpsCertificates = models.trustAllHttpsCertificates
            ).collect { event -> if (event is LlmEvent.TextDelta) text.append(event.text) }
        }.getOrNull()
        val title = text.toString().trim().replace("\n", " ").trim('"', '\'', '“', '”')
        if (title.isBlank()) return
        repository.update { current ->
            val currentEntry = current.historyMetadata.firstOrNull { it.historyId == result.id } ?: return@update current
            if (currentEntry.contentRevision != revision) current
            else current.copy(historyMetadata = current.normalizedHistoryMetadata().map {
                if (it.historyId == result.id) it.copy(title = HistoryTitle.AiSummary(title, revision, System.currentTimeMillis())) else it
            })
        }
    }
}
