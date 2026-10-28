package `fun`.kirari.llm.core

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiChatAdapterTest {
    @Test
    fun stream_preservesWhitespaceOnlyDeltas() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"role":"assistant"},"index":0}]}

                    data: {"choices":[{"delta":{"content":"题目内容："},"index":0}]}

                    data: {"choices":[{"delta":{"content":"\n\n"},"index":0}]}

                    data: {"choices":[{"delta":{"content":"-"},"index":0}]}

                    data: {"choices":[{"delta":{"content":" "},"index":0}]}

                    data: {"choices":[{"delta":{"content":"答案"},"index":0}]}

                    data: [DONE]

                    """.trimIndent()
                )
        )
        server.start()
        try {
            val client = LlmClient()
            val events = client.stream(
                StreamRequest(
                    provider = ProviderConfig(
                        kind = ProviderKind.OPENAI_COMPATIBLE,
                        baseUrl = server.url("/v1").toString(),
                        apiKey = "test-key"
                    ),
                    model = "test-model",
                    systemPrompt = "system",
                    userPrompt = "user",
                    firstDeltaTimeoutMillis = 1_000L,
                    trustAllHttpsCertificates = false
                )
            ).toList()

            val text = events.filterIsInstance<LlmEvent.TextDelta>().joinToString(separator = "") { it.text }
            assertEquals("题目内容：\n\n- 答案", text)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun stream_emitsToolCallId_andSendsMessagePayload() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"web_search","arguments":"{\"query\":\"hanako\"}"}}]},"index":0}]}

                    data: [DONE]

                    """.trimIndent()
                )
        )
        server.start()
        try {
            val client = LlmClient()
            val events = client.stream(
                StreamRequest(
                    provider = ProviderConfig(
                        kind = ProviderKind.OPENAI_COMPATIBLE,
                        baseUrl = server.url("/v1").toString(),
                        apiKey = "test-key"
                    ),
                    model = "test-model",
                    messages = listOf(
                        ChatMessage(
                            role = "user",
                            content = buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "input_text")
                                    put("text", "lookup")
                                })
                            }
                        ),
                        ChatMessage(
                            role = "assistant",
                            content = buildJsonArray { },
                            toolCalls = listOf(
                                ChatToolCall(
                                    id = "call_prev",
                                    function = ChatToolFunction("lookup", "{\"q\":\"old\"}")
                                )
                            )
                        ),
                        ChatMessage(
                            role = "tool",
                            toolCallId = "call_prev",
                            content = JsonPrimitive("result")
                        )
                    ),
                    tools = listOf(ToolRegistry.WEB_SEARCH_TOOL),
                    firstDeltaTimeoutMillis = 1_000L,
                    trustAllHttpsCertificates = false
                )
            ).toList()

            val toolCall = events.filterIsInstance<LlmEvent.ToolCall>().single()
            assertEquals("call_1", toolCall.id)
            assertEquals("web_search", toolCall.name)
            assertEquals("hanako", toolCall.arguments["query"]?.toString()?.trim('"'))

            val requestBody = server.takeRequest().body.readUtf8()
            assertNotNull(requestBody)
            assertTrue(requestBody.contains("\"messages\""))
            assertTrue(requestBody.contains("\"tool_call_id\":\"call_prev\""))
            assertTrue(requestBody.contains("\"tool_calls\""))
        } finally {
            server.shutdown()
        }
    }
}
