package `fun`.kirari.hanako.solve.workflow

internal class WorkflowRunner {
    suspend fun <I, O> runNode(
        node: WorkflowNode<I, O>,
        input: I,
        ctx: WorkflowContext
    ): NodeResult<O> {
        val result = node.run(input, ctx)
        ctx.recordCheckpoint(
            WorkflowCheckpoint(
                workflowId = ctx.workflowId,
                nodeId = node.id,
                inputSummary = ctx.summarize(input),
                outputSummary = result.checkpointSummary ?: ctx.summarize(result.output),
                artifacts = result.artifacts,
                replayable = result.replayable,
                resumable = result.resumable
            )
        )
        return result
    }
}
