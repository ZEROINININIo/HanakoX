package `fun`.kirari.hanako.solve.workflow.tools

import `fun`.kirari.hanako.core.model.ProcessingEvent
import kotlinx.serialization.json.JsonObject

internal data class ToolResult(
    val text: String,
    val events: List<ProcessingEvent> = emptyList(),
    val payload: JsonObject? = null,
    val hasSideEffect: Boolean = false,
    val summary: String? = null
)
