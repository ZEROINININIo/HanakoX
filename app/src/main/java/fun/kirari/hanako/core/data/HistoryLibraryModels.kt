package `fun`.kirari.hanako.core.data

import `fun`.kirari.hanako.core.model.ProcessingResult
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class HistoryMarkerColor {
    MIST_BLUE,
    SAGE,
    LILAC,
    OCHRE,
    ROSE,
    TEAL,
    SLATE
}

@Serializable
sealed interface HistoryTitle {
    @Serializable
    data object Assistant : HistoryTitle

    @Serializable
    data class Custom(val text: String) : HistoryTitle

    @Serializable
    data class AiSummary(
        val text: String,
        val sourceRevision: Long,
        val generatedAtMillis: Long
    ) : HistoryTitle
}

@Serializable
data class HistoryGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis
)

@Serializable
data class HistoryRecordMetadata(
    val historyId: String,
    val title: HistoryTitle = HistoryTitle.Assistant,
    val markerColor: HistoryMarkerColor? = null,
    val groupIds: List<String> = emptyList(),
    val lastActivityAtMillis: Long,
    val contentRevision: Long = 0L,
    val questionCard: QuestionCardArtifact? = null
)

@Serializable
data class QuestionCardArtifact(
    val id: String,
    val contentRevision: Long,
    val path: String,
    val widthPx: Int,
    val heightPx: Int,
    val themeMode: String,
    val createdAtMillis: Long
)

@Serializable
data class TitleSummarySettings(
    val enabled: Boolean = false,
    val prompt: String = "请根据题目和解答，总结知识点和关键题目特征，用不超过 12 个字生成标题，例如：计算二重积分。"
)

fun HistoryRecordMetadata.normalized(validGroupIds: Set<String>): HistoryRecordMetadata {
    return copy(
        groupIds = groupIds.filter(validGroupIds::contains).distinct(),
        contentRevision = contentRevision.coerceAtLeast(0L)
    )
}

fun AppSettings.historyMetadataFor(result: ProcessingResult): HistoryRecordMetadata {
    return historyMetadata.firstOrNull { it.historyId == result.id }
        ?: HistoryRecordMetadata(
            historyId = result.id,
            lastActivityAtMillis = result.createdAtMillis
        )
}

fun AppSettings.historyDisplayTitle(result: ProcessingResult): String {
    return when (val title = historyMetadataFor(result).title) {
        HistoryTitle.Assistant -> result.assistantName
        is HistoryTitle.Custom -> title.text.ifBlank { result.assistantName }
        is HistoryTitle.AiSummary -> title.text.ifBlank { result.assistantName }
    }
}

fun AppSettings.normalizedHistoryMetadata(): List<HistoryRecordMetadata> {
    val validResultIds = history.mapTo(mutableSetOf()) { it.id }
    val validGroupIds = historyGroups.mapTo(mutableSetOf()) { it.id }
    val existing = historyMetadata
        .filter { it.historyId in validResultIds }
        .map { it.normalized(validGroupIds) }
        .associateBy { it.historyId }
    return history.map { result ->
        existing[result.id] ?: HistoryRecordMetadata(
            historyId = result.id,
            lastActivityAtMillis = result.createdAtMillis
        )
    }
}
