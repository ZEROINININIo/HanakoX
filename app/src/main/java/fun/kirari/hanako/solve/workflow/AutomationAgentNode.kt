package `fun`.kirari.hanako.solve.workflow

import android.util.Log
import `fun`.kirari.hanako.solve.workflow.tools.AgentTool
import `fun`.kirari.hanako.solve.workflow.tools.ToolContext
import `fun`.kirari.hanako.solve.model.AutomationResult
import `fun`.kirari.hanako.solve.workflow.buildAutomationResultFromModelOutput
import `fun`.kirari.hanako.core.model.AutomationActionRecord
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.model.ProcessingEvent
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.core.network.ToolRegistry
import `fun`.kirari.hanako.core.network.UnifiedLLMClient
import `fun`.kirari.hanako.core.network.search.SearchOutcome
import `fun`.kirari.hanako.solve.workflow.NodeResult
import `fun`.kirari.hanako.solve.workflow.WorkflowContext
import `fun`.kirari.hanako.solve.workflow.WorkflowNode
import `fun`.kirari.llm.core.ChatMessage
import `fun`.kirari.llm.core.ChatToolCall
import `fun`.kirari.llm.core.ChatToolFunction
import `fun`.kirari.llm.core.LlmEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class AutomationAgentNode(
    private val unifiedClient: UnifiedLLMClient,
    private val onThoughtDelta: suspend (String) -> Unit,
    private val onProgressEvent: suspend (ProcessingEvent) -> Unit,
    private val toolsProvider: (AutomationNodeInput) -> List<AgentTool>
) : WorkflowNode<AutomationNodeInput, AutomationNodeOutput> {
    override val id: String = "automation_agent"
    private val traceTag = "HanakoAutomationTrace"

    override suspend fun run(input: AutomationNodeInput, ctx: WorkflowContext): NodeResult<AutomationNodeOutput> {
        val models = input.models
        val provider = when (models.route) {
            ProcessingRoute.OCR_THEN_LLM -> requireNotNull(models.textProvider)
            ProcessingRoute.MULTIMODAL_DIRECT -> requireNotNull(models.visionProvider)
        }
        val model = when (models.route) {
            ProcessingRoute.OCR_THEN_LLM -> models.textModel
            ProcessingRoute.MULTIMODAL_DIRECT -> models.visionModel
        }
        val assistantPrompt = when (models.route) {
            ProcessingRoute.OCR_THEN_LLM -> models.assistant.textPrompt
            ProcessingRoute.MULTIMODAL_DIRECT -> models.assistant.visionPrompt
        }
        val basePrompt = input.ocrOutput?.let {
            "以下是 OCR 结果，请先输出简短、清晰的思考过程，再通过一次工具调用给出自动模式动作：\n${it.text}"
        } ?: "请根据整张屏幕截图先输出简短、清晰的思考过程，再通过一次工具调用给出自动模式动作。"
        val imagesBase64 = if (input.ocrOutput == null) {
            input.capturedImages.bitmaps.map { it.toBase64Jpeg() }
        } else {
            emptyList()
        }
        val runtime = AutomationAgentRuntime(unifiedClient)
        automationTrace(
            traceTag,
            "node run workflow=${ctx.workflowId} route=${models.route} provider=${provider.kind} model=$model " +
                "ocr=${input.ocrOutput != null} imageCount=${input.capturedImages.bitmaps.size}"
        )
        val output = runtime.run(
            provider = provider,
            model = model,
            assistantPrompt = assistantPrompt,
            basePrompt = basePrompt,
            imagesBase64 = imagesBase64,
            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = models.trustAllHttpsCertificates,
            tools = toolsProvider(input),
            toolContext = ToolContext(workflowId = ctx.workflowId, nodeId = id),
            onThoughtDelta = onThoughtDelta,
            onToolEvents = { events ->
                events.forEach { onProgressEvent(it) }
            }
        )
        AppDebugLogStore.i(
            "HanakoAutomationNode",
            "automation complete thoughtLength=${output.automationResult.thought.length} action=${output.automationResult.action?.type}"
        )
        return NodeResult(
            output = output,
            checkpointSummary = "automation action ${output.automationResult.action?.type ?: "none"}"
        )
    }
}

