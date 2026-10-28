package `fun`.kirari.llm.core

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType

internal class OpenAiChatAdapter(
    private val sseClient: SseStreamClient,
    private val json: Json,
    private val logger: LlmLogger = NoopLlmLogger
) : ProviderAdapter {
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val tag = "KirariLlmOpenAI"

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        val messages = request.effectiveMessages()
        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("messages", openAiMessages(messages))
            request.tools?.let { put("tools", ToolRegistry.formatForProvider(it, request.provider.kind)) }
        }
        if (logger.isVerboseEnabled) {
            logger.v(tag, "chat request payload=${payload.toString().sanitizeForLog()}")
        }

        val toolCalls = mutableMapOf<Int, PendingToolCall>()
        var textDeltaCount = 0

        sseClient.stream(
            request = baseRequest(request.provider, "${request.provider.baseUrl.trimEnd('/')}/chat/completions", payload, json, mediaType),
            firstDeltaTimeoutMillis = request.firstDeltaTimeoutMillis,
            onEvent = { _, _, _, data ->
                if (data == "[DONE]") {
                    SseStreamClient.StreamEventResult(done = true)
                } else {
                    val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                        ?: return@stream null
                    val choice = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject
                        ?: return@stream null
                    val delta = choice["delta"]?.jsonObject ?: return@stream null

                    val textDelta = extractOpenAiContent(delta["content"])
                    if (textDelta.isNotEmpty()) {
                        textDeltaCount += 1
                        if (textDeltaCount <= 20 || textDelta.any(Char::isWhitespace)) {
                            logger.d(tag, "chat textDelta#$textDeltaCount len=${textDelta.length} text=${textDelta.visibleWhitespaceForLog()}")
                        }
                        trySend(LlmEvent.TextDelta(textDelta))
                    }

                    (delta["tool_calls"] as? JsonArray)?.forEach { item ->
                        val toolCall = item.jsonObject
                        val index = toolCall["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                        val function = toolCall["function"]?.jsonObject ?: return@forEach
                        val tc = toolCalls.getOrPut(index) { PendingToolCall() }
                        toolCall["id"]?.jsonPrimitive?.contentOrNull?.let { tc.id = it }
                        function["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                            tc.name = it
                        }
                        tc.arguments.append(function["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    }

                    SseStreamClient.StreamEventResult(
                        delta = textDelta.ifEmpty { null },
                        activity = delta["tool_calls"] is JsonArray
                    )
                }
            },
            onDelta = {}
        )

        for (tc in toolCalls.values) {
            val name = tc.name ?: continue
            val args = runCatching { json.parseToJsonElement(tc.arguments.toString()).jsonObject }.getOrNull()
                ?: continue
            trySend(LlmEvent.ToolCall(tc.id, name, args))
        }
        trySend(LlmEvent.Done)
        close()

        awaitClose()
    }
}

internal class OpenAiResponsesAdapter(
    private val sseClient: SseStreamClient,
    private val json: Json,
    private val logger: LlmLogger = NoopLlmLogger
) : ProviderAdapter {
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val tag = "KirariLlmResponses"

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        val messages = request.effectiveMessages()
        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            val instructions = responsesInstructions(messages)
            if (instructions.isNotBlank()) {
                put("instructions", instructions)
            }
            request.tools?.let {
                put("tools", ToolRegistry.formatForProvider(it, request.provider.kind))
            }
            put("input", responsesInput(messages))
        }
        if (logger.isVerboseEnabled) {
            logger.v(tag, "responses request payload=${payload.toString().sanitizeForLog()}")
        }

        val toolCalls = linkedMapOf<String, PendingToolCall>()

        sseClient.stream(
            request = baseRequest(request.provider, "${request.provider.baseUrl.trimEnd('/')}/responses", payload, json, mediaType),
            firstDeltaTimeoutMillis = request.firstDeltaTimeoutMillis,
            onEvent = { _, type, _, data ->
                if (data == "[DONE]") return@stream SseStreamClient.StreamEventResult(done = true)
                val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                    ?: return@stream null
                when (type) {
                    "response.output_text.delta" -> {
                        val delta = root["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (delta.isNotEmpty()) {
                            trySend(LlmEvent.TextDelta(delta))
                        }
                        SseStreamClient.StreamEventResult(delta = delta)
                    }

                    "response.function_call_arguments.delta" -> {
                        val itemId = root["item_id"]?.jsonPrimitive?.contentOrNull ?: return@stream null
                        val tc = toolCalls.getOrPut(itemId) { PendingToolCall() }
                        tc.arguments.append(root["delta"]?.jsonPrimitive?.contentOrNull.orEmpty())
                        SseStreamClient.StreamEventResult(activity = true)
                    }

                    "response.output_item.added", "response.output_item.done" -> {
                        val item = root["item"]?.jsonObject ?: return@stream null
                        if (item["type"]?.jsonPrimitive?.contentOrNull == "function_call") {
                            val itemId = item["id"]?.jsonPrimitive?.contentOrNull ?: return@stream null
                            val tc = toolCalls.getOrPut(itemId) { PendingToolCall() }
                            tc.id = item["call_id"]?.jsonPrimitive?.contentOrNull ?: itemId
                            tc.name = item["name"]?.jsonPrimitive?.contentOrNull
                            item["arguments"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let {
                                tc.arguments.clear()
                                tc.arguments.append(it)
                            }
                            SseStreamClient.StreamEventResult(activity = true)
                        } else {
                            null
                        }
                    }

                    "response.function_call_arguments.done" -> {
                        val itemId = root["item_id"]?.jsonPrimitive?.contentOrNull ?: return@stream null
                        val tc = toolCalls.getOrPut(itemId) { PendingToolCall() }
                        root["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                            tc.name = it
                        }
                        root["arguments"]?.jsonPrimitive?.contentOrNull?.let {
                            tc.arguments.clear()
                            tc.arguments.append(it)
                        }
                        SseStreamClient.StreamEventResult(activity = true)
                    }

                    else -> null
                }
            },
            onDelta = {}
        )

        for (tc in toolCalls.values) {
            val name = tc.name ?: continue
            val args = runCatching { json.parseToJsonElement(tc.arguments.toString()).jsonObject }.getOrNull()
                ?: continue
            trySend(LlmEvent.ToolCall(tc.id, name, args))
        }
        trySend(LlmEvent.Done)
        close()

        awaitClose()
    }
}

internal class AnthropicAdapter(
    private val sseClient: SseStreamClient,
    private val json: Json,
    private val logger: LlmLogger = NoopLlmLogger
) : ProviderAdapter {
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val tag = "KirariLlmAnthropic"

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        val messages = request.effectiveMessages()
        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("max_tokens", 4096)
            anthropicSystem(messages)?.let { put("system", it) }
            request.tools?.let {
                put("tools", ToolRegistry.formatForProvider(it, request.provider.kind))
            }
            put("messages", anthropicMessages(messages))
        }
        if (logger.isVerboseEnabled) {
            logger.v(tag, "anthropic request payload=${payload.toString().sanitizeForLog()}")
        }

        val toolCallsByIndex = linkedMapOf<Int, PendingToolCall>()

        sseClient.stream(
            request = baseRequest(
                request.provider,
                "${request.provider.baseUrl.trimEnd('/')}/messages",
                payload,
                json,
                mediaType,
                headers = mapOf("anthropic-version" to "2023-06-01")
            ),
            firstDeltaTimeoutMillis = request.firstDeltaTimeoutMillis,
            onEvent = { _, type, _, data ->
                if (data == "[DONE]") return@stream SseStreamClient.StreamEventResult(done = true)
                val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                    ?: return@stream null
                when (type) {
                    "content_block_start" -> {
                        val index = root["index"]?.jsonPrimitive?.intOrNull ?: return@stream null
                        val block = root["content_block"]?.jsonObject ?: return@stream null
                        if (block["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
                            val tc = toolCallsByIndex.getOrPut(index) { PendingToolCall() }
                            tc.id = block["id"]?.jsonPrimitive?.contentOrNull
                            tc.name = block["name"]?.jsonPrimitive?.contentOrNull
                            block["input"]?.jsonObject?.takeIf { it.isNotEmpty() }?.let {
                                tc.arguments.clear()
                                tc.arguments.append(json.encodeToString(JsonObject.serializer(), it))
                            }
                        }
                        null
                    }

                    "content_block_delta" -> {
                        val index = root["index"]?.jsonPrimitive?.intOrNull ?: return@stream null
                        val delta = root["delta"]?.jsonObject ?: return@stream null
                        when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                            "text_delta" -> {
                                val text = delta["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                if (text.isNotEmpty()) {
                                    trySend(LlmEvent.TextDelta(text))
                                }
                                SseStreamClient.StreamEventResult(delta = text)
                            }

                            "input_json_delta" -> {
                                val tc = toolCallsByIndex.getOrPut(index) { PendingToolCall() }
                                tc.arguments.append(delta["partial_json"]?.jsonPrimitive?.contentOrNull.orEmpty())
                                SseStreamClient.StreamEventResult(activity = true)
                            }

                            else -> null
                        }
                    }

                    else -> null
                }
            },
            onDelta = {}
        )

        for (tc in toolCallsByIndex.values) {
            val name = tc.name ?: continue
            val args = runCatching { json.parseToJsonElement(tc.arguments.toString()).jsonObject }.getOrNull()
                ?: continue
            trySend(LlmEvent.ToolCall(tc.id, name, args))
        }
        trySend(LlmEvent.Done)
        close()

        awaitClose()
    }
}

