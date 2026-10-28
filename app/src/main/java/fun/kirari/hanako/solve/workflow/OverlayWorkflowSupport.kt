package `fun`.kirari.hanako.solve.workflow

import android.graphics.Bitmap
import android.util.Base64
import `fun`.kirari.hanako.core.network.search.SearchOutcome
import `fun`.kirari.llm.core.ChatMessage
import `fun`.kirari.llm.core.ChatToolCall
import `fun`.kirari.llm.core.ChatToolFunction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun assistantPromptWithCopyMarker(systemPrompt: String): String {
    val trimmed = systemPrompt.trim()
    if (trimmed.isBlank()) return trimmed
    return """
        你可以在回答中插入如下格式的可复制文本块：
        [copy:内容]
        其中"内容"会显示为一个小标签，点击复制图标后会写入同样的文本到剪贴板。
        对于问题的答案或用户需要填写到某个表单中的内容，你必须给出一键复制的标签。

        $trimmed
    """.trimIndent()
}

internal fun Bitmap.toBase64Jpeg(quality: Int = 92): String {
    val output = java.io.ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

internal fun buildEnhancedUserPrompt(
    basePrompt: String,
    searchOutcome: SearchOutcome?
): String {
    if (searchOutcome?.formattedText == null) return basePrompt
    return "${searchOutcome.formattedText}\n\n$basePrompt"
}

internal fun searchEnabledAssistantPrompt(systemPrompt: String): String {
    val trimmed = systemPrompt.trim()
    return """
        当问题依赖最新事实、新闻、政策法规更新、体育赛果、统计数据、公众人物近况、产品/软件/模型版本、公司动态，或明确要求联网查询时，先调用 web_search。
        web_search 的 query 参数必须是简洁关键词或短语，不要写成长句。
        不需要搜索时，直接继续完成任务，不要解释你是否联网。

        $trimmed
    """.trimIndent()
}

internal fun textMessage(role: String, text: String): ChatMessage =
    ChatMessage(
        role = role,
        content = buildJsonArray {
            add(buildJsonObject {
                put("type", "input_text")
                put("text", text)
            })
        }
    )

internal fun userMessage(text: String, imagesBase64: List<String>): ChatMessage =
    ChatMessage(
        role = "user",
        content = buildJsonArray {
            if (text.isNotBlank()) {
                add(buildJsonObject {
                    put("type", "input_text")
                    put("text", text)
                })
            }
            imagesBase64.forEach { imageBase64 ->
                add(buildJsonObject {
                    put("type", "input_image")
                    put("image_url", "data:image/jpeg;base64,$imageBase64")
                })
            }
        }
    )

internal fun assistantToolCallMessage(
    text: String,
    toolCallId: String,
    toolName: String,
    arguments: JsonObject
): ChatMessage =
    ChatMessage(
        role = "assistant",
        content = buildJsonArray {
            if (text.isNotBlank()) {
                add(buildJsonObject {
                    put("type", "output_text")
                    put("text", text)
                })
            }
        },
        toolCalls = listOf(
            ChatToolCall(
                id = toolCallId,
                function = ChatToolFunction(
                    name = toolName,
                    arguments = arguments.toString()
                )
            )
        )
    )

internal fun toolResultMessage(toolCallId: String, result: String): ChatMessage =
    ChatMessage(
        role = "tool",
        toolCallId = toolCallId,
        content = JsonPrimitive(result)
    )
