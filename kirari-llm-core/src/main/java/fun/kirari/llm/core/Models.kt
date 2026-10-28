package `fun`.kirari.llm.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
enum class ProviderKind {
    OPENAI_COMPATIBLE,
    OPENAI_RESPONSES,
    ANTHROPIC,
    GOOGLE,
    KIRARI_NETWORK
}

data class ProviderConfig(
    val kind: ProviderKind,
    val baseUrl: String,
    val apiKey: String = "",
    val headers: Map<String, String> = emptyMap()
)

data class ToolParam(
    val name: String,
    val type: String,
    val description: String,
    val pattern: String? = null
)

data class ToolDef(
    val name: String,
    val description: String,
    val params: List<ToolParam>
)

data class ChatToolFunction(
    val name: String,
    val arguments: String
)

data class ChatToolCall(
    val id: String,
    val type: String = "function",
    val function: ChatToolFunction
)

data class ChatMessage(
    val role: String,
    val content: JsonElement? = null,
    val toolCallId: String? = null,
    val toolCalls: List<ChatToolCall> = emptyList()
)

sealed class LlmEvent {
    data class TextDelta(val text: String) : LlmEvent()
    data class ToolCall(val id: String?, val name: String, val arguments: JsonObject) : LlmEvent()
    data object Done : LlmEvent()
}

data class StreamRequest(
    val provider: ProviderConfig,
    val model: String,
    val systemPrompt: String = "",
    val userPrompt: String = "",
    val imagesBase64: List<String> = emptyList(),
    val messages: List<ChatMessage>? = null,
    val tools: List<ToolDef>? = null,
    val firstDeltaTimeoutMillis: Long,
    val trustAllHttpsCertificates: Boolean
) {
    val hasImages: Boolean get() = imagesBase64.isNotEmpty()

    fun effectiveMessages(): List<ChatMessage> {
        messages?.let { return it }
        val result = mutableListOf<ChatMessage>()
        if (systemPrompt.isNotBlank()) {
            result += ChatMessage(
                role = "system",
                content = JsonArray(listOf(textPart(systemPrompt, assistant = false)))
            )
        }
        val userParts = mutableListOf<JsonElement>()
        if (userPrompt.isNotBlank()) {
            userParts += textPart(userPrompt, assistant = false)
        }
        imagesBase64.forEach { imageBase64 ->
            userParts += imagePart(imageBase64)
        }
        result += ChatMessage(
            role = "user",
            content = JsonArray(userParts)
        )
        return result
    }
}

internal interface ProviderAdapter {
    suspend fun stream(request: StreamRequest): kotlinx.coroutines.flow.Flow<LlmEvent>
}

internal class PendingToolCall(
    var id: String? = null,
    var name: String? = null,
    val arguments: StringBuilder = StringBuilder()
)

private fun textPart(text: String, assistant: Boolean): JsonObject = buildJsonObject {
    put("type", if (assistant) "output_text" else "input_text")
    put("text", JsonPrimitive(text))
}

private fun imagePart(imageBase64: String): JsonObject = buildJsonObject {
    put("type", "input_image")
    put("image_url", "data:image/jpeg;base64,$imageBase64")
}
