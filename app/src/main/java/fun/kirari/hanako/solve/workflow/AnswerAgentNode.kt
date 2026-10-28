package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.solve.workflow.tools.AgentTool
import `fun`.kirari.hanako.solve.workflow.tools.ToolContext
import `fun`.kirari.hanako.solve.workflow.tools.ToolResult
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
import `fun`.kirari.llm.core.LlmEvent

internal class AnswerAgentNode(
    private val unifiedClient: UnifiedLLMClient,
    private val onAnswerDelta: suspend (String) -> Unit,
    private val onProgressEvent: suspend (ProcessingEvent) -> Unit,
    private val toolsProvider: (AnswerNodeInput) -> List<AgentTool>
) : WorkflowNode<AnswerNodeInput, AnswerNodeOutput> {
    override val id: String = "answer_agent"

    override suspend fun run(input: AnswerNodeInput, ctx: WorkflowContext): NodeResult<AnswerNodeOutput> {
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
            ProcessingRoute.OCR_THEN_LLM -> assistantPromptWithCopyMarker(models.assistant.textPrompt)
            ProcessingRoute.MULTIMODAL_DIRECT -> assistantPromptWithCopyMarker(models.assistant.visionPrompt)
        }.withAnswerStyle(models.answerStyle)
        val basePrompt = input.ocrOutput?.let { "以下是 OCR 结果，请完成任务：\n${it.text}" }
            ?: "请直接基于图片内容完成任务。"
        val imagesBase64 = if (input.ocrOutput == null) {
            input.capturedImages.bitmaps.map { it.toBase64Jpeg() }
        } else {
            emptyList()
        }
        val runtime = AnswerAgentRuntime(unifiedClient)
        val outcome = runtime.run(
            provider = provider,
            model = model,
            assistantPrompt = assistantPrompt,
            basePrompt = basePrompt,
            imagesBase64 = imagesBase64,
            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = models.trustAllHttpsCertificates,
            tools = toolsProvider(input),
            toolContext = ToolContext(workflowId = ctx.workflowId, nodeId = id),
            onAnswerDelta = onAnswerDelta,
            onToolEvents = { events ->
                events.forEach { onProgressEvent(it) }
            }
        )
        AppDebugLogStore.i("HanakoAnswerNode", "answer complete length=${outcome.answer.length}")
        return NodeResult(
            output = AnswerNodeOutput(
                answer = outcome.answer,
                searchOutcome = outcome.searchOutcome,
                messageTraceSummary = outcome.messageTraceSummary
            ),
            checkpointSummary = "answer ${outcome.answer.length} chars"
        )
    }
}

internal fun String.withAnswerStyle(style: `fun`.kirari.hanako.core.data.AnswerStyle): String = when (style) {
    `fun`.kirari.hanako.core.data.AnswerStyle.DIRECT -> this
    `fun`.kirari.hanako.core.data.AnswerStyle.CHALLENGE -> this + """


        当前启用“先试试看”挑战模式：不要直接给出最终答案或完整解法。先用一两句话点明关键知识点，再给一个循序渐进的小提示，最后提出一个具体问题邀请用户先尝试。只有用户在后续对话中明确要求答案或解法时，才展开完整讲解。
    """.trimIndent()
}

private data class RuntimeAnswerResult(
    val answer: String,
    val searchOutcome: SearchOutcome?,
    val messageTraceSummary: String
)

