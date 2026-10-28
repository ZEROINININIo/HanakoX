package `fun`.kirari.hanako.solve.workflow.tools

import `fun`.kirari.hanako.core.network.ToolDef
import kotlinx.serialization.json.JsonObject

internal interface AgentTool {
    val name: String
    val definition: ToolDef

    suspend fun invoke(args: JsonObject, ctx: ToolContext): ToolResult
}
