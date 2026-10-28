package `fun`.kirari.hanako.platform.capture.shizuku

import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference

class ShizukuShellService : IShizukuShellService.Stub() {
    override fun exec(command: Array<out String>?): ByteArray {
        val safeCommand = command?.toList().orEmpty()
        require(safeCommand.isNotEmpty()) { "command must not be empty" }

        val process = ProcessBuilder(safeCommand)
            .redirectErrorStream(false)
            .start()
        val stdout = AtomicReference<ByteArray>()
        val stderr = AtomicReference("")
        val stdoutError = AtomicReference<Throwable?>()
        val stdoutThread = Thread({
            runCatching { process.inputStream.use(::readAllBytesCompat) }
                .onSuccess(stdout::set)
                .onFailure(stdoutError::set)
        }, "hanako-screencap-stdout").apply { start() }
        val stderrThread = Thread({
            runCatching { process.errorStream.bufferedReader().use { it.readText() } }
                .onSuccess(stderr::set)
        }, "hanako-screencap-stderr").apply { start() }

        val deadline = SystemClock.elapsedRealtime() + COMMAND_TIMEOUT_MS
        var exitCode: Int? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            exitCode = runCatching { process.exitValue() }.getOrNull()
            if (exitCode != null) break
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MS)
        }
        if (exitCode == null) {
            process.destroy()
            SystemClock.sleep(PROCESS_DESTROY_GRACE_MS)
            if (runCatching { process.exitValue() }.isFailure && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.destroyForcibly()
            }
            Log.e(LOG_TAG, "screencap command timed out")
            error("Shizuku 截屏命令超时，请重启 Shizuku 后重试。")
        }

        stdoutThread.join(STREAM_JOIN_TIMEOUT_MS)
        stderrThread.join(STREAM_JOIN_TIMEOUT_MS)
        stdoutError.get()?.let { throw it }
        val output = stdout.get() ?: error("读取 screencap 输出超时")
        if (exitCode != 0) {
            error(stderr.get().ifBlank { "命令执行失败，退出码=$exitCode" })
        }
        return output
    }

    private companion object {
        const val LOG_TAG = "HanakoShizuku"
        const val COMMAND_TIMEOUT_MS = 15_000L
        const val PROCESS_POLL_INTERVAL_MS = 50L
        const val PROCESS_DESTROY_GRACE_MS = 200L
        const val STREAM_JOIN_TIMEOUT_MS = 1_000L
    }
}

private fun readAllBytesCompat(inputStream: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = inputStream.read(buffer)
        if (count <= 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
