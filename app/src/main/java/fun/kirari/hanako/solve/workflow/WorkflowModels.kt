package `fun`.kirari.hanako.solve.workflow

import android.graphics.Bitmap
import `fun`.kirari.hanako.solve.model.AutomationResult
import `fun`.kirari.hanako.core.model.ProcessingCheckpointSummary
import `fun`.kirari.hanako.core.network.search.SearchOutcome
import `fun`.kirari.hanako.solve.workflow.ProcessingPipeline
import `fun`.kirari.hanako.solve.workflow.WorkflowCheckpoint

internal data class CapturedImages(
    val historyId: String,
    val bitmaps: List<Bitmap>,
    val screenshotPaths: List<String>
)

internal data class OcrNodeInput(
    val capturedImages: CapturedImages,
    val models: ProcessingPipeline.ResolvedModels
)

internal data class OcrNodeOutput(
    val text: String,
    val pageTexts: List<String>,
    val providerInfo: String
)

internal data class AnswerNodeInput(
    val capturedImages: CapturedImages,
    val models: ProcessingPipeline.ResolvedModels,
    val ocrOutput: OcrNodeOutput?
)

internal data class AnswerNodeOutput(
    val answer: String,
    val searchOutcome: SearchOutcome?,
    val messageTraceSummary: String
)

internal data class AnswerWorkflowOutput(
    val capturedImages: CapturedImages,
    val ocrOutput: OcrNodeOutput?,
    val answerOutput: AnswerNodeOutput,
    val checkpoints: List<WorkflowCheckpoint>
)

internal data class AutomationNodeInput(
    val capturedImages: CapturedImages,
    val models: ProcessingPipeline.ResolvedModels,
    val ocrOutput: OcrNodeOutput?
)

internal data class AutomationNodeOutput(
    val automationResult: AutomationResult,
    val searchOutcome: SearchOutcome?,
    val toolTraceSummary: String
)

internal data class AutomationWorkflowOutput(
    val capturedImages: CapturedImages,
    val ocrOutput: OcrNodeOutput?,
    val automationOutput: AutomationNodeOutput,
    val checkpoints: List<WorkflowCheckpoint>
)

internal fun List<WorkflowCheckpoint>.toProcessingCheckpointSummaries(): List<ProcessingCheckpointSummary> {
    return map { checkpoint ->
        ProcessingCheckpointSummary(
            nodeId = checkpoint.nodeId,
            inputSummary = checkpoint.inputSummary,
            outputSummary = checkpoint.outputSummary,
            artifacts = checkpoint.artifacts,
            replayable = checkpoint.replayable,
            resumable = checkpoint.resumable,
            createdAtMillis = checkpoint.createdAtMillis
        )
    }
}
