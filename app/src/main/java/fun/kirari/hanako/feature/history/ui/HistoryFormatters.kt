package `fun`.kirari.hanako.feature.history.ui

import `fun`.kirari.hanako.core.model.AutomationActionType
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.model.ProcessingStatus
import `fun`.kirari.hanako.core.model.latestAnswerText
import java.io.File
import java.util.Calendar
import java.util.Locale

internal fun historyPreviewText(result: ProcessingResult): String {
    return when {
        result.detail.isNotBlank() && result.status != ProcessingStatus.SUCCESS -> result.detail
        result.automationAction != null -> "${automationActionLabel(result)}：${result.automationAction.text}"
        result.automationThought.isNotBlank() -> result.automationThought
        result.latestAnswerText().isNotBlank() -> result.latestAnswerText()
        else -> "暂无回答"
    }
}

internal fun automationActionLabel(result: ProcessingResult): String {
    return when (result.automationAction?.type) {
        AutomationActionType.SET_CLIPBOARD -> "设置剪贴板"
        AutomationActionType.SHOW_BUBBLE_LETTERS -> "显示悬浮球字母"
        null -> "未调用工具"
    }
}

internal fun ProcessingRoute.displayName(): String = when (this) {
    ProcessingRoute.OCR_THEN_LLM -> "OCR"
    ProcessingRoute.MULTIMODAL_DIRECT -> "多模态"
}

internal fun formatHistoryDetailHeader(result: ProcessingResult): String {
    val providerOrModel = result.modelSummary
        .substringAfter('（', missingDelimiterValue = "")
        .substringBefore('）')
        .ifBlank { result.modelSummary }

    return buildList {
        add(result.assistantName)
        add(result.route.displayName())
        providerOrModel.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ")
}

internal fun historyStorageBytes(results: List<ProcessingResult>): Long {
    val fileBytes = results.sumOf { result ->
        result.screenshotPath?.let { path ->
            runCatching { File(path).length() }.getOrDefault(0)
        } ?: 0
    }
    val base64Bytes = results.sumOf { it.screenshotBase64?.length ?: 0 }.toLong()
    return fileBytes + base64Bytes
}

internal fun formatHistorySize(bytes: Long): String {
    if (bytes < 1024L) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1fKB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1fMB", mb)
}

/** Compact, local-time label that stays readable in a dense history card. */
internal fun formatHistoryDateTime(millis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val date = Calendar.getInstance().apply { timeInMillis = millis }
    val todayStart = dayStart(now)
    val dateStart = dayStart(date)
    val dayDelta = ((todayStart.timeInMillis - dateStart.timeInMillis) / DAY_MILLIS).toInt()
    val clock = "%02d:%02d".format(Locale.CHINA, date.get(Calendar.HOUR_OF_DAY), date.get(Calendar.MINUTE))

    if (dayDelta == 0) {
        val elapsedMinutes = ((nowMillis - millis).coerceAtLeast(0L) / MINUTE_MILLIS)
        return when {
            elapsedMinutes < 1L -> "刚刚"
            elapsedMinutes < 60L -> "${elapsedMinutes}分钟前"
            else -> clock
        }
    }
    if (dayDelta == 1) return "昨天 $clock"
    if (dayDelta == 2) return "前天 $clock"

    val monthDay = "${date.get(Calendar.MONTH) + 1}月${date.get(Calendar.DAY_OF_MONTH)}日 $clock"
    return if (date.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
        monthDay
    } else {
        "${date.get(Calendar.YEAR) % 100}年$monthDay"
    }
}

private const val MINUTE_MILLIS = 60_000L
private const val DAY_MILLIS = 24 * 60 * MINUTE_MILLIS

private fun dayStart(source: Calendar): Calendar = Calendar.getInstance().apply {
    timeInMillis = source.timeInMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}
