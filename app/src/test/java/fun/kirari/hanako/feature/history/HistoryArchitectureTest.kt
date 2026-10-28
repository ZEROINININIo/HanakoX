package `fun`.kirari.hanako.feature.history

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryArchitectureTest {
    private fun source(path: String): String = listOf(File(path), File("../$path"))
        .first { it.exists() }
        .readText()

    @Test
    fun historyUiDoesNotBypassApplicationStorageOrNetwork() {
        val root = "app/src/main/java/fun/kirari/hanako/feature/history"
        val uiDirectory = listOf(File("$root/ui"), File("../$root/ui")).first { it.exists() }
        val uiSources = uiDirectory.walkTopDown().filter { it.extension == "kt" }.toList()
            .joinToString("\n") { it.readText() }

        assertFalse(uiSources.contains("SettingsStore"))
        assertFalse(uiSources.contains("UnifiedLLMClient"))
        assertFalse(uiSources.contains("@Query"))
    }

    @Test
    fun quotePromptBuilderStaysInWorkflowBoundary() {
        val workflow = source("app/src/main/java/fun/kirari/hanako/solve/workflow/ConversationWorkflow.kt")
        assertTrue(workflow.contains("buildQuotedPrompt"))
        assertFalse(workflow.contains("HistoryChatComposer"))
        assertFalse(workflow.contains("HistoryQuoteUi"))
    }
}
