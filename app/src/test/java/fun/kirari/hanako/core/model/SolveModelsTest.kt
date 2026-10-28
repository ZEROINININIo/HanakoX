package `fun`.kirari.hanako.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolveModelsTest {

    @Test
    fun displayedAssistantVersions_readsLegacyAssistantText() {
        val legacyTurn = FollowUpTurn(
            userText = "question",
            assistantText = "legacy answer",
            completed = true
        )

        assertEquals(listOf("legacy answer"), legacyTurn.displayedAssistantVersions().map { it.text })
        assertEquals("legacy answer", legacyTurn.latestAssistantText())
    }

    @Test
    fun committedAssistantVersion_appendsStreamingTextToInheritedVersions() {
        val pendingRetry = FollowUpTurn(
            userText = "question",
            pendingAssistantText = "new answer",
            assistantVersions = listOf(AnswerVersion("old answer"))
        )

        val committed = pendingRetry.withCommittedAssistantVersion()

        assertEquals(listOf("old answer", "new answer"), committed.assistantVersions.map { it.text })
        assertEquals("", committed.pendingAssistantText)
        assertTrue(committed.completed)
        assertEquals(null, committed.errorMessage)
    }

    @Test
    fun assistantError_clearsTransientDraftAndPreservesCommittedVersions() {
        val streaming = FollowUpTurn(
            userText = "question",
            assistantVersions = listOf(AnswerVersion("old answer")),
            pendingAssistantText = "partial answer"
        )

        val failed = streaming.withAssistantError("request failed")

        assertEquals("", failed.pendingAssistantText)
        assertEquals(listOf("old answer"), failed.assistantVersions.map { it.text })
        assertTrue(failed.completed)
        assertEquals("request failed", failed.errorMessage)
    }
}
