package `fun`.kirari.hanako.core.network.search

import `fun`.kirari.hanako.core.data.SearchProviderConfig
import `fun`.kirari.hanako.core.data.SearchProviderKind
import `fun`.kirari.hanako.core.network.NetworkClientProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchClientImplTest {

    @Test
    fun search_usesTavilyAdapterShape() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "results": [
                        {"title": "Tavily", "content": "Answer"}
                      ]
                    }
                    """.trimIndent()
                )
            )

            val client = SearchClientImpl(NetworkClientProvider())
            val response = client.search(
                config = SearchProviderConfig(
                    kind = SearchProviderKind.TAVILY,
                    baseUrl = server.url("/search").toString(),
                    apiKey = "key"
                ),
                query = "hanako",
                maxResults = 2
            )

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertTrue(request.body.readUtf8().contains(""""max_results":2"""))
            assertEquals(listOf(SearchHit("Tavily", "Answer")), response.hits)
        }
    }

    @Test
    fun search_usesBraveAdapterShape() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "web": {
                        "results": [
                          {"title": "Brave", "description": "Result"}
                        ]
                      }
                    }
                    """.trimIndent()
                )
            )

            val client = SearchClientImpl(NetworkClientProvider())
            val response = client.search(
                config = SearchProviderConfig(
                    kind = SearchProviderKind.BRAVE,
                    baseUrl = server.url("/web/search").toString(),
                    apiKey = "brave-key"
                ),
                query = "latest",
                maxResults = 4
            )

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("brave-key", request.getHeader("X-Subscription-Token"))
            assertEquals("latest", request.requestUrl?.queryParameter("q"))
            assertEquals("4", request.requestUrl?.queryParameter("count"))
            assertEquals(listOf(SearchHit("Brave", "Result")), response.hits)
        }
    }

    @Test
    fun search_usesSerperAdapterShape() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "organic": [
                        {"title": "Serper", "snippet": "Snippet"}
                      ]
                    }
                    """.trimIndent()
                )
            )

            val client = SearchClientImpl(NetworkClientProvider())
            val response = client.search(
                config = SearchProviderConfig(
                    kind = SearchProviderKind.SERPER,
                    baseUrl = server.url("/search").toString(),
                    apiKey = "serper-key"
                ),
                query = "query",
                maxResults = 1
            )

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("serper-key", request.getHeader("X-API-KEY"))
            assertTrue(request.body.readUtf8().contains(""""q":"query""""))
            assertEquals(listOf(SearchHit("Serper", "Snippet")), response.hits)
        }
    }

    @Test
    fun search_usesCustomTavilyCompatibleAdapterShape() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "results": [
                        {"title": "Custom", "content": "Compatible"}
                      ]
                    }
                    """.trimIndent()
                )
            )

            val client = SearchClientImpl(NetworkClientProvider())
            val response = client.search(
                config = SearchProviderConfig(
                    kind = SearchProviderKind.CUSTOM,
                    baseUrl = server.url("/custom").toString(),
                    apiKey = "custom-key"
                ),
                query = "query",
                maxResults = 3
            )

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals(listOf(SearchHit("Custom", "Compatible")), response.hits)
        }
    }
}
