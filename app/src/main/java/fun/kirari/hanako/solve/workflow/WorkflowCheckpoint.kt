package `fun`.kirari.hanako.solve.workflow

internal data class WorkflowCheckpoint(
    val workflowId: String,
    val nodeId: String,
    val inputSummary: String,
    val outputSummary: String,
    val artifacts: Map<String, String> = emptyMap(),
    val replayable: Boolean,
    val resumable: Boolean,
    val createdAtMillis: Long = System.currentTimeMillis()
)