internal class GoogleAdapter(
    private val sseClient: SseStreamClient,
    private val json: Json,
    private val logger: LlmLogger = NoopLlmLogger
) : ProviderAdapter {
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val tag = "KirariLlmGoogle"

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        val messages = request.effectiveMessages()
        val payload = buildJsonObject {
            googleSystem(messages)?.let { put("systemInstruction", it) }
            put("contents", googleContents(messages))
            request.tools?.let { tools ->
                put("tools", buildJsonArray {
                    add(ToolRegistry.formatForProvider(tools, request.provider.kind))
                })
                put("toolConfig", buildJsonObject {
                    put("functionCallingConfig", buildJsonObject {
                        put("mode", "AUTO")
                    })
                })
            }
        }
        if (logger.isVerboseEnabled) {
            logger.v(tag, "google request payload=${payload.toString().sanitizeForLog()}")
        }
        if (json !== Json.Default && false) Unit

        val url = "${request.provider.baseUrl.trimEnd('/')}/models/${request.model}:streamGenerateContent?alt=sse"
        var toolName: String? = null
        var toolArgs: JsonObject? = null

        sseClient.stream(
            request = baseRequest(request.provider, url, payload, json, mediaType),
            firstDeltaTimeoutMillis = request.firstDeltaTimeoutMillis,
            onEvent = { _, _, _, data ->
                val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                    ?: return@stream null
                val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return@stream null
                val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
                var deltaText = ""
                parts.forEach { part ->
                    val obj = part.jsonObject
                    obj["text"]?.jsonPrimitive?.contentOrNull?.let { deltaText += it }
                    obj["functionCall"]?.jsonObject?.let { call ->
                        toolName = call["name"]?.jsonPrimitive?.contentOrNull
                        toolArgs = call["args"]?.jsonObject
                    }
                }
                if (deltaText.isNotEmpty()) {
                    trySend(LlmEvent.TextDelta(deltaText))
                }
                SseStreamClient.StreamEventResult(
                    delta = deltaText.ifEmpty { null },
                    activity = toolName != null && toolArgs != null
                )
            },
            onDelta = {}
        )

        val name = toolName
        val args = toolArgs
        if (name != null && args != null) {
            trySend(LlmEvent.ToolCall(null, name, args))
        }
        trySend(LlmEvent.Done)
        close()

        awaitClose()
    }
}

