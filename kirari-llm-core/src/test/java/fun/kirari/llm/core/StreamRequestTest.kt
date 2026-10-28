package `fun`.kirari.llm.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRequestTest {

    @Test
    fun effectiveMessages_buildsSystemAndUserMessagesFromPromptsAndImages() {
        val request = StreamRequest(
            provider = ProviderConfig(
                kind = ProviderKind.OPENAI_COMPATIBLE,
                baseUrl = "https://example.com"
            ),
            model = "model",
            systemPrompt = "system",
            userPrompt = "user",
            imagesBase64 = listOf("abc123"),
            firstDeltaTimeoutMillis = 1_000L,
            trustAllHttpsCertificates = false
        )

        val messages = request.effectiveMessages()

        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)

        val systemContent = messages[0].content as JsonArray
        assertEquals("input_text", systemContent[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("system", systemContent[0].jsonObject["text"]?.jsonPrimitive?.content)

        val userContent = messages[1].content as JsonArray
        assertEquals("input_text", userContent[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("user", userContent[0].jsonObject["text"]?.jsonPrimitive?.content)
        assertEquals("input_image", userContent[1].jsonObject["type"]?.jsonPrimitive?.content)
        assertTrue(
            userContent[1].jsonObject["image_url"]?.jsonPrimitive?.content.orEmpty()
                .startsWith("data:image/jpeg;base64,abc123")
        )
    }

    @Test
    fun effectiveMessages_prefersExplicitMessagesWhenProvided() {
        val explicit = listOf(
            ChatMessage(
                role = "assistant",
                content = JsonArray(emptyList())
            )
        )
        val request = StreamRequest(
            provider = ProviderConfig(
                kind = ProviderKind.OPENAI_COMPATIBLE,
                baseUrl = "https://example.com"
            ),
            model = "model",
            systemPrompt = "ignored",
            userPrompt = "ignored",
            messages = explicit,
            firstDeltaTimeoutMillis = 1_000L,
            trustAllHttpsCertificates = false
        )

        assertEquals(explicit, request.effectiveMessages())
    }

    @Test
    fun effectiveMessages_allowsBlankPromptsAndStillBuildsUserMessage() {
        val request = StreamRequest(
            provider = ProviderConfig(
                kind = ProviderKind.OPENAI_COMPATIBLE,
                baseUrl = "https://example.com"
            ),
            model = "model",
            systemPrompt = "",
            userPrompt = "",
            firstDeltaTimeoutMillis = 1_000L,
            trustAllHttpsCertificates = false
        )

        val messages = request.effectiveMessages()

        assertEquals(1, messages.size)
        assertEquals("user", messages.single().role)
        assertEquals(0, (messages.single().content as JsonArray).size)
    }
}