private class AutomationAgentRuntime(
    private val unifiedClient: UnifiedLLMClient
) {
    private val traceTag = "HanakoAutomationTrace"

    suspend fun run(
        provider: ModelProviderConfig,
        model: String,
        assistantPrompt: String,
        basePrompt: String,
        imagesBase64: List<String>,
        firstDeltaTimeoutMillis: Long,
        trustAllHttpsCertificates: Boolean,
        tools: List<AgentTool>,
        toolContext: ToolContext,
        onThoughtDelta: suspend (String) -> Unit,
        onToolEvents: suspend (List<ProcessingEvent>) -> Unit
    ): AutomationNodeOutput {
        val messages = mutableListOf<ChatMessage>()
        messages += textMessage(
            role = "system",
            text = automationSystemPrompt(
                if (tools.any { it.name == ToolRegistry.WEB_SEARCH_TOOL.name }) {
                    searchEnabledAssistantPrompt(assistantPrompt)
                } else {
                    assistantPrompt
                }
            )
        )
        messages += userMessage(basePrompt, imagesBase64)
        automationTrace(
            traceTag,
            "runtime start provider=${provider.kind} model=$model imageCount=${imagesBase64.size} " +
                "tools=${tools.joinToString { it.name }} promptLen=${basePrompt.length}"
        )

        var latestSearchOutcome: SearchOutcome? = null
        repeat(4) { index ->
            val pass = collectToolLoopStream(
                provider = provider,
                model = model,
                messages = messages,
                tools = tools,
                firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
                trustAllHttpsCertificates = trustAllHttpsCertificates,
                onThoughtDelta = onThoughtDelta
            )
            logPass("tool_loop_${index + 1}", pass.text, pass.toolCalls)
            val toolCall = pass.toolCalls.firstOrNull { it.name != ToolRegistry.WEB_SEARCH_TOOL.name }
                ?: pass.toolCalls.firstOrNull()
            if (toolCall == null) {
                automationTrace(traceTag, "tool_loop_${index + 1} no tool call; returning raw text only")
                return AutomationNodeOutput(
                    automationResult = buildAutomationResult(StreamResult(thought = pass.text.trim(), toolCall = null)),
                    searchOutcome = latestSearchOutcome,
                    toolTraceSummary = "tool_loop_${index + 1}_no_call"
                )
            }

            val tool = tools.firstOrNull { it.name == toolCall.name }
            if (tool == null) {
                automationTrace(traceTag, "tool_loop_${index + 1} unknown tool=${toolCall.name} args=${toolCall.arguments}")
                return AutomationNodeOutput(
                    automationResult = buildAutomationResult(StreamResult(thought = pass.text.trim(), toolCall = toolCall)),
                    searchOutcome = latestSearchOutcome,
                    toolTraceSummary = "unknown_tool_${toolCall.name}"
                )
            }
            val toolResult = tool.invoke(toolCall.arguments, toolContext)
            automationTrace(
                traceTag,
                "tool_loop_${index + 1} invoke tool=${tool.name} args=${toolCall.arguments} resultLen=${toolResult.text.length}"
            )
            onToolEvents(toolResult.events)
            if (tool.name == ToolRegistry.WEB_SEARCH_TOOL.name) {
                latestSearchOutcome = SearchOutcome(
                    performed = true,
                    results = emptyList(),
                    formattedText = toolResult.text.takeIf { it.isNotBlank() },
                    keywords = toolCall.arguments["query"]?.jsonPrimitive?.contentOrNull,
                    skipReason = null
                )
                val toolCallId = toolCall.id ?: "call_${tool.name}_${index + 1}"
                messages += assistantToolCallMessage(
                    text = pass.text,
                    toolCallId = toolCallId,
                    toolName = toolCall.name,
                    arguments = toolCall.arguments
                )
                messages += toolResultMessage(toolCallId, toolResult.text)
                return@repeat
            }
            return AutomationNodeOutput(
                automationResult = buildAutomationResult(StreamResult(thought = pass.text.trim(), toolCall = toolCall)),
                searchOutcome = latestSearchOutcome,
                toolTraceSummary = "final_tool_${toolCall.name}"
            )
        }

        val fallback = collectSingleToolPass(
            provider = provider,
            model = model,
            systemPrompt = automationSystemPrompt(assistantPrompt),
            userPrompt = buildEnhancedUserPrompt(basePrompt, latestSearchOutcome),
            imagesBase64 = imagesBase64,
            firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = trustAllHttpsCertificates,
            onThoughtDelta = onThoughtDelta
        )
        automationTrace(
            traceTag,
            "fallback pass textLen=${fallback.thought.length} tool=${fallback.toolCall?.name} args=${fallback.toolCall?.arguments}"
        )
        return AutomationNodeOutput(
            automationResult = buildAutomationResult(fallback),
            searchOutcome = latestSearchOutcome,
            toolTraceSummary = "fallback_after_tool_loop"
        )
    }

    private suspend fun collectSingleToolPass(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imagesBase64: List<String>,
        firstDeltaTimeoutMillis: Long,
        trustAllHttpsCertificates: Boolean,
        onThoughtDelta: suspend (String) -> Unit
    ): StreamResult {
        val text = StringBuilder()
        var toolCall: LlmEvent.ToolCall? = null
        unifiedClient.stream(
            provider = provider,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            imagesBase64 = imagesBase64,
            tools = ToolRegistry.AUTOMATION_TOOLS,
            firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = trustAllHttpsCertificates
        ).collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> {
                    text.append(event.text)
                    onThoughtDelta(event.text)
                }
                is LlmEvent.ToolCall -> {
                    toolCall = event
                    automationTrace(traceTag, "single pass toolCall name=${event.name} args=${event.arguments}")
                }
                is LlmEvent.Done -> {}
            }
        }
        automationTrace(traceTag, "single pass complete text=${text.toString().trim()}")
        return StreamResult(text.toString().trim(), toolCall)
    }

    private suspend fun collectToolLoopStream(
        provider: ModelProviderConfig,
        model: String,
        messages: List<ChatMessage>,
        tools: List<AgentTool>,
        firstDeltaTimeoutMillis: Long,
        trustAllHttpsCertificates: Boolean,
        onThoughtDelta: suspend (String) -> Unit
    ): ToolLoopResult {
        val text = StringBuilder()
        val toolCalls = mutableListOf<LlmEvent.ToolCall>()
        unifiedClient.streamMessages(
            provider = provider,
            model = model,
            messages = messages,
            tools = tools.map { it.definition },
            firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = trustAllHttpsCertificates
        ).collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> {
                    text.append(event.text)
                    onThoughtDelta(event.text)
                }
                is LlmEvent.ToolCall -> {
                    toolCalls += event
                    automationTrace(traceTag, "streamMessages toolCall name=${event.name} args=${event.arguments}")
                }
                is LlmEvent.Done -> {}
            }
        }
        return ToolLoopResult(text = text.toString(), toolCalls = toolCalls)
    }

    private fun buildAutomationResult(streamResult: StreamResult): AutomationResult {
        val tc = streamResult.toolCall
        val result = buildAutomationResultFromModelOutput(
            toolName = tc?.name,
            toolText = tc?.arguments?.get("text")?.jsonPrimitive?.contentOrNull,
            thought = streamResult.thought
        )
        automationTrace(
            traceTag,
            "build result tool=${tc?.name} toolText=${tc?.arguments?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()} " +
                "thought=${streamResult.thought} action=${result.action?.type} actionText=${result.action?.text.orEmpty()}"
        )
        return result
    }

    private fun logPass(label: String, text: String, toolCalls: List<LlmEvent.ToolCall>) {
        automationTrace(
            traceTag,
            "$label complete textLen=${text.length} toolCount=${toolCalls.size} " +
                "tools=${toolCalls.joinToString { "${it.name}:${it.arguments}" }}"
        )
        automationTrace(traceTag, "$label rawText=${text.trim()}")
    }
}

