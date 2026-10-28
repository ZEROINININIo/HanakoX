package `fun`.kirari.hanako.feature.history.ui

import `fun`.kirari.hanako.core.model.AutomationActionRecord
import `fun`.kirari.hanako.core.model.AutomationActionType
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.model.ProcessingStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Calendar

class HistoryFormattersTest {

    @Test
    fun historyPreviewText_prefersErrorDetailForNonSuccessResult() {
        val result = ProcessingResult(
            assistantName = "助手",
            route = ProcessingRoute.OCR_THEN_LLM,
            status = ProcessingStatus.ERROR,
            detail = "处理失败",
            answer = "这条不该优先"
        )

        assertEquals("处理失败", historyPreviewText(result))
    }

    @Test
    fun historyPreviewText_prefersAutomationActionThenAnswerThenFallback() {
        val automation = ProcessingResult(
            assistantName = "助手",
            route = ProcessingRoute.OCR_THEN_LLM,
            automationAction = AutomationActionRecord(
                type = AutomationActionType.SET_CLIPBOARD,
                text = "42"
            ),
            answer = "answer"
        )
        val answerOnly = ProcessingResult(
            assistantName = "助手",
            route = ProcessingRoute.MULTIMODAL_DIRECT,
            answer = "最终答案"
        )
        val empty = ProcessingResult(
            assistantName = "助手",
            route = ProcessingRoute.MULTIMODAL_DIRECT
        )

        assertEquals("设置剪贴板：42", historyPreviewText(automation))
        assertEquals("最终答案", historyPreviewText(answerOnly))
        assertEquals("暂无回答", historyPreviewText(empty))
    }

    @Test
    fun automationActionLabel_coversKnownAndMissingTypes() {
        val clipboard = ProcessingResult(
            assistantName = "助手",
            route = ProcessingRoute.OCR_THEN_LLM,
            automationAction = AutomationActionRecord(AutomationActionType.SET_CLIPBOARD, "1")
        )
        val letters = ProcessingResult(
            assistantName = "助手",
            route = ProcessingRoute.OCR_THEN_LLM,
            automationAction = AutomationActionRecord(AutomationActionType.SHOW_BUBBLE_LETTERS, "AB")
        )
        val none = ProcessingResult(
            assistantName = "助手",
            route = ProcessingRoute.OCR_THEN_LLM
        )

        assertEquals("设置剪贴板", automationActionLabel(clipboard))
        assertEquals("显示悬浮球字母", automationActionLabel(letters))
        assertEquals("未调用工具", automationActionLabel(none))
    }

    @Test
    fun formatHistoryDetailHeader_extractsProviderNameFromModelSummary() {
        val result = ProcessingResult(
            assistantName = "题目解答助手",
            route = ProcessingRoute.MULTIMODAL_DIRECT,
            modelSummary = "gpt-4o（OpenAI）"
        )

        assertEquals("题目解答助手 · 多模态 · OpenAI", formatHistoryDetailHeader(result))
    }

    @Test
    fun formatHistoryDetailHeader_fallsBackToWholeModelSummaryWhenNoParentheses() {
        val result = ProcessingResult(
            assistantName = "题目解答助手",
            route = ProcessingRoute.OCR_THEN_LLM,
            modelSummary = "gpt-4.1-mini"
        )

        assertEquals("题目解答助手 · OCR · gpt-4.1-mini", formatHistoryDetailHeader(result))
    }

    @Test
    fun historyStorageBytes_countsExistingFilesAndBase64Length_only() {
        val file = File.createTempFile("history-formatters", ".txt")
        file.writeText("12345")
        file.deleteOnExit()

        val results = listOf(
            ProcessingResult(
                assistantName = "助手",
                route = ProcessingRoute.OCR_THEN_LLM,
                screenshotPath = file.absolutePath
            ),
            ProcessingResult(
                assistantName = "助手",
                route = ProcessingRoute.OCR_THEN_LLM,
                screenshotPath = file.absolutePath + ".missing"
            ),
            ProcessingResult(
                assistantName = "助手",
                route = ProcessingRoute.OCR_THEN_LLM,
                screenshotBase64 = "abcdef"
            )
        )

        assertEquals(11L, historyStorageBytes(results))
    }

    @Test
    fun formatHistorySize_coversBytesKilobytesAndMegabytes() {
        assertEquals("512B", formatHistorySize(512))
        assertEquals("1.5KB", formatHistorySize(1536))
        assertEquals("2.0MB", formatHistorySize(2L * 1024L * 1024L))
    }

    @Test
    fun formatHistoryDateTime_usesRelativeAndCalendarLabels() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 18, 16, 40, 0)
            set(Calendar.MILLISECOND, 0)
        }
        fun offset(days: Int, hours: Int = 0, minutes: Int = 0): Long =
            (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -days)
                add(Calendar.HOUR_OF_DAY, -hours)
                add(Calendar.MINUTE, -minutes)
            }.timeInMillis

        assertEquals("5分钟前", formatHistoryDateTime(offset(0, minutes = 5), now.timeInMillis))
        assertEquals("15:20", formatHistoryDateTime(offset(0, hours = 1, minutes = 20), now.timeInMillis))
        assertEquals("昨天 15:30", formatHistoryDateTime((now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1); set(Calendar.HOUR_OF_DAY, 15); set(Calendar.MINUTE, 30) }.timeInMillis, now.timeInMillis))
        assertEquals("前天 15:30", formatHistoryDateTime((now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2); set(Calendar.HOUR_OF_DAY, 15); set(Calendar.MINUTE, 30) }.timeInMillis, now.timeInMillis))
        assertEquals("8月1日 09:00", formatHistoryDateTime((now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0) }.timeInMillis, now.timeInMillis))
        assertEquals("25年12月31日 23:00", formatHistoryDateTime((now.clone() as Calendar).apply { add(Calendar.YEAR, -1); set(Calendar.MONTH, Calendar.DECEMBER); set(Calendar.DAY_OF_MONTH, 31); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 0) }.timeInMillis, now.timeInMillis))
    }
}
