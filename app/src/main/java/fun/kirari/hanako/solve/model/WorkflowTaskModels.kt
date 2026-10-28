package `fun`.kirari.hanako.solve.model

enum class WorkflowTaskKind {
    ANSWER,
    REGENERATE_ANSWER,
    AUTOMATION,
    CONVERSATION
}

enum class WorkflowTaskStatus {
    RUNNING,
    SUCCESS,
    ERROR,
    CANCELLED
}

data class WorkflowTaskState(
    val taskId: String,
    val historyId: String? = null,
    val kind: WorkflowTaskKind,
    val status: WorkflowTaskStatus = WorkflowTaskStatus.RUNNING,
    val answerVersionIndex: Int? = null,
    val conversationTurnId: String? = null,
    val errorMessage: String? = null
)
