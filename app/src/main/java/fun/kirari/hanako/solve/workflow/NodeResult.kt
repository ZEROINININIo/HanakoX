package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.core.model.ProcessingEvent

internal data class NodeResult<O>(
    val output: O,
    val events: List<ProcessingEvent> = emptyList(),
    val artifacts: Map<String, String> = emptyMap(),
    val checkpointSummary: String? = null,
    val replayable: Boolean = true,
    val resumable: Boolean = true
)
