package `fun`.kirari.hanako.platform.capture.shizuku

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface BinaryScreencap {
    suspend fun capturePng(): ByteArray
}

internal object ShellScreencap {
    suspend fun captureBitmap(source: BinaryScreencap): Bitmap {
        val png = withContext(Dispatchers.IO) { source.capturePng() }
        return BitmapFactory.decodeByteArray(png, 0, png.size)
            ?: error("无法解码 screencap 输出的 PNG 数据")
    }
}