private fun openAiMessages(messages: List<ChatMessage>): JsonArray = buildJsonArray {
    messages.forEach { message ->
        add(buildJsonObject {
            put("role", message.role)
            message.toolCallId?.let { put("tool_call_id", it) }
            if (message.toolCalls.isNotEmpty()) {
                putJsonArray("tool_calls") {
                    message.toolCalls.forEach { call ->
                        add(buildJsonObject {
                            put("id", call.id)
                            put("type", call.type)
                            putJsonObject("function") {
                                put("name", call.function.name)
                                put("arguments", call.function.arguments)
                            }
                        })
                    }
                }
            }
            put("content", openAiContent(message))
        })
    }
}

private fun openAiContent(message: ChatMessage): JsonElement {
    val content = message.content ?: return JsonPrimitive("")
    if (content !is JsonArray) return content
    val parts = buildJsonArray {
        content.forEach { part ->
            val obj = part.jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "input_text", "output_text", "text" -> add(buildJsonObject {
                    put("type", "text")
                    put("text", obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                })
                "input_image", "image_url" -> add(buildJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") {
                        put("url", chatImageUrl(obj))
                        put("detail", "high")
                    }
                })
            }
        }
    }
    if (parts.size == 1) {
        val only = parts.first().jsonObject
        if (only["type"]?.jsonPrimitive?.contentOrNull == "text") {
            return JsonPrimitive(only["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }
    }
    return parts
}

private fun responsesInstructions(messages: List<ChatMessage>): String =
    messages.filter { it.role == "system" || it.role == "developer" }
        .joinToString("\n\n") { contentText(it.content).trim() }
        .trim()

private fun responsesInput(messages: List<ChatMessage>): JsonArray = buildJsonArray {
    messages.forEach { message ->
        when (message.role) {
            "system", "developer" -> Unit
            "tool", "function" -> {
                val callId = message.toolCallId.orEmpty()
                if (callId.isBlank()) {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", "[tool_output_missing_call_id] ${contentText(message.content)}")
                    })
                } else {
                    add(buildJsonObject {
                        put("type", "function_call_output")
                        put("call_id", callId)
                        put("output", contentText(message.content))
                    })
                }
            }
            "assistant" -> {
                add(buildJsonObject {
                    put("role", "assistant")
                    put("content", responsesContent(message.content, assistant = true))
                })
                message.toolCalls.forEach { call ->
                    add(buildJsonObject {
                        put("type", "function_call")
                        put("call_id", call.id)
                        put("name", call.function.name)
                        put("arguments", call.function.arguments)
                    })
                }
            }
            else -> {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", responsesContent(message.content, assistant = false))
                })
            }
        }
    }
}