private data class StreamResult(
    val thought: String,
    val toolCall: LlmEvent.ToolCall?
)

private data class ToolLoopResult(
    val text: String,
    val toolCalls: List<LlmEvent.ToolCall>
)

private fun automationTrace(tag: String, message: String) {
    val chunks = message.chunked(3500).ifEmpty { listOf("") }
    chunks.forEachIndexed { index, chunk ->
        val line = if (chunks.size == 1) chunk else "[$index/${chunks.lastIndex}] $chunk"
        Log.i(tag, line)
        AppDebugLogStore.i(tag, line)
    }
}

private fun automationSystemPrompt(userPrompt: String): String {
    val trimmed = userPrompt.trim()
    return """
        你当前处于自动答题模式。
        你必须先输出简短、清晰的思考过程，说明你识别到了什么题型、关键依据和最终答案判断。
        思考过程结束后，你必须且只能调用一个工具给出自动动作，不能在工具调用后继续输出额外文本。
        如果不确定，也要先说明不确定点，再优先调用最合适的工具，而不是只输出文字。
        工具选择规则：
        - 单选题、多选题、判断题，或答案最终对应题目选项时，必须调用 show_bubble_letters；text 只写选项字母或对错符号。
        - 填空题、输入框题、文本题，或用户需要粘贴答案本身时，必须调用 set_clipboard；text 只写用户要粘贴进去的最终内容。
        - 选择题即使需要推理，也只用 show_bubble_letters 返回选项字母；不要把选择题的解题过程、判断依据、题干摘要写入 set_clipboard。
        show_bubble_letters 的 text 参数可以是 1-8 个英文字母（大小写均可），或者"对""错""√""×"。不能包含空格、标点或其他解释。
        set_clipboard 的 text 参数必须是用户可以直接粘贴使用的最终答案，不能包含"识别为""关键信息""说明""所以""即可判断""对应选项"等分析性文字。
        不允许调用多个工具，不允许省略工具调用。

        ${trimmed.ifBlank { "请根据截图内容判断题目类型，并选择最合适的自动动作。" }}
    """.trimIndent()
}
