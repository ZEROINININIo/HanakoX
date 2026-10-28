package `fun`.kirari.hanako.solve.workflow

import android.graphics.Bitmap
import `fun`.kirari.hanako.core.model.AutomationActionRecord
import `fun`.kirari.hanako.core.model.ProcessingEvent
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.solve.workflow.ProcessingPipeline

internal interface HanakoWorkflowEngine {
    suspend fun prepareBaseResult(
        models: ProcessingPipeline.ResolvedModels,
        bitmaps: List<Bitmap>,
        detail: String = "请求已开始"
    ): Pair<ProcessingResult, CapturedImages>

    fun prepareRegenerationBaseResult(
        existingResult: ProcessingResult,
        models: ProcessingPipeline.ResolvedModels,
        bitmaps: List<Bitmap>,
        detail: String = "正在重新生成"
    ): Pair<ProcessingResult, CapturedImages>

    suspend fun runAnswerWorkflow(
        models: ProcessingPipeline.ResolvedModels,
        capturedImages: CapturedImages,
        onOcrDelta: suspend (String) -> Unit,
        onAnswerDelta: suspend (String) -> Unit,
        onProgressEvent: suspend (ProcessingEvent) -> Unit
    ): AnswerWorkflowOutput

    suspend fun runAutomationWorkflow(
        models: ProcessingPipeline.ResolvedModels,
        capturedImages: CapturedImages,
        onOcrDelta: suspend (String) -> Unit,
        onThoughtDelta: suspend (String) -> Unit,
        onProgressEvent: suspend (ProcessingEvent) -> Unit
    ): AutomationWorkflowOutput

    fun buildAnswerResult(
        base: ProcessingResult,
        models: ProcessingPipeline.ResolvedModels,
        output: AnswerWorkflowOutput,
        progressEvents: List<ProcessingEvent>
    ): ProcessingResult

    fun buildAutomationResult(
        base: ProcessingResult,
        models: ProcessingPipeline.ResolvedModels,
        output: AutomationWorkflowOutput,
        progressEvents: List<ProcessingEvent>
    ): Pair<AutomationActionRecord?, ProcessingResult>
}
