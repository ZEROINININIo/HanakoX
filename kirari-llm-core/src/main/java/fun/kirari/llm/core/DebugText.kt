package `fun`.kirari.llm.core

fun String.visibleWhitespaceForLog(maxLength: Int = 160): String {
    val escaped = buildString {
        this@visibleWhitespaceForLog.take(maxLength).forEach { ch ->
            when (ch) {
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                ' ' -> append("·")
                else -> append(ch)
            }
        }
        if (this@visibleWhitespaceForLog.length > maxLength) {
            append("...")
        }
    }
    return escaped
}

fun String.sanitizeForLog(): String {
    val dataUrlRegex = Regex("data:image/[^;]+;base64,[A-Za-z0-9+/=]+")
    return dataUrlRegex.replace(this) { match ->
        val payload = match.value.substringAfter("base64,", "")
        "data:image;base64<[${payload.length} chars]>"
    }
}
