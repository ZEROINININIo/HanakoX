package `fun`.kirari.hanako.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

fun formatDebugTime(timestamp: Long): String = synchronized(timeFormatter) {
    timeFormatter.format(Date(timestamp))
}

fun easeOutCubic(fraction: Float): Float {
    val inverse = 1f - fraction
    return 1f - inverse * inverse * inverse
}
