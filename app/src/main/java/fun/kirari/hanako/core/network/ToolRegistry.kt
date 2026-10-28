package `fun`.kirari.hanako.core.network

internal typealias ToolParam = `fun`.kirari.llm.core.ToolParam
internal typealias ToolDef = `fun`.kirari.llm.core.ToolDef

internal object ToolRegistry {
    val AUTOMATION_TOOLS: List<ToolDef> = `fun`.kirari.llm.core.ToolRegistry.AUTOMATION_TOOLS
    val WEB_SEARCH_TOOL: ToolDef = `fun`.kirari.llm.core.ToolRegistry.WEB_SEARCH_TOOL
}
