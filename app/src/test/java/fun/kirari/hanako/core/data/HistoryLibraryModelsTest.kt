package `fun`.kirari.hanako.core.data

import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryLibraryModelsTest {
    @Test
    fun newResultHasNoGroupsAndUsesAssistantTitle() {
        val result = ProcessingResult(assistantName = "助手", route = ProcessingRoute.OCR_THEN_LLM)
        val settings = AppSettings(history = listOf(result)).normalize()

        val metadata = settings.historyMetadataFor(result)
        assertTrue(metadata.groupIds.isEmpty())
        assertEquals("助手", settings.historyDisplayTitle(result))
    }

    @Test
    fun metadataDropsDeletedGroupsAndUnknownRecords() {
        val result = ProcessingResult(assistantName = "助手", route = ProcessingRoute.OCR_THEN_LLM)
        val settings = AppSettings(
            history = listOf(result),
            historyGroups = listOf(HistoryGroup(id = "a", name = "重点")),
            historyMetadata = listOf(
                HistoryRecordMetadata(result.id, groupIds = listOf("a", "missing"), lastActivityAtMillis = 1),
                HistoryRecordMetadata("missing-record", groupIds = listOf("a"), lastActivityAtMillis = 1)
            )
        ).normalize()

        assertEquals(listOf("a"), settings.historyMetadataFor(result).groupIds)
        assertEquals(1, settings.historyMetadata.size)
    }
}
