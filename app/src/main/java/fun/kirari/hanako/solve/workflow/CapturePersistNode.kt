package `fun`.kirari.hanako.solve.workflow

import android.content.Context
import android.graphics.Bitmap
import `fun`.kirari.hanako.core.model.saveToHistoryFile
import `fun`.kirari.hanako.solve.workflow.NodeResult
import `fun`.kirari.hanako.solve.workflow.WorkflowContext
import `fun`.kirari.hanako.solve.workflow.WorkflowNode
import java.util.UUID

internal class CapturePersistNode(
    private val appContext: Context
) : WorkflowNode<List<Bitmap>, CapturedImages> {
    override val id: String = "capture_persist"

    override suspend fun run(input: List<Bitmap>, ctx: WorkflowContext): NodeResult<CapturedImages> {
        val historyId = UUID.randomUUID().toString()
        val screenshotPaths = input.mapIndexed { index, bitmap ->
            bitmap.saveToHistoryFile(appContext, "${historyId}_$index")
        }
        return NodeResult(
            output = CapturedImages(
                historyId = historyId,
                bitmaps = input,
                screenshotPaths = screenshotPaths
            ),
            artifacts = screenshotPaths.mapIndexed { index, path -> "screenshot_$index" to path }.toMap(),
            checkpointSummary = "saved ${screenshotPaths.size} screenshots"
        )
    }
}
