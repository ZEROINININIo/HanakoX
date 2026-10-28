package `fun`.kirari.llm.core

interface LlmLogger {
    val isDebugEnabled: Boolean get() = false
    val isVerboseEnabled: Boolean get() = false
    fun v(tag: String, message: String) {}
    fun d(tag: String, message: String) {}
    fun i(tag: String, message: String) {}
    fun e(tag: String, message: String, throwable: Throwable? = null) {}
}

object NoopLlmLogger : LlmLogger
