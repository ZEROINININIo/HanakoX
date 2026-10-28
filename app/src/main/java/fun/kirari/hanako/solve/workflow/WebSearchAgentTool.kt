package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.solve.workflow.tools.AgentTool
import `fun`.kirari.hanako.solve.workflow.tools.ToolContext
import `fun`.kirari.hanako.solve.workflow.tools.ToolResult
import `fun`.kirari.hanako.core.model.ProcessingEvent
import `fun`.kirari.hanako.core.network.ToolRegistry
import `fun`.kirari.hanako.core.network.search.SearchContext
import `fun`.kirari.hanako.core.network.search.SearchOrchestrator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal class WebSearchAgentTool(
    private val orchestrator: SearchOrchestrator,
    private val settingsProvider: () -> SearchContext
) : AgentTool {
    override val name: String = ToolRegistry.WEB_SEARCH_TOOL.name
    override val definition = ToolRegistry.WEB_SEARCH_TOOL

    override suspend fun invoke(args: JsonObject, ctx: ToolContext): ToolResult {
        val query = args["query"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val searchContext = settingsProvider().copy(query = query)
        val outcome = orchestrator.execute(searchContext)
        val events = buildList {
            if (query.isNotBlank()) {
                add(ProcessingEvent(title = "正在联网搜索", detail = "关键词：$query"))
            }
            buildSearchEvent(outcome)?.let(::add)
        }
        return ToolResult(
            text = outcome.formattedText ?: outcome.skipReason?.displayText.orEmpty(),
            events = events,
            summary = query.ifBlank { outcome.skipReason?.displayText }
        )
    }
}

internal fun buildSearchEvent(outcome: `fun`.kirari.hanako.core.network.search.SearchOutcome?): ProcessingEvent? {
    if (outcome == null) return null
    return if (outcome.performed && outcome.results.isNotEmpty()) {
        ProcessingEvent(
            title = "联网搜索完成",
            detail = "关键词：${outcome.keywords}，获取 ${outcome.results.size} 条结果"
        )
    } else {
        outcome.skipReason?.let {
            ProcessingEvent(
                title = "联网搜索已跳过",
                detail = it.displayText
            )
        }
    }
}
