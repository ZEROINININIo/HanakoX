package `fun`.kirari.hanako.solve.workflow

internal interface WorkflowNode<I, O> {
    val id: String

    suspend fun run(input: I, ctx: WorkflowContext): NodeResult<O>
}
