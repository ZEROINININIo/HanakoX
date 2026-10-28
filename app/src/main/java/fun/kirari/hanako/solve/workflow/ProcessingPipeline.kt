package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.AnswerStyle
import `fun`.kirari.hanako.core.data.AssistantPreset
import `fun`.kirari.hanako.core.data.LOCAL_OCR_PROVIDER_ID
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.data.WebSearchSettings
import `fun`.kirari.hanako.core.data.resolveModelName
import `fun`.kirari.hanako.core.data.resolveModelProvider

internal class ProcessingPipeline {
    data class ResolvedModels(
        val assistant: AssistantPreset,
        val ocrProvider: ModelProviderConfig?,
        val ocrModel: String,
        val textProvider: ModelProviderConfig?,
        val textModel: String,
        val visionProvider: ModelProviderConfig?,
        val visionModel: String,
        val firstDeltaTimeoutMillis: Long,
        val route: ProcessingRoute,
        val usingLocalOcr: Boolean,
        val trustAllHttpsCertificates: Boolean,
        val webSearchSettings: WebSearchSettings,
        val answerStyle: AnswerStyle = AnswerStyle.DIRECT
    )

    fun resolveModels(settings: AppSettings): ResolvedModels {
        val assistant = settings.assistants.firstOrNull { it.id == settings.selectedAssistantId }
            ?: error("请先配置助手")
        return ResolvedModels(
            assistant = assistant,
            answerStyle = settings.answerStyle,
            ocrProvider = settings.resolveModelProvider(ModelPurpose.OCR),
            ocrModel = settings.resolveModelName(ModelPurpose.OCR),
            textProvider = settings.resolveModelProvider(ModelPurpose.TEXT),
            textModel = settings.resolveModelName(ModelPurpose.TEXT),
            visionProvider = settings.resolveModelProvider(ModelPurpose.VISION),
            visionModel = settings.resolveModelName(ModelPurpose.VISION),
            firstDeltaTimeoutMillis = settings.automation.autoModeTimeoutSeconds.coerceAtLeast(1) * 1000L,
            route = settings.processingRoute,
            usingLocalOcr = settings.ocrModelSelection.providerId == LOCAL_OCR_PROVIDER_ID,
            trustAllHttpsCertificates = settings.trustAllHttpsCertificates,
            webSearchSettings = settings.webSearch
        )
    }

    fun buildModelSummary(model: String, providerName: String?): String {
        val trimmedModel = model.trim()
        val trimmedProvider = providerName?.trim().orEmpty()
        if (trimmedModel.isBlank()) return ""
        return if (trimmedProvider.isBlank()) trimmedModel else "$trimmedModel（$trimmedProvider）"
    }

    fun validateOcrThenLlmModels(models: ResolvedModels) {
        if ((!models.usingLocalOcr && (models.ocrProvider == null || models.ocrModel.isBlank())) ||
            models.textProvider == null || models.textModel.isBlank()
        ) {
            error("请先在模型设置中配置 OCR 和文本模型")
        }
    }

    fun validateVisionModels(models: ResolvedModels) {
        if (models.visionProvider == null || models.visionModel.isBlank()) {
            error("请先在模型设置中配置多模态模型")
        }
    }
}
