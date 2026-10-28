package `fun`.kirari.hanako.core.data

import `fun`.kirari.llm.core.ProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class ProviderImportParserTest {

    @Test
    fun parseImportedProviderConfig_parsesNewApiChannelConn() {
        val raw = """
            {
              "newapi_channel_conn": true,
              "name": "NewAPI Channel",
              "url": "https://example.com/v1",
              "key": "secret"
            }
        """.trimIndent()

        val config = parseImportedProviderConfig(raw)

        requireNotNull(config)
        assertEquals(ProviderKind.OPENAI_COMPATIBLE, config.kind)
        assertEquals("NewAPI Channel", config.name)
        assertEquals("https://example.com/v1", config.baseUrl)
        assertEquals("secret", config.apiKey)
    }

    @Test
    fun parseImportedProviderConfig_parsesAiProviderPayload() {
        val payload = """
            {
              "type": "openai",
              "useResponseApi": "true",
              "name": "Imported",
              "baseUrl": "https://api.openai.com/v1",
              "apiKey": "k"
            }
        """.trimIndent()
        val raw = "ai-provider:v1:${Base64.getEncoder().encodeToString(payload.toByteArray())}"

        val config = parseImportedProviderConfig(raw)

        requireNotNull(config)
        assertEquals(ProviderKind.OPENAI_RESPONSES, config.kind)
        assertEquals("Imported", config.name)
        assertEquals("https://api.openai.com/v1", config.baseUrl)
        assertEquals("k", config.apiKey)
    }

    @Test
    fun parseImportedProviderConfig_mapsAnthropicAndGoogleTypes() {
        val anthropic = encodedProvider(type = "anthropic")
        val google = encodedProvider(type = "gemini")

        assertEquals(ProviderKind.ANTHROPIC, parseImportedProviderConfig(anthropic)?.kind)
        assertEquals(ProviderKind.GOOGLE, parseImportedProviderConfig(google)?.kind)
    }

    @Test
    fun parseImportedProviderConfig_unknownTypeFallsBackToOpenAiCompatibleOrResponses() {
        val plainUnknown = encodedProvider(type = "unknown")
        val responsesUnknown = encodedProvider(type = "unknown", useResponseApi = true)

        assertEquals(ProviderKind.OPENAI_COMPATIBLE, parseImportedProviderConfig(plainUnknown)?.kind)
        assertEquals(ProviderKind.OPENAI_RESPONSES, parseImportedProviderConfig(responsesUnknown)?.kind)
    }

    @Test
    fun parseImportedProviderConfig_returnsNullForInvalidPayloads() {
        assertNull(parseImportedProviderConfig(""))
        assertNull(parseImportedProviderConfig("ai-provider:v1:not-base64"))
        assertNull(parseImportedProviderConfig("""{"newapi_channel_conn":true,"url":"","key":"x"}"""))
    }

    private fun encodedProvider(type: String, useResponseApi: Boolean = false): String {
        val payload = """
            {
              "type": "$type",
              "useResponseApi": "${useResponseApi}",
              "baseUrl": "https://example.com",
              "apiKey": "key"
            }
        """.trimIndent()
        return "ai-provider:v1:${Base64.getEncoder().encodeToString(payload.toByteArray())}"
    }
}