private fun responsesContent(content: JsonElement?, assistant: Boolean): JsonElement {
    val textType = if (assistant) "output_text" else "input_text"
    val arr = when (content) {
        is JsonArray -> content
        is JsonNull, null -> JsonArray(emptyList())
        else -> JsonArray(listOf(buildJsonObject {
            put("type", textType)
            put("text", content.jsonPrimitive.contentOrNull.orEmpty())
        }))
    }
    return buildJsonArray {
        arr.forEach { part ->
            val obj = part.jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "input_text", "output_text", "text" -> add(buildJsonObject {
                    put("type", textType)
                    put("text", obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                })
                "input_image", "image_url" -> if (!assistant) {
                    add(buildJsonObject {
                        put("type", "input_image")
                        put("image_url", chatImageUrl(obj))
                    })
                }
            }
        }
    }
}

private fun anthropicSystem(messages: List<ChatMessage>): String? =
    messages.filter { it.role == "system" || it.role == "developer" }
        .joinToString("\n\n") { contentText(it.content).trim() }
        .trim()
        .ifBlank { null }

private fun anthropicMessages(messages: List<ChatMessage>): JsonArray = buildJsonArray {
    messages.forEach { message ->
        when (message.role) {
            "system", "developer" -> Unit
            "tool", "function" -> {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", message.toolCallId.orEmpty())
                            put("content", contentText(message.content))
                        })
                    })
                })
            }
            else -> {
                add(buildJsonObject {
                    put("role", if (message.role == "assistant") "assistant" else "user")
                    put("content", anthropicContent(message))
                })
            }
        }
    }
}

private fun anthropicContent(message: ChatMessage): JsonArray = buildJsonArray {
    (message.content as? JsonArray)?.forEach { part ->
        val obj = part.jsonObject
        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "input_text", "output_text", "text" -> add(buildJsonObject {
                put("type", "text")
                put("text", obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
            })
            "input_image", "image_url" -> if (message.role != "assistant") {
                add(buildJsonObject {
                    put("type", "image")
                    putJsonObject("source") {
                        put("type", "base64")
                        put("media_type", "image/jpeg")
                        put("data", dataUrlPayload(chatImageUrl(obj)))
                    }
                })
            }
        }
    }
    if (message.role == "assistant") {
        message.toolCalls.forEach { call ->
            add(buildJsonObject {
                put("type", "tool_use")
                put("id", call.id)
                put("name", call.function.name)
                put("input", parseJsonObjectOrEmpty(call.function.arguments))
            })
        }
    }
}

private fun googleSystem(messages: List<ChatMessage>): JsonObject? {
    val text = messages.filter { it.role == "system" || it.role == "developer" }
        .joinToString("\n\n") { contentText(it.content).trim() }
        .trim()
    if (text.isBlank()) return null
    return buildJsonObject {
        putJsonArray("parts") {
            add(buildJsonObject { put("text", text) })
        }
    }
}

private fun googleContents(messages: List<ChatMessage>): JsonArray = buildJsonArray {
    messages.filter { it.role != "system" && it.role != "developer" && it.role != "tool" && it.toolCalls.isEmpty() }
        .forEach { message ->
            add(buildJsonObject {
                put("role", if (message.role == "assistant") "model" else "user")
                putJsonArray("parts") {
                    (message.content as? JsonArray)?.forEach { part ->
                        val obj = part.jsonObject
                        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                            "input_text", "output_text", "text" -> add(buildJsonObject {
                                put("text", obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                            })
                            "input_image", "image_url" -> add(buildJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", "image/jpeg")
                                    put("data", dataUrlPayload(chatImageUrl(obj)))
                                }
                            })
                        }
                    }
                }
            })
        }
}

private fun contentText(content: JsonElement?): String {
    return when (content) {
        null, JsonNull -> ""
        is JsonPrimitive -> content.contentOrNull.orEmpty()
        is JsonArray -> content.joinToString("") { part ->
            val obj = part.jsonObject
            obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
        else -> ""
    }
}

private fun chatImageUrl(part: JsonObject): String {
    val image = part["image_url"]
    return when (image) {
        is JsonPrimitive -> image.contentOrNull.orEmpty()
        is JsonObject -> image["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        else -> ""
    }
}

private fun dataUrlPayload(url: String): String = url.substringAfter("base64,", "")

private fun parseJsonObjectOrEmpty(text: String): JsonObject =
    runCatching { Json.Default.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))
