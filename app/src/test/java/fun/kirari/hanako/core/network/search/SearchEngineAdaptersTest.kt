package `fun`.kirari.hanako.core.network.search

import `fun`.kirari.hanako.core.network.NetworkClientProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineAdaptersTest {

    @Test
    fun tavilyAdapter_postsPayloadAndParsesResults() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "results": [
                        {"title": "Result 1", "content": "Snippet 1"},
                        {"title": "Result 2", "content": "Snippet 2"}
                      ]
                    }
                    """.trimIndent()
                )
            )

            val adapter = TavilySearchAdapter(NetworkClientProvider())
            val response = adapter.search(
                SearchRequest(
                    apiKey = "key",
                    baseUrl = server.url("/search").toString(),
                    query = "hanako",
                    maxResults = 2
                )
            )

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertTrue(request.body.readUtf8().contains(""""query":"hanako""""))
            assertEquals(2, response.hits.size)
            assertEquals("Result 1", response.hits[0].title)
            assertEquals("Snippet 2", response.hits[1].content)
        }
    }

    @Test
    fun braveAdapter_buildsQueryParametersAndHeaders() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "web": {
                        "results": [
                          {"title": "Brave 1", "description": "Desc 1"}
                        ]
                      }
                    }
                    """.trimIndent()
                )
            )

            val adapter = BraveSearchAdapter(NetworkClientProvider())
            val response = adapter.search(
                SearchRequest(
                    apiKey = "brave-key",
                    baseUrl = server.url("/res/v1/web/search").toString(),
                    query = "latest",
                    maxResults = 5
                )
            )

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("brave-key", request.getHeader("X-Subscription-Token"))
            assertTrue(request.requestUrl!!.queryParameterNames.contains("q"))
            assertEquals("latest", request.requestUrl!!.queryParameter("q"))
            assertEquals("5", request.requestUrl!!.queryParameter("count"))
            assertEquals(listOf(SearchHit("Brave 1", "Desc 1")), response.hits)
        }
    }

    @Test
    fun serperAdapter_mapsHttpErrorsToUserFacingMessage() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setBody("""{"message":"rate limit"}"""))

            val adapter = SerperSearchAdapter(NetworkClientProvider())
            val response = adapter.search(
                SearchRequest(
                    apiKey = "serper-key",
                    baseUrl = server.url("/search").toString(),
                    query = "news",
                    maxResults = 3
                )
            )

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("serper-key", request.getHeader("X-API-KEY"))
            assertEquals(429, response.errorCode)
            assertEquals("请求频率超限，请稍后再试", response.errorMessage)
            assertTrue(response.hits.isEmpty())
        }
    }

    @Test
    fun customAdapter_reusesTavilyCompatibleParsing() = runBlocking {
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

            val adapter = CustomSearchAdapter(NetworkClientProvider())
            val response = adapter.search(
                SearchRequest(
                    apiKey = "custom-key",
                    baseUrl = server.url("/custom").toString(),
                    query = "query",
                    maxResults = 1
                )
            )

            assertEquals(listOf(SearchHit("Custom", "Compatible")), response.hits)
        }
    }
}
