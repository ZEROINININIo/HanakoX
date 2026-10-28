package `fun`.kirari.hanako.core.model

import java.util.UUID
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable

@Serializable
enum class ProcessingRoute {
    OCR_THEN_LLM,
    MULTIMODAL_DIRECT
}

@Serializable
data class ProcessingResult(
    val id: String = UUID.randomUUID().toString(),
    val assistantName: String,
    val route: ProcessingRoute,
    val status: ProcessingStatus = ProcessingStatus.SUCCESS,
    val modelSummary: String = "",
    val detail: String = "",
    val extractedText: String = "",
    val answer: String = "",
    val answerVersions: List<AnswerVersion> = emptyList(),
    val followUpTurns: List<FollowUpTurn> = emptyList(),
    val automationThought: String = "",
    val automationAction: AutomationActionRecord? = null,
    val automationActionDelivery: AutomationActionDelivery = AutomationActionDelivery.COMPLETED,
    val screenshotBase64: String? = null,
    val screenshotPath: String? = null,
    val screenshotPaths: List<String> = emptyList(),
    val events: List<ProcessingEvent> = emptyList(),
    val checkpoints: List<ProcessingCheckpointSummary> = emptyList(),
    val lastSearchAtMillis: Long? = null,
    val lastSearchQuery: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val allScreenshotPaths: List<String>
        get() = screenshotPaths.ifEmpty {
            screenshotPath?.let(::listOf) ?: emptyList()
        }
}

@Serializable
data class FollowUpTurn(
    val id: String = UUID.randomUUID().toString(),
    val userText: String,
    val quotedFragments: List<QuotedFragment> = emptyList(),
    val assistantText: String = "",
    val assistantVersions: List<AnswerVersion> = emptyList(),
    val pendingAssistantText: String = "",
    val modelSummary: String = "",
    val completed: Boolean = false,
    val errorMessage: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
data class AnswerVersion(
    val text: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString()
)

@Serializable
enum class RichTextBlockKind {
    PARAGRAPH,
    DISPLAY_MATH
}

@Serializable
data class ContentAnchor(
    val historyId: String,
    val messageId: String,
    val answerVersionId: String? = null,
    val blockId: String,
    val blockKind: RichTextBlockKind,
    val sourceRevision: Long,
    val rawMarkdown: String,
    val previewLabel: String
)

@Serializable
data class QuotedFragment(
    val id: String = UUID.randomUUID().toString(),
    val anchor: ContentAnchor,
    val quotedAtMillis: Long = System.currentTimeMillis()
)

fun AnswerVersion.stableFor(messageId: String, index: Int): AnswerVersion {
    val stableId = UUID.nameUUIDFromBytes(
        "hanako-answer-version:$messageId:$index:$createdAtMillis:$text".toByteArray(StandardCharsets.UTF_8)
    ).toString()
    return copy(id = stableId)
}

fun ProcessingResult.withStableAnswerVersionIds(): ProcessingResult {
    val initialMessageId = "$id:initial"
    val sourceVersions = answerVersions.ifEmpty {
        answer.takeIf(String::isNotBlank)?.let { listOf(AnswerVersion(it, createdAtMillis)) }.orEmpty()
    }
    val stableVersions = sourceVersions.mapIndexed { index, version ->
        version.stableFor(initialMessageId, index)
    }
    val stableTurns = followUpTurns.map { turn ->
        val turnSourceVersions = turn.assistantVersions.ifEmpty {
            turn.assistantText.takeIf(String::isNotBlank)?.let { listOf(AnswerVersion(it, turn.createdAtMillis)) }.orEmpty()
        }
        turn.copy(
            assistantVersions = turnSourceVersions.mapIndexed { index, version ->
                version.stableFor(turn.id, index)
            }
        )
    }
    return copy(
        answerVersions = stableVersions,
        followUpTurns = stableTurns
    )
}

@Serializable
data class ProcessingEvent(
    val title: String,
    val detail: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
data class ProcessingCheckpointSummary(
    val nodeId: String,
    val inputSummary: String,
    val outputSummary: String,
    val artifacts: Map<String, String> = emptyMap(),
    val replayable: Boolean,
    val resumable: Boolean,
    val createdAtMillis: Long
)

@Serializable
enum class ProcessingStatus {
    RUNNING,
    SUCCESS,
    ERROR,
    TIMEOUT
}

@Serializable
data class AutomationActionRecord(
    val type: AutomationActionType,
    val text: String
)

@Serializable
enum class AutomationActionType {
    SET_CLIPBOARD,
    SHOW_BUBBLE_LETTERS
}

@Serializable
enum class AutomationActionDelivery {
    PENDING,
    DELIVERING,
    COMPLETED,
    FAILED,
    ABANDONED
}

fun ProcessingResult.displayedAnswerVersions(): List<AnswerVersion> {
    return answerVersions.ifEmpty {
        answer.takeIf(String::isNotBlank)?.let(::AnswerVersion)?.let(::listOf) ?: emptyList()
    }
}

fun ProcessingResult.latestAnswerText(): String {
    return displayedAnswerVersions().lastOrNull()?.text ?: answer
}

fun ProcessingResult.withAppendedAnswerVersion(newAnswer: String): ProcessingResult {
    val existingVersions = displayedAnswerVersions().toMutableList()
    if (newAnswer.isNotBlank()) existingVersions += AnswerVersion(newAnswer)
    return copy(answer = newAnswer, answerVersions = existingVersions)
}

fun ProcessingResult.withMergedAnswerVersionsFrom(previous: ProcessingResult): ProcessingResult {
    val mergedVersions = previous.displayedAnswerVersions().toMutableList()
    answer.takeIf(String::isNotBlank)?.let { mergedVersions += AnswerVersion(it) }
    return copy(answerVersions = mergedVersions)
}

fun FollowUpTurn.displayedAssistantVersions(): List<AnswerVersion> {
    return assistantVersions.ifEmpty {
        assistantText.takeIf(String::isNotBlank)?.let(::AnswerVersion)?.let(::listOf) ?: emptyList()
    }
}

fun FollowUpTurn.latestAssistantText(): String {
    return displayedAssistantVersions().lastOrNull()?.text ?: assistantText
}

fun FollowUpTurn.currentAssistantText(): String {
    return pendingAssistantText.ifBlank { latestAssistantText() }
}

fun FollowUpTurn.withStreamingAssistantText(text: String): FollowUpTurn {
    return copy(pendingAssistantText = text)
}

fun FollowUpTurn.withCommittedAssistantVersion(): FollowUpTurn {
    val versions = assistantVersions.toMutableList()
    if (pendingAssistantText.isNotBlank()) versions += AnswerVersion(pendingAssistantText)
    return copy(
        assistantVersions = versions,
        pendingAssistantText = "",
        completed = true,
        errorMessage = null
    )
}

fun FollowUpTurn.withAssistantError(message: String): FollowUpTurn {
    return copy(
        pendingAssistantText = "",
        completed = true,
        errorMessage = message
    )
}
