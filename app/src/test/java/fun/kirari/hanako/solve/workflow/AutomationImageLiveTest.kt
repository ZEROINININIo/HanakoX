package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.core.model.AutomationActionType
import `fun`.kirari.hanako.solve.model.AutomationResult
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.network.NetworkClientProvider
import `fun`.kirari.hanako.core.network.UnifiedLLMClient
import `fun`.kirari.llm.core.LlmEvent
import `fun`.kirari.llm.core.ProviderKind
import `fun`.kirari.llm.core.ToolRegistry
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AutomationImageLiveTest {
    @Test
    fun responsesVision_routesChoiceImageToBubbleAndBlankImageToClipboard() = runBlocking {
        val baseUrl = System.getenv("HANAKO_RESPONSES_TEST_BASE_URL")
        val apiKey = System.getenv("HANAKO_RESPONSES_TEST_API_KEY")
        assumeTrue(
            "Missing HANAKO_RESPONSES_TEST_BASE_URL or HANAKO_RESPONSES_TEST_API_KEY",
            !baseUrl.isNullOrBlank() && !apiKey.isNullOrBlank()
        )
        val model = System.getenv("HANAKO_RESPONSES_TEST_MODEL") ?: "gpt-5.4-mini"
        val provider = ModelProviderConfig(
            name = "ResponsesLiveTest",
            kind = ProviderKind.OPENAI_RESPONSES,
            baseUrl = requireNotNull(baseUrl).removeSuffix("/responses").trimEnd('/'),
            apiKey = requireNotNull(apiKey),
            chatModel = model,
            visionModel = model,
            ocrModel = model
        )
        val client = UnifiedLLMClient(NetworkClientProvider())

        val choice = runAutomationImageCase(
            client = client,
            provider = provider,
            model = model,
            imageBase64 = questionImageBase64(
                lines = listOf(
                    "Single choice question",
                    "Solve: 3x + 6 = 12. What is x?",
                    "A. 1",
                    "B. 2",
                    "C. 3",
                    "D. 4"
                )
            )
        )
        val choiceAction = checkNotNull(choice.action)
        assertEquals(AutomationActionType.SHOW_BUBBLE_LETTERS, choiceAction.type)
        assertEquals("B", choiceAction.text)

        val blank = runAutomationImageCase(
            client = client,
            provider = provider,
            model = model,
            imageBase64 = questionImageBase64(
                lines = listOf(
                    "Fill in the blank",
                    "Compute 7 + 8 = ____.",
                    "Only fill the blank."
                )
            )
        )
        val blankAction = checkNotNull(blank.action)
        assertEquals(AutomationActionType.SET_CLIPBOARD, blankAction.type)
        assertEquals("15", blankAction.text)
        assertNotEquals(AutomationActionType.SET_CLIPBOARD, choiceAction.type)
    }

    private suspend fun runAutomationImageCase(
        client: UnifiedLLMClient,
        provider: ModelProviderConfig,
        model: String,
        imageBase64: String
    ): AutomationResult {
        val text = StringBuilder()
        var toolCall: LlmEvent.ToolCall? = null
        client.stream(
            provider = provider,
            model = model,
            systemPrompt = liveAutomationSystemPrompt,
            userPrompt = "Read the image and answer through exactly one automation tool call.",
            imagesBase64 = listOf(imageBase64),
            tools = ToolRegistry.AUTOMATION_TOOLS,
            firstDeltaTimeoutMillis = 60_000L,
            trustAllHttpsCertificates = false
        ).collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> text.append(event.text)
                is LlmEvent.ToolCall -> toolCall = event
                is LlmEvent.Done -> Unit
            }
        }
        return buildAutomationResultFromModelOutput(
            toolName = toolCall?.name,
            toolText = toolCall?.arguments?.get("text")?.jsonPrimitive?.contentOrNull,
            thought = text.toString()
        )
    }

    private fun questionImageBase64(lines: List<String>): String {
        val image = PngCanvas(width = 1200, height = 760)
        var y = 110
        for ((index, line) in lines.withIndex()) {
            image.drawText(line.uppercase(), x = 80, y = y, scale = if (index == 0) 7 else 6)
            y += if (index == 0) 90 else 74
        }
        return Base64.getEncoder().encodeToString(image.toPngBytes())
    }
}

