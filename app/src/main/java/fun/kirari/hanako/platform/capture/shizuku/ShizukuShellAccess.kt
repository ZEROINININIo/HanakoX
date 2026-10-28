package `fun`.kirari.hanako.platform.capture.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val LOG_TAG = "HanakoShizuku"

internal object ShizukuShellScreencap : BinaryScreencap {
    override suspend fun capturePng(): ByteArray {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        Log.i(LOG_TAG, "screencap request started")
        return runCatching {
            val service = ShizukuUserServiceClient.acquire()
            service.exec(arrayOf("sh", "-c", "screencap -p"))
                .also { png ->
                    Log.i(
                        LOG_TAG,
                        "screencap request completed bytes=${png.size} durationMs=${android.os.SystemClock.elapsedRealtime() - startedAt}"
                    )
                }
        }.getOrElse { error ->
            if (error is RemoteException) {
                ShizukuUserServiceClient.invalidate()
            }
            Log.e(LOG_TAG, "screencap request failed", error)
            throw error
        }
    }
}

internal object ShizukuUserServiceClient {
    @Volatile
    private var remoteService: IShizukuShellService? = null

    @Volatile
    private var activeConnection: ServiceConnection? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("fun.kirari.hanako", ShizukuShellService::class.java.name)
    )
        .processNameSuffix("capture")
        .tag("hanako-shizuku-shell")
        .version(2)
        .daemon(false)
        .debuggable(false)

    suspend fun acquire(): IShizukuShellService {
        remoteService
            ?.takeIf { it.asBinder().isBinderAlive }
            ?.let { return it }
        remoteService = null
        check(Shizuku.pingBinder()) { "Shizuku 未运行，请先启动 Shizuku 服务。" }
        Log.i(LOG_TAG, "binding user service")

        return try {
            withTimeout(BIND_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val callbackConnection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            Log.i(LOG_TAG, "user service connected name=$name alive=${service.isBinderAlive}")
                            val remote = IShizukuShellService.Stub.asInterface(service)
                            remoteService = remote
                            activeConnection = this
                            if (continuation.isActive) {
                                continuation.resume(remote)
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            Log.w(LOG_TAG, "user service disconnected name=$name")
                            remoteService = null
                            if (activeConnection === this) activeConnection = null
                        }
                    }
                    runCatching {
                        Shizuku.bindUserService(userServiceArgs, callbackConnection)
                        Log.i(LOG_TAG, "bindUserService dispatched")
                    }.onFailure { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }

                    continuation.invokeOnCancellation {
                        runCatching {
                            Shizuku.unbindUserService(userServiceArgs, callbackConnection, false)
                        }
                    }
                }
            }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            throw IllegalStateException("连接 Shizuku 截图服务超时，请重启 Shizuku 后重试。", error)
        }
    }

    fun invalidate() {
        remoteService = null
        activeConnection?.let { connection ->
            runCatching {
                Shizuku.unbindUserService(userServiceArgs, connection, true)
            }
        }
        activeConnection = null
    }

    private const val BIND_TIMEOUT_MS = 10_000L
}
