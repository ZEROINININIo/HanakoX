package `fun`.kirari.hanako.core.network

import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.llm.core.LlmLogger

internal object HanakoLlmLogger : LlmLogger {
    override val isDebugEnabled: Boolean get() = AppDebugLogStore.enabled
    override val isVerboseEnabled: Boolean get() = AppDebugLogStore.verboseLlmEnabled
    override fun v(tag: String, message: String) = AppDebugLogStore.v(tag, message)
    override fun d(tag: String, message: String) = AppDebugLogStore.d(tag, message)
    override fun i(tag: String, message: String) = AppDebugLogStore.i(tag, message)
    override fun e(tag: String, message: String, throwable: Throwable?) = AppDebugLogStore.e(tag, message, throwable)
}
