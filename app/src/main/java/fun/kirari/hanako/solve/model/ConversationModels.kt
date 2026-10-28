package `fun`.kirari.hanako.solve.model

import `fun`.kirari.hanako.core.model.QuotedFragment

internal sealed interface ConversationIntent {
    data class NewTurn(val prompt: String, val quotedFragments: List<QuotedFragment> = emptyList()) : ConversationIntent
    data object RegenerateLatest : ConversationIntent
}
