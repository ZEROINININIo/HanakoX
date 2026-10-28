package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.network.UnifiedLLMClient
import `fun`.kirari.hanako.core.model.FollowUpTurn
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.model.latestAnswerText
import `fun`.kirari.hanako.core.model.displayedAssistantVersions
import `fun`.kirari.hanako.core.model.latestAssistantText
import `fun`.kirari.hanako.core.model.loadHistoryBitmaps
import `fun`.kirari.hanako.solve.model.ConversationIntent
import `fun`.kirari.llm.core.ChatMessage
import `fun`.kirari.llm.core.LlmEvent

internal data class PreparedConversationTurn(
    val historyId: String,
    val turnId: String,
    val startedResult: ProcessingResult,
    val provider: ModelProviderConfig,
    val model: String,
    val messages: List<ChatMessage>,
    val firstDeltaTimeoutMillis: Long,
    val trustAllHttpsCertificates: Boolean
)

internal interface ConversationWorkflowEngine {
    suspend fun prepareTurn(
        existingResult: ProcessingResult,
        models: ProcessingPipeline.ResolvedModels,
        intent: ConversationIntent,
        turnId: String
    ): PreparedConversationTurn

    suspend fun runTurn(
        turn: PreparedConversationTurn,
        onAnswerDelta: suspend (String) -> Unit
    ): String
}

internal class ConversationWorkflow(
    private val unifiedClient: UnifiedLLMClient,
    private val pipeline: ProcessingPipeline
) : ConversationWorkflowEngine {
    override suspend fun prepareTurn(
        existingResult: ProcessingResult,
        models: ProcessingPipeline.ResolvedModels,
        intent: ConversationIntent,
        turnId: String
    ): PreparedConversationTurn {
        val provider = when (existingResult.route) {
            ProcessingRoute.OCR_THEN_LLM -> {
                require(models.textModel.isNotBlank()) { "请先在模型设置中配置文本模型" }
                requireNotNull(models.textProvider) { "请先在模型设置中配置文本模型" }
            }
            ProcessingRoute.MULTIMODAL_DIRECT -> {
                pipeline.validateVisionModels(models)
                requireNotNull(models.visionProvider)
            }
        }
        val model = when (existingResult.route) {
            ProcessingRoute.OCR_THEN_LLM -> models.textModel
            ProcessingRoute.MULTIMODAL_DIRECT -> models.visionModel
        }
        val assistantPrompt = when (existingResult.route) {
            ProcessingRoute.OCR_THEN_LLM -> models.assistant.textPrompt
            ProcessingRoute.MULTIMODAL_DIRECT -> models.assistant.visionPrompt
        }.withAnswerStyle(models.answerStyle)
        val (prompt, retainedTurns, retainedVersions) = when (intent) {
            is ConversationIntent.NewTurn -> Triple(
                buildQuotedPrompt(intent.prompt, intent.quotedFragments),
                existingResult.followUpTurns,
                emptyList()
            )
            ConversationIntent.RegenerateLatest -> {
                val latestTurn = existingResult.followUpTurns.lastOrNull()
                    ?: error("找不到要重试的对话轮次")
                Triple(
                    latestTurn.userText,
                    existingResult.followUpTurns.dropLast(1),
                    latestTurn.displayedAssistantVersions()
                )
            }
        }
        val pendingTurn = FollowUpTurn(
            id = turnId,
            userText = when (intent) {
                is ConversationIntent.NewTurn -> intent.prompt
                ConversationIntent.RegenerateLatest -> existingResult.followUpTurns.lastOrNull()?.userText.orEmpty()
            },
            quotedFragments = when (intent) {
                is ConversationIntent.NewTurn -> intent.quotedFragments
                ConversationIntent.RegenerateLatest -> existingResult.followUpTurns.lastOrNull()?.quotedFragments.orEmpty()
            },
            assistantVersions = retainedVersions,
            modelSummary = pipeline.buildModelSummary(model, provider.name)
        )
        val started = existingResult.copy(followUpTurns = retainedTurns + pendingTurn)
        return PreparedConversationTurn(
            historyId = existingResult.id,
            turnId = turnId,
            startedResult = started,
            provider = provider,
            model = model,
            messages = buildMessages(started, assistantPrompt),
            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = models.trustAllHttpsCertificates
        )
    }

    override suspend fun runTurn(
        turn: PreparedConversationTurn,
        onAnswerDelta: suspend (String) -> Unit
    ): String {
        val answer = StringBuilder()
        unifiedClient.streamMessages(
            provider = turn.provider,
            model = turn.model,
            messages = turn.messages,
            firstDeltaTimeoutMillis = turn.firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = turn.trustAllHttpsCertificates
        ).collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> {
                    answer.append(event.text)
                    onAnswerDelta(event.text)
                }
                is LlmEvent.Done -> Unit
                is LlmEvent.ToolCall -> Unit
            }
        }
        return answer.toString()
    }

    private suspend fun buildMessages(
        result: ProcessingResult,
        assistantPrompt: String
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        messages += textMessage(
            role = "system",
            text = assistantPromptWithCopyMarker(assistantPrompt) +
                "\n\n你正在历史记录页面继续与用户对话。请结合首轮题目、首轮回答和后续消息直接回答。"
        )
        val initialPrompt = when (result.route) {
            ProcessingRoute.OCR_THEN_LLM -> result.extractedText.takeIf(String::isNotBlank)?.let {
                "以下是用户通过悬浮窗提交题目的 OCR 结果，请完成任务：\n$it"
            } ?: "用户通过悬浮窗提交了一道题目。"
            ProcessingRoute.MULTIMODAL_DIRECT -> "请直接基于图片内容完成任务。"
        }
        val images = if (result.route == ProcessingRoute.MULTIMODAL_DIRECT) {
            result.loadHistoryBitmaps().map { it.toBase64Jpeg() }
        } else {
            emptyList()
        }
        messages += userMessage(initialPrompt, images)
        messages += textMessage(role = "assistant", text = result.initialAssistantContext())
        result.followUpTurns.forEach { followUp ->
            messages += textMessage(
                role = "user",
                text = buildQuotedPrompt(followUp.userText, followUp.quotedFragments)
            )
            val assistantText = followUp.latestAssistantText()
            if (followUp.completed && assistantText.isNotBlank() && followUp.errorMessage == null) {
                messages += textMessage(role = "assistant", text = assistantText)
            }
        }
        return messages
    }
}

internal fun buildQuotedPrompt(
    prompt: String,
    quotedFragments: List<`fun`.kirari.hanako.core.model.QuotedFragment>
): String {
    val trimmed = prompt.trim()
    if (quotedFragments.isEmpty()) return trimmed
    val quotes = quotedFragments.joinToString("\n\n") { fragment ->
        "[引用片段]\n${fragment.anchor.rawMarkdown}\n[/引用片段]"
    }
    return "$quotes\n\n$trimmed"
}

private fun ProcessingResult.initialAssistantContext(): String {
    latestAnswerText().takeIf(String::isNotBlank)?.let { return it }
    return buildString {
        if (automationThought.isNotBlank()) append(automationThought)
        automationAction?.text?.takeIf(String::isNotBlank)?.let { actionText ->
            if (isNotEmpty()) append("\n\n")
            append("执行结果：")
            append(actionText)
        }
    }.ifBlank { "首轮处理没有生成文本回答。" }
}
