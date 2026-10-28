package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.core.data.WebSearchSettings
import `fun`.kirari.hanako.core.data.defaultAssistant
import `fun`.kirari.hanako.core.data.defaultProvider
import `fun`.kirari.hanako.core.network.UnifiedLLMClient
import `fun`.kirari.hanako.core.model.FollowUpTurn
import `fun`.kirari.hanako.core.model.AnswerVersion
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.model.ContentAnchor
import `fun`.kirari.hanako.core.model.QuotedFragment
import `fun`.kirari.hanako.core.model.RichTextBlockKind
import `fun`.kirari.hanako.solve.model.ConversationIntent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationWorkflowTest {

    private val workflow = ConversationWorkflow(
        unifiedClient = UnifiedLLMClient(),
        pipeline = ProcessingPipeline()
    )

    @Test
    fun prepareTurn_buildsOrderedOcrConversationAndOmitsInvalidAssistantMessages() = runTest {
        val existing = baseResult().copy(
            extractedText = "question text",
            answer = "initial answer",
            followUpTurns = listOf(
                FollowUpTurn(userText = "first", assistantText = "first answer", completed = true),
                FollowUpTurn(userText = "partial", assistantText = "unfinished", completed = false),
                FollowUpTurn(userText = "failed", assistantText = "ignored", completed = true, errorMessage = "error")
            )
        )

        val prepared = workflow.prepareTurn(
            existingResult = existing,
            models = models(),
            intent = ConversationIntent.NewTurn("current"),
            turnId = "turn-current"
        )

        assertEquals(
            listOf("system", "user", "assistant", "user", "assistant", "user", "user", "user"),
            prepared.messages.map { it.role }
        )
        assertTrue(prepared.messages[1].text().contains("question text"))
        assertEquals("initial answer", prepared.messages[2].text())
        assertEquals(listOf("first", "partial", "failed", "current"), prepared.messages.drop(3).filter { it.role == "user" }.map { it.text() })
        assertEquals("turn-current", prepared.startedResult.followUpTurns.last().id)
    }

    @Test
    fun prepareTurn_regeneratesLatestTurnAndKeepsItsVersions() = runTest {
        val existing = baseResult().copy(
            followUpTurns = listOf(
                FollowUpTurn(userText = "keep", assistantText = "kept answer", completed = true),
                FollowUpTurn(
                    userText = "retry",
                    assistantText = "legacy answer",
                    assistantVersions = listOf(AnswerVersion("first answer"), AnswerVersion("second answer")),
                    completed = true
                )
            )
        )

        val prepared = workflow.prepareTurn(
            existingResult = existing,
            models = models(),
            intent = ConversationIntent.RegenerateLatest,
            turnId = "replacement"
        )

        assertEquals(listOf("keep", "retry"), prepared.startedResult.followUpTurns.map { it.userText })
        assertEquals(listOf("system", "user", "assistant", "user", "assistant", "user"), prepared.messages.map { it.role })
        assertEquals("replacement", prepared.startedResult.followUpTurns.last().id)
        assertEquals(
            listOf("first answer", "second answer"),
            prepared.startedResult.followUpTurns.last().assistantVersions.map { it.text }
        )
        assertEquals("", prepared.startedResult.followUpTurns.last().assistantText)
        assertEquals("retry", prepared.messages.last().text())
    }

    @Test
    fun prepareTurn_keepsPlainUserTextAndAddsQuotedSnapshotToPrompt() = runTest {
        val quote = QuotedFragment(
            anchor = ContentAnchor(
                historyId = "history-1",
                messageId = "answer",
                answerVersionId = "version-1",
                blockId = "block-1",
                blockKind = RichTextBlockKind.DISPLAY_MATH,
                sourceRevision = 1L,
                rawMarkdown = "x^2 + y^2",
                previewLabel = "[公式]"
            )
        )
        val prepared = workflow.prepareTurn(
            existingResult = baseResult(),
            models = models(),
            intent = ConversationIntent.NewTurn("请解释", listOf(quote)),
            turnId = "turn-with-quote"
        )

        assertEquals("请解释", prepared.startedResult.followUpTurns.last().userText)
        assertEquals(listOf(quote), prepared.startedResult.followUpTurns.last().quotedFragments)
        assertTrue(prepared.messages.last().text().contains("[引用片段]"))
        assertTrue(prepared.messages.last().text().contains("x^2 + y^2"))
        assertTrue(prepared.messages.last().text().endsWith("请解释"))
    }

    private fun baseResult() = ProcessingResult(
        id = "history-1",
        assistantName = "assistant",
        route = ProcessingRoute.OCR_THEN_LLM
    )

    private fun models(): ProcessingPipeline.ResolvedModels {
        val provider = defaultProvider()
        return ProcessingPipeline.ResolvedModels(
            assistant = defaultAssistant(),
            ocrProvider = provider,
            ocrModel = "ocr",
            textProvider = provider,
            textModel = "text",
            visionProvider = provider,
            visionModel = "vision",
            firstDeltaTimeoutMillis = 1_000L,
            route = ProcessingRoute.OCR_THEN_LLM,
            usingLocalOcr = false,
            trustAllHttpsCertificates = false,
            webSearchSettings = WebSearchSettings()
        )
    }
}

private fun `fun`.kirari.llm.core.ChatMessage.text(): String {
    return requireNotNull(content).jsonArray
        .first { it.jsonObject["type"]?.jsonPrimitive?.content == "input_text" }
        .jsonObject.getValue("text").jsonPrimitive.content
}
