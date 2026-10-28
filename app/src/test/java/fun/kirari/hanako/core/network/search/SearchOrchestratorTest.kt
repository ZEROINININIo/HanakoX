package `fun`.kirari.hanako.core.network.search

import `fun`.kirari.hanako.core.data.SearchProviderConfig
import `fun`.kirari.hanako.core.data.WebSearchSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchOrchestratorTest {

    @Test
    fun execute_skipsWhenDisabled() = runBlocking {
        val client = RecordingSearchClient()
        val orchestrator = SearchOrchestrator(client)

        val outcome = orchestrator.execute(
            SearchContext(
                query = "latest news",
                settings = WebSearchSettings(enabled = false),
                trustAllHttps = false,
                isAutomation = false
            )
        )

        assertFalse(outcome.performed)
        assertEquals(SearchSkipReason.DISABLED, outcome.skipReason)
        assertEquals(0, client.callCount)
    }

    @Test
    fun execute_skipsAutomationWhenDisabledForAutomation() = runBlocking {
        val client = RecordingSearchClient()
        val orchestrator = SearchOrchestrator(client)

        val outcome = orchestrator.execute(
            SearchContext(
                query = "query",
                settings = WebSearchSettings(
                    enabled = true,
                    provider = SearchProviderConfig(baseUrl = "https://example.com", apiKey = "k"),
                    automationAlsoSearch = false
                ),
                trustAllHttps = false,
                isAutomation = true
            )
        )

        assertFalse(outcome.performed)
        assertEquals(SearchSkipReason.AUTOMATION_DISABLED, outcome.skipReason)
        assertEquals(0, client.callCount)
    }

    @Test
    fun execute_skipsWhenApiKeyMissing() = runBlocking {
        val client = RecordingSearchClient()
        val orchestrator = SearchOrchestrator(client)

        val outcome = orchestrator.execute(
            SearchContext(
                query = "query",
                settings = WebSearchSettings(
                    enabled = true,
                    provider = SearchProviderConfig(baseUrl = "https://example.com", apiKey = "")
                ),
                trustAllHttps = false,
                isAutomation = false
            )
        )

        assertFalse(outcome.performed)
        assertEquals(SearchSkipReason.API_KEY_MISSING, outcome.skipReason)
        assertEquals(0, client.callCount)
    }

    @Test
    fun execute_skipsWhenQueryIsBlankAfterTrim() = runBlocking {
        val client = RecordingSearchClient()
        val orchestrator = SearchOrchestrator(client)

        val outcome = orchestrator.execute(
            SearchContext(
                query = "   ",
                settings = WebSearchSettings(
                    enabled = true,
                    provider = SearchProviderConfig(baseUrl = "https://example.com", apiKey = "k")
                ),
                trustAllHttps = false,
                isAutomation = false
            )
        )

        assertFalse(outcome.performed)
        assertEquals(SearchSkipReason.LLM_NO_KEYWORDS, outcome.skipReason)
        assertEquals(0, client.callCount)
    }

    @Test
    fun execute_returnsApiErrorOutcomeWhenClientThrows() = runBlocking {
        val client = RecordingSearchClient(exception = IllegalStateException("boom"))
        val orchestrator = SearchOrchestrator(client)

        val outcome = orchestrator.execute(validContext(query = "question"))

        assertTrue(outcome.performed)
        assertEquals(SearchSkipReason.SEARCH_API_ERROR, outcome.skipReason)
        assertNull(outcome.formattedText)
        assertEquals("question", outcome.keywords)
    }

    @Test
    fun execute_returnsApiErrorOutcomeWhenClientReturnsErrorCode() = runBlocking {
        val client = RecordingSearchClient(
            response = SearchResponse(hits = emptyList(), errorCode = 429, errorMessage = "rate limit")
        )
        val orchestrator = SearchOrchestrator(client)

        val outcome = orchestrator.execute(validContext(query = "question"))

        assertTrue(outcome.performed)
        assertEquals(SearchSkipReason.SEARCH_API_ERROR, outcome.skipReason)
        assertNull(outcome.formattedText)
    }

    @Test
    fun execute_returnsNoResultsOutcomeWhenSearchIsEmpty() = runBlocking {
        val client = RecordingSearchClient(response = SearchResponse(hits = emptyList()))
        val orchestrator = SearchOrchestrator(client)

        val outcome = orchestrator.execute(validContext(query = "question"))

        assertTrue(outcome.performed)
        assertEquals(SearchSkipReason.SEARCH_NO_RESULTS, outcome.skipReason)
        assertNull(outcome.formattedText)
        assertEquals("question", outcome.keywords)
    }

    @Test
    fun execute_formatsSearchResultsAndPassesParametersToClient() = runBlocking {
        val client = RecordingSearchClient(
            response = SearchResponse(
                hits = listOf(
                    SearchHit(title = "T1", content = "C1"),
                    SearchHit(title = "T2", content = "C2")
                )
            )
        )
        val orchestrator = SearchOrchestrator(client)

        val outcome = orchestrator.execute(validContext(query = "  current events  ", trustAllHttps = true))

        assertTrue(outcome.performed)
        assertEquals(null, outcome.skipReason)
        assertEquals("current events", outcome.keywords)
        assertEquals(1, client.callCount)
        assertEquals("current events", client.lastQuery)
        assertEquals(true, client.lastTrustAllHttps)
        assertEquals(3, client.lastMaxResults)
        assertEquals(
            """
                以下是网络搜索结果（供参考，可能不准确）：
                [1] T1
                C1

                [2] T2
                C2
            """.trimIndent(),
            outcome.formattedText?.trimEnd()
        )
    }

    private fun validContext(query: String, trustAllHttps: Boolean = false): SearchContext {
        return SearchContext(
            query = query,
            settings = WebSearchSettings(
                enabled = true,
                provider = SearchProviderConfig(baseUrl = "https://example.com", apiKey = "k"),
                maxResults = 3,
                automationAlsoSearch = true
            ),
            trustAllHttps = trustAllHttps,
            isAutomation = false
        )
    }

    private class RecordingSearchClient(
        private val response: SearchResponse = SearchResponse(emptyList()),
        private val exception: Exception? = null
    ) : SearchClient {
        var callCount: Int = 0
        var lastQuery: String? = null
        var lastMaxResults: Int? = null
        var lastTrustAllHttps: Boolean? = null

        override suspend fun search(
            config: SearchProviderConfig,
            query: String,
            maxResults: Int,
            trustAllHttps: Boolean
        ): SearchResponse {
            callCount += 1
            lastQuery = query
            lastMaxResults = maxResults
            lastTrustAllHttps = trustAllHttps
            exception?.let { throw it }
            return response
        }
    }
}
