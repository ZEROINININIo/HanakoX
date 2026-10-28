package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.solve.workflow.tools.AgentTool
import `fun`.kirari.hanako.solve.workflow.tools.ToolContext
import `fun`.kirari.hanako.solve.workflow.tools.ToolResult
import `fun`.kirari.hanako.core.model.ProcessingEvent
import `fun`.kirari.hanako.core.network.ToolDef
import `fun`.kirari.hanako.core.network.ToolRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal class RegistryToolAgentTool(
    override val definition: ToolDef
) : AgentTool {
    override val name: String = definition.name

    override suspend fun invoke(args: JsonObject, ctx: ToolContext): ToolResult {
        val text = args["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        return ToolResult(
            text = text,
            events = listOf(
                ProcessingEvent(
                    title = "工具调用准备完成",
                    detail = "${definition.name}: $text"
                )
            ),
            hasSideEffect = true,
            summary = definition.name
        )
    }
}

internal fun automationAgentTools(): List<AgentTool> {
    return ToolRegistry.AUTOMATION_TOOLS.map(::RegistryToolAgentTool)
}
