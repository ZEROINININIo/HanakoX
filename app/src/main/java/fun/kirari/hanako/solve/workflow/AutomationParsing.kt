package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.core.model.AutomationActionType
import `fun`.kirari.hanako.core.model.AutomationActionRecord
import `fun`.kirari.hanako.solve.model.AutomationResult
import `fun`.kirari.hanako.solve.model.bubbleLettersAction
import `fun`.kirari.hanako.solve.model.clipboardAction

private val bubbleLettersPattern = Regex("^[A-Za-z]{1,8}$|^(对|错|√|×)$")

internal fun validateAutomationAction(name: String, text: String): AutomationActionRecord {
    val normalized = text.trim()
    require(normalized.isNotBlank()) { "自动模式工具参数不能为空" }
    return when (name) {
        "set_clipboard" -> clipboardAction(normalized)
        "show_bubble_letters" -> {
            require(bubbleLettersPattern.matches(normalized)) {
                "悬浮球字母必须是 1-8 个英文字母（大小写均可），或'对'、'错'、'√'、'×'"
            }
            bubbleLettersAction(normalized.uppercase())
        }

        else -> error("未知自动模式工具：$name")
    }
}

internal fun buildAutomationResultFromModelOutput(
    toolName: String?,
    toolText: String?,
    thought: String
): AutomationResult {
    val normalizedThought = thought.trim()
    val normalizedToolText = toolText?.trim().orEmpty()
    if (toolName != null) {
        val action = runCatching { validateAutomationAction(toolName, normalizedToolText) }.getOrNull()
        if (action != null) return AutomationResult(normalizedThought, action)
    }

    return AutomationResult(
        thought = normalizedThought,
        action = null
    )
}
