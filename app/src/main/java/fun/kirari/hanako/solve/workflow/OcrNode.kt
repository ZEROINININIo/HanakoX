package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.core.model.ProcessingEvent
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.platform.capture.ocr.LocalOcrManager
import `fun`.kirari.hanako.core.network.UnifiedLLMClient
import `fun`.kirari.hanako.solve.workflow.NodeResult
import `fun`.kirari.hanako.solve.workflow.WorkflowContext
import `fun`.kirari.hanako.solve.workflow.WorkflowNode
import `fun`.kirari.llm.core.LlmEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class OcrNode(
    private val unifiedClient: UnifiedLLMClient,
    private val localOcrManager: LocalOcrManager
) : WorkflowNode<OcrNodeInput, OcrNodeOutput> {
    override val id: String = "ocr"

    override suspend fun run(input: OcrNodeInput, ctx: WorkflowContext): NodeResult<OcrNodeOutput> {
        val models = input.models
        val pageTexts = input.capturedImages.bitmaps.map { bitmap ->
            if (models.usingLocalOcr) {
                withContext(Dispatchers.Default) {
                    localOcrManager.recognize(bitmap)
                }
            } else {
                collectTextStream(
                    provider = requireNotNull(models.ocrProvider),
                    model = models.ocrModel,
                    systemPrompt = models.assistant.ocrPrompt,
                    userPrompt = "请执行 OCR。",
                    imagesBase64 = listOf(bitmap.toBase64Jpeg()),
                    firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                    trustAllHttpsCertificates = models.trustAllHttpsCertificates
                )
            }
        }
        val combinedText = pageTexts.joinToString("\n\n---\n\n")
        AppDebugLogStore.i("HanakoOcrNode", "ocr complete pages=${pageTexts.size} length=${combinedText.length}")
        return NodeResult(
            output = OcrNodeOutput(
                text = combinedText,
                pageTexts = pageTexts,
                providerInfo = if (models.usingLocalOcr) "local" else models.ocrProvider?.name.orEmpty()
            ),
            events = listOf(ProcessingEvent(title = "OCR 完成", detail = "已提取 ${combinedText.length} 个字符")),
            checkpointSummary = "ocr text ${combinedText.length} chars"
        )
    }

    private suspend fun collectTextStream(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imagesBase64: List<String>,
        firstDeltaTimeoutMillis: Long,
        trustAllHttpsCertificates: Boolean
    ): String {
        val text = StringBuilder()
        unifiedClient.stream(
            provider = provider,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            imagesBase64 = imagesBase64,
            firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = trustAllHttpsCertificates
        ).collect { event ->
            if (event is LlmEvent.TextDelta) {
                text.append(event.text)
            }
        }
        return text.toString()
    }
}
