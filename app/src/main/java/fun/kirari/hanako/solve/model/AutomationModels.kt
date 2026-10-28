package `fun`.kirari.hanako.solve.model

import `fun`.kirari.hanako.core.model.AutomationActionRecord
import `fun`.kirari.hanako.core.model.AutomationActionType

data class AutomationResult(
    val thought: String,
    val action: AutomationActionRecord?
)

internal fun bubbleLettersAction(text: String): AutomationActionRecord =
    AutomationActionRecord(
        type = AutomationActionType.SHOW_BUBBLE_LETTERS,
        text = text
    )

internal fun clipboardAction(text: String): AutomationActionRecord =
    AutomationActionRecord(
        type = AutomationActionType.SET_CLIPBOARD,
        text = text
    )