private class AnswerAgentRuntime(
    private val unifiedClient: UnifiedLLMClient
) {
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
        onAnswerDelta: suspend (String) -> Unit,
        onToolEvents: suspend (List<ProcessingEvent>) -> Unit
    ): RuntimeAnswerResult {
        if (tools.isEmpty()) {
            val answer = collectTextStream(
                provider = provider,
                model = model,
                systemPrompt = assistantPrompt,
                userPrompt = basePrompt,
                imagesBase64 = imagesBase64,
                firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
                trustAllHttpsCertificates = trustAllHttpsCertificates,
                onDelta = onAnswerDelta
            )
            return RuntimeAnswerResult(answer = answer, searchOutcome = null, messageTraceSummary = "single_pass")
        }

        val messages = mutableListOf<ChatMessage>()
        if (assistantPrompt.isNotBlank()) {
            messages += textMessage(role = "system", text = searchEnabledAssistantPrompt(assistantPrompt))
        }
        messages += userMessage(basePrompt, imagesBase64)

        var latestSearchOutcome: SearchOutcome? = null
        repeat(3) { index ->
            val pass = collectSearchAwareTextStream(
                provider = provider,
                model = model,
                messages = messages,
                tools = tools,
                firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
                trustAllHttpsCertificates = trustAllHttpsCertificates,
                onAnswerDelta = onAnswerDelta
            )
            val toolCall = pass.toolCalls.firstOrNull()
            if (toolCall == null) {
                val finalText = pass.text.trim()
                if (finalText.isNotBlank()) {
                    return RuntimeAnswerResult(
                        answer = pass.text,
                        searchOutcome = latestSearchOutcome,
                        messageTraceSummary = "tool_loop_${index + 1}_done"
                    )
                }
                val fallbackAnswer = collectTextStream(
                    provider = provider,
                    model = model,
                    systemPrompt = assistantPrompt,
                    userPrompt = buildEnhancedUserPrompt(basePrompt, latestSearchOutcome),
                    imagesBase64 = imagesBase64,
                    firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
                    trustAllHttpsCertificates = trustAllHttpsCertificates,
                    onDelta = onAnswerDelta
                )
                return RuntimeAnswerResult(
                    answer = fallbackAnswer,
                    searchOutcome = latestSearchOutcome,
                    messageTraceSummary = "fallback_no_tool_call"
                )
            }
            val tool = tools.firstOrNull { it.name == toolCall.name } ?: return RuntimeAnswerResult(
                answer = pass.text,
                searchOutcome = latestSearchOutcome,
                messageTraceSummary = "unknown_tool_${toolCall.name}"
            )
            val toolResult = tool.invoke(toolCall.arguments, toolContext)
            onToolEvents(toolResult.events)
            if (tool.name == ToolRegistry.WEB_SEARCH_TOOL.name) {
                latestSearchOutcome = inferSearchOutcome(toolResult, latestSearchOutcome)
            }
            val toolCallId = toolCall.id ?: "call_${tool.name}_${index + 1}"
            messages += assistantToolCallMessage(
                text = pass.text,
                toolCallId = toolCallId,
                toolName = toolCall.name,
                arguments = toolCall.arguments
            )
            messages += toolResultMessage(toolCallId, toolResult.text)
        }

        val fallbackAnswer = collectTextStream(
            provider = provider,
            model = model,
            systemPrompt = assistantPrompt,
            userPrompt = buildEnhancedUserPrompt(basePrompt, latestSearchOutcome),
            imagesBase64 = imagesBase64,
            firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = trustAllHttpsCertificates,
            onDelta = onAnswerDelta
        )
        return RuntimeAnswerResult(
            answer = fallbackAnswer,
            searchOutcome = latestSearchOutcome,
            messageTraceSummary = "fallback_after_tool_loop"
        )
    }

    private fun inferSearchOutcome(
        toolResult: ToolResult,
        previous: SearchOutcome?
    ): SearchOutcome? {
        return previous ?: if (toolResult.text.isBlank()) null else SearchOutcome(
            performed = true,
            results = emptyList(),
            formattedText = toolResult.text,
            keywords = toolResult.summary,
            skipReason = null
        )
    }

    private data class SearchAwareTextResult(
        val text: String,
        val toolCalls: List<LlmEvent.ToolCall>
    )

    private suspend fun collectTextStream(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imagesBase64: List<String>,
        firstDeltaTimeoutMillis: Long,
        trustAllHttpsCertificates: Boolean,
        onDelta: suspend (String) -> Unit
    ): String {
        val text = StringBuilder()
        unifiedClient.stream(
            provider = provider,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            imagesBase64 = imagesBase64,
            firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = trustAllHttpsCertificates
        ).collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> {
                    text.append(event.text)
                    onDelta(event.text)
                }
                is LlmEvent.Done -> {}
                else -> {}
            }
        }
        return text.toString()
    }

    private suspend fun collectSearchAwareTextStream(
        provider: ModelProviderConfig,
        model: String,
        messages: List<ChatMessage>,
        tools: List<AgentTool>,
        firstDeltaTimeoutMillis: Long,
        trustAllHttpsCertificates: Boolean,
        onAnswerDelta: suspend (String) -> Unit
    ): SearchAwareTextResult {
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
                    onAnswerDelta(event.text)
                }
                is LlmEvent.ToolCall -> toolCalls += event
                is LlmEvent.Done -> {}
            }
        }
        return SearchAwareTextResult(text = text.toString(), toolCalls = toolCalls)
    }
}
