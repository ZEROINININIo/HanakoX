package `fun`.kirari.hanako.core.data

sealed interface HistoryCommandResult {
    data object Success : HistoryCommandResult
    data object NameConflict : HistoryCommandResult
    data object NotFound : HistoryCommandResult
    data class Invalid(val message: String) : HistoryCommandResult
}

internal fun String.normalizedHistoryGroupName(): String = trim().replace(Regex("\\s+"), " ")

internal fun List<HistoryGroup>.hasNameConflict(name: String, excludingId: String? = null): Boolean {
    return any { it.id != excludingId && it.name.equals(name, ignoreCase = true) }
}
