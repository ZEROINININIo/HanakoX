package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.core.model.AutomationActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AutomationParsingTest {

    @Test
    fun validateAutomationAction_trimsClipboardText() {
        val action = validateAutomationAction("set_clipboard", "  answer  ")

        assertEquals(AutomationActionType.SET_CLIPBOARD, action.type)
        assertEquals("answer", action.text)
    }

    @Test
    fun validateAutomationAction_acceptsLettersAndChineseSymbols() {
        assertEquals(
            "ABCD",
            validateAutomationAction("show_bubble_letters", "ABCD").text
        )
        assertEquals(
            "对",
            validateAutomationAction("show_bubble_letters", "对").text
        )
        assertEquals(
            "√",
            validateAutomationAction("show_bubble_letters", "√").text
        )
    }

    @Test
    fun validateAutomationAction_rejectsBlankText() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateAutomationAction("set_clipboard", "   ")
        }

        assertEquals("自动模式工具参数不能为空", error.message)
    }

    @Test
    fun validateAutomationAction_rejectsInvalidBubbleLetters() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateAutomationAction("show_bubble_letters", "A-1")
        }

        assertEquals(
            "悬浮球字母必须是 1-8 个英文字母（大小写均可），或'对'、'错'、'√'、'×'",
            error.message
        )
    }

    @Test
    fun validateAutomationAction_rejectsUnknownActionName() {
        val error = assertThrows(IllegalStateException::class.java) {
            validateAutomationAction("unknown", "text")
        }

        assertEquals("未知自动模式工具：unknown", error.message)
    }

    @Test
    fun buildAutomationResult_usesBubbleLettersForChoiceToolOutput() {
        val result = buildAutomationResultFromModelOutput(
            toolName = "show_bubble_letters",
            toolText = " b ",
            thought = "识别为单选题。"
        )

        val action = checkNotNull(result.action)
        assertEquals(AutomationActionType.SHOW_BUBBLE_LETTERS, action.type)
        assertEquals("B", action.text)
    }

    @Test
    fun buildAutomationResult_usesClipboardToolOutputVerbatim() {
        val result = buildAutomationResultFromModelOutput(
            toolName = "set_clipboard",
            toolText = "识别为单选题。题干给出三个向量及“秩等于2”，需要用线性相关/矩阵秩判断 x，再按单选题规则返回对应选项字母。",
            thought = "原始输出"
        )

        assertEquals("原始输出", result.thought)
        val action = checkNotNull(result.action)
        assertEquals(AutomationActionType.SET_CLIPBOARD, action.type)
        assertEquals("识别为单选题。题干给出三个向量及“秩等于2”，需要用线性相关/矩阵秩判断 x，再按单选题规则返回对应选项字母。", action.text)
    }

    @Test
    fun buildAutomationResult_withoutToolKeepsRawOutputAndNoActionText() {
        val result = buildAutomationResultFromModelOutput(
            toolName = null,
            toolText = null,
            thought = "识别为单选题。题干给出三个向量及“秩等于2”，需要用线性相关/矩阵秩判断 x，再按单选题规则返回对应选项字母。"
        )

        assertEquals("识别为单选题。题干给出三个向量及“秩等于2”，需要用线性相关/矩阵秩判断 x，再按单选题规则返回对应选项字母。", result.thought)
        assertNull(result.action)
    }

    @Test
    fun buildAutomationResult_invalidToolKeepsRawOutputAndNoActionText() {
        val result = buildAutomationResultFromModelOutput(
            toolName = "show_bubble_letters",
            toolText = "识别为单选题。",
            thought = "模型原始输出"
        )

        assertEquals("模型原始输出", result.thought)
        assertNull(result.action)
    }
}
