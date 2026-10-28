package `fun`.kirari.llm.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    private val sampleTool = ToolDef(
        name = "web_search",
        description = "Search the web",
        params = listOf(
            ToolParam("query", "string", "keywords", pattern = ".*")
        )
    )

    @Test
    fun formatForProvider_chatCompletionsShape_containsAdditionalPropertiesFalse() {
        val formatted = ToolRegistry.formatForProvider(listOf(sampleTool), ProviderKind.OPENAI_COMPATIBLE).jsonArray
        val function = formatted.first().jsonObject["function"]!!.jsonObject
        val parameters = function["parameters"]!!.jsonObject

        assertEquals("web_search", function["name"]!!.jsonPrimitive.content)
        assertEquals(false, parameters["additionalProperties"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("query", parameters["required"]!!.jsonArray.first().jsonPrimitive.content)
    }

    @Test
    fun formatForProvider_responsesShape_placesNameAtTopLevel() {
        val formatted = ToolRegistry.formatForProvider(listOf(sampleTool), ProviderKind.OPENAI_RESPONSES).jsonArray
        val tool = formatted.first().jsonObject

        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        assertEquals("web_search", tool["name"]!!.jsonPrimitive.content)
        assertTrue(tool["parameters"]!!.jsonObject["properties"]!!.jsonObject.containsKey("query"))
    }

    @Test
    fun formatForProvider_anthropicShape_usesInputSchema() {
        val formatted = ToolRegistry.formatForProvider(listOf(sampleTool), ProviderKind.ANTHROPIC).jsonArray
        val tool = formatted.first().jsonObject

        assertEquals("web_search", tool["name"]!!.jsonPrimitive.content)
        assertTrue(tool.containsKey("input_schema"))
        assertFalse(tool["input_schema"]!!.jsonObject.containsKey("additionalProperties"))
    }

    @Test
    fun formatForProvider_googleShape_usesUppercaseTypes() {
        val formatted = ToolRegistry.formatForProvider(listOf(sampleTool), ProviderKind.GOOGLE).jsonObject
        val declaration = formatted["functionDeclarations"]!!.jsonArray.first().jsonObject
        val parameters = declaration["parameters"]!!.jsonObject
        val query = parameters["properties"]!!.jsonObject["query"]!!.jsonObject

        assertEquals("OBJECT", parameters["type"]!!.jsonPrimitive.content)
        assertEquals("STRING", query["type"]!!.jsonPrimitive.content)
    }
}