private val liveAutomationSystemPrompt = """
    You are in automatic answer mode.
    First output a brief thought identifying the question type, then call exactly one tool.
    Tool rules:
    - For single-choice, multiple-choice, true/false, or any answer that maps to visible options, call show_bubble_letters. The text argument must contain only option letters or true/false symbols.
    - For fill-in-the-blank, input-box, or text answers that the user needs to paste, call set_clipboard. The text argument must contain only the final pasteable answer.
    - Never put analysis, explanation, or question summaries into set_clipboard.
    - Do not omit the tool call.
""".trimIndent()

private class PngCanvas(
    private val width: Int,
    private val height: Int
) {
    private val pixels = ByteArray(width * height * 3) { 0xFF.toByte() }

    fun drawText(text: String, x: Int, y: Int, scale: Int) {
        var cursor = x
        for (ch in text) {
            val glyph = glyphs[ch] ?: glyphs[' '] ?: continue
            drawGlyph(glyph, cursor, y, scale)
            cursor += 6 * scale
        }
    }

    private fun drawGlyph(rows: List<String>, x: Int, y: Int, scale: Int) {
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { colIndex, bit ->
                if (bit == '1') {
                    fillRect(
                        x = x + colIndex * scale,
                        y = y + rowIndex * scale,
                        width = scale,
                        height = scale
                    )
                }
            }
        }
    }

    private fun fillRect(x: Int, y: Int, width: Int, height: Int) {
        for (py in y until (y + height).coerceAtMost(this.height)) {
            if (py < 0) continue
            for (px in x until (x + width).coerceAtMost(this.width)) {
                if (px < 0) continue
                val offset = (py * this.width + px) * 3
                pixels[offset] = 24
                pixels[offset + 1] = 31
                pixels[offset + 2] = 42
            }
        }
    }

    fun toPngBytes(): ByteArray {
        val raw = ByteArrayOutputStream()
        for (row in 0 until height) {
            raw.write(0)
            raw.write(pixels, row * width * 3, width * 3)
        }
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed, Deflater(Deflater.BEST_SPEED)).use { it.write(raw.toByteArray()) }

        return ByteArrayOutputStream().apply {
            write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
            writeChunk("IHDR", ByteArrayOutputStream().apply {
                writeInt(width)
                writeInt(height)
                write(byteArrayOf(8, 2, 0, 0, 0))
            }.toByteArray())
            writeChunk("IDAT", compressed.toByteArray())
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
        writeInt(data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        write(typeBytes)
        write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeInt(crc.value.toInt())
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }
}

private val glyphs: Map<Char, List<String>> = mapOf(
    ' ' to listOf("00000", "00000", "00000", "00000", "00000", "00000", "00000"),
    '.' to listOf("00000", "00000", "00000", "00000", "00000", "01100", "01100"),
    ':' to listOf("00000", "01100", "01100", "00000", "01100", "01100", "00000"),
    '?' to listOf("01110", "10001", "00001", "00010", "00100", "00000", "00100"),
    '_' to listOf("00000", "00000", "00000", "00000", "00000", "00000", "11111"),
    '+' to listOf("00000", "00100", "00100", "11111", "00100", "00100", "00000"),
    '=' to listOf("00000", "00000", "11111", "00000", "11111", "00000", "00000"),
    '-' to listOf("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
    '/' to listOf("00001", "00010", "00010", "00100", "01000", "01000", "10000"),
    '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    '3' to listOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
    '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    '5' to listOf("11111", "10000", "10000", "11110", "00001", "00001", "11110"),
    '6' to listOf("01110", "10000", "10000", "11110", "10001", "10001", "01110"),
    '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to listOf("01110", "10001", "10001", "01111", "00001", "00001", "01110"),
    'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    'B' to listOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
    'C' to listOf("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
    'D' to listOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    'F' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
    'G' to listOf("01110", "10001", "10000", "10111", "10001", "10001", "01110"),
    'H' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
    'I' to listOf("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
    'J' to listOf("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
    'K' to listOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'M' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    'N' to listOf("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
    'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'P' to listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    'Q' to listOf("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
    'R' to listOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
    'S' to listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
    'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    'V' to listOf("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
    'W' to listOf("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
    'X' to listOf("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
    'Y' to listOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
    'Z' to listOf("11111", "00001", "00010", "00100", "01000", "10000", "11111")
)
