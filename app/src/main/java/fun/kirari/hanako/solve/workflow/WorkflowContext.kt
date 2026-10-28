package `fun`.kirari.hanako.solve.workflow

internal class WorkflowContext(
    val workflowId: String,
    val metadata: Map<String, String> = emptyMap(),
    private val summarizer: (Any?) -> String = ::defaultSummary
) {
    private val recordedCheckpoints = mutableListOf<WorkflowCheckpoint>()

    fun summarize(value: Any?): String = summarizer(value)

    fun recordCheckpoint(checkpoint: WorkflowCheckpoint) {
        recordedCheckpoints += checkpoint
    }

    fun checkpoints(): List<WorkflowCheckpoint> = recordedCheckpoints.toList()
}

private fun defaultSummary(value: Any?): String {
    return when (value) {
        null -> "null"
        is String -> "text(${value.length})"
        is Collection<*> -> "collection(${value.size})"
        is Map<*, *> -> "map(${value.size})"
        else -> value::class.simpleName ?: "unknown"
    }
}
