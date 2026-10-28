package `fun`.kirari.llm.core

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAdaptersRegressionTest {

    @Test
    fun responses_stream_mergesArgumentDeltas_intoSingleToolCall_andBuildsPayload() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        sseBody(
                            "response.output_text.delta" to """{"delta":"先搜索"}""",
                            "response.output_item.added" to """{"item":{"id":"item_1","type":"function_call","call_id":"call_1","name":"web_search","arguments":""}}""",
                            "response.function_call_arguments.delta" to """{"item_id":"item_1","delta":"{\"query\":\"han"}""",
                            "response.function_call_arguments.delta" to """{"item_id":"item_1","delta":"ako\"}"}""",
                            "response.function_call_arguments.done" to """{"item_id":"item_1","name":"web_search","arguments":"{\"query\":\"hanako\"}"}""",
                            null to "[DONE]"
                        )
                    )
            )

            val client = LlmClient()
            val events = client.stream(
                StreamRequest(
                    provider = ProviderConfig(
                        kind = ProviderKind.OPENAI_RESPONSES,
                        baseUrl = server.url("/v1").toString(),
                        apiKey = "test-key"
                    ),
                    model = "gpt-test",
                    messages = listOf(
                        ChatMessage(
                            role = "system",
                            content = buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "input_text")
                                    put("text", "system")
                                })
                            }
                        ),
                        ChatMessage(
                            role = "tool",
                            toolCallId = "",
                            content = kotlinx.serialization.json.JsonPrimitive("missing id")
                        )
                    ),
                    tools = listOf(ToolRegistry.WEB_SEARCH_TOOL),
                    firstDeltaTimeoutMillis = 1_000L,
                    trustAllHttpsCertificates = false
                )
            ).toList()

            assertEquals("先搜索", events.filterIsInstance<LlmEvent.TextDelta>().joinToString("") { it.text })
            val toolCall = events.filterIsInstance<LlmEvent.ToolCall>().single()
            assertEquals("call_1", toolCall.id)
            assertEquals("web_search", toolCall.name)
            assertEquals("hanako", toolCall.arguments["query"]?.toString()?.trim('"'))
            assertTrue(events.last() is LlmEvent.Done)

            val requestBody = server.takeRequest().body.readUtf8()
            assertTrue(requestBody.contains("\"tools\""))
            assertTrue(requestBody.contains("\"function_call_output\"").not())
            assertTrue(requestBody.contains("[tool_output_missing_call_id] missing id"))
        }
    }

    @Test
    fun anthropic_stream_emitsTextAndToolCall_fromSplitEvents() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        sseBody(
                            "content_block_start" to """{"index":0,"content_block":{"type":"text","text":""}}""",
                            "content_block_delta" to """{"index":0,"delta":{"type":"text_delta","text":"Hello "}}""",
                            "content_block_start" to """{"index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"web_search","input":{}}}""",
                            "content_block_delta" to """{"index":1,"delta":{"type":"input_json_delta","partial_json":"{\"query\":\"ha"}}""",
                            "content_block_delta" to """{"index":1,"delta":{"type":"input_json_delta","partial_json":"nako\"}"}}""",
                            null to "[DONE]"
                        )
                    )
            )

            val client = LlmClient()
            val events = client.stream(
                StreamRequest(
                    provider = ProviderConfig(
                        kind = ProviderKind.ANTHROPIC,
                        baseUrl = server.url("/v1").toString(),
                        apiKey = "test-key"
                    ),
                    model = "claude-test",
                    systemPrompt = "system",
                    userPrompt = "user",
                    tools = listOf(ToolRegistry.WEB_SEARCH_TOOL),
                    firstDeltaTimeoutMillis = 1_000L,
                    trustAllHttpsCertificates = false
                )
            ).toList()

            assertEquals("Hello ", events.filterIsInstance<LlmEvent.TextDelta>().joinToString("") { it.text })
            val toolCall = events.filterIsInstance<LlmEvent.ToolCall>().single()
            assertEquals("toolu_1", toolCall.id)
            assertEquals("web_search", toolCall.name)
            assertEquals("hanako", toolCall.arguments["query"]?.toString()?.trim('"'))

            val request = server.takeRequest()
            val requestBody = request.body.readUtf8()
            assertTrue(requestBody.contains("\"tools\""))
            assertEquals("2023-06-01", request.getHeader("anthropic-version"))
            assertTrue(requestBody.contains("\"messages\""))
        }
    }

    @Test
    fun google_stream_emitsTextAndFunctionCall_andSendsToolConfig() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        sseBody(
                            null to """{"candidates":[{"content":{"parts":[{"text":"Part 1 "},{"text":"Part 2"},{"functionCall":{"name":"web_search","args":{"query":"hanako"}}}]}}]}"""
                        )
                    )
            )

            val client = LlmClient()
            val events = client.stream(
                StreamRequest(
                    provider = ProviderConfig(
                        kind = ProviderKind.GOOGLE,
                        baseUrl = server.url("/v1beta").toString(),
                        apiKey = "test-key"
                    ),
                    model = "gemini-test",
                    systemPrompt = "system",
                    userPrompt = "user",
                    tools = listOf(ToolRegistry.WEB_SEARCH_TOOL),
                    firstDeltaTimeoutMillis = 1_000L,
                    trustAllHttpsCertificates = false
                )
            ).toList()

            assertEquals("Part 1 Part 2", events.filterIsInstance<LlmEvent.TextDelta>().joinToString("") { it.text })
            val toolCall = events.filterIsInstance<LlmEvent.ToolCall>().single()
            assertEquals(null, toolCall.id)
            assertEquals("web_search", toolCall.name)
            assertEquals("hanako", toolCall.arguments["query"]?.toString()?.trim('"'))
            assertTrue(events.last() is LlmEvent.Done)

            val request = server.takeRequest()
            assertTrue(request.path!!.contains(":streamGenerateContent?alt=sse"))
            val requestBody = request.body.readUtf8()
            assertTrue(requestBody.contains("\"toolConfig\""))
            assertTrue(requestBody.contains("\"functionCallingConfig\""))
            assertTrue(requestBody.contains("\"mode\":\"AUTO\""))
        }
    }

    @Test
    fun openAiChat_stream_mergesSplitToolArguments_beforeEmittingToolCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        sseBody(
                            null to """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"web_search","arguments":"{\"query\":\"ha"}}]},"index":0}]}""",
                            null to """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"nako\"}"}}]},"index":0}]}""",
                            null to "[DONE]"
                        )
                    )
            )

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
                    tools = listOf(ToolRegistry.WEB_SEARCH_TOOL),
                    firstDeltaTimeoutMillis = 1_000L,
                    trustAllHttpsCertificates = false
                )
            ).toList()

            val toolCall = events.filterIsInstance<LlmEvent.ToolCall>().single()
            assertEquals("call_1", toolCall.id)
            assertEquals("hanako", toolCall.arguments["query"]?.toString()?.trim('"'))
        }
    }

    private fun sseBody(vararg events: Pair<String?, String>): String {
        return buildString {
            events.forEach { (type, data) ->
                if (type != null) {
                    append("event: ").append(type).append('\n')
                }
                append("data: ").append(data).append('\n').append('\n')
            }
        }
    }
}
