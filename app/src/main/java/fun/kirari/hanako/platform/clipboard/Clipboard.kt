package `fun`.kirari.hanako.platform.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun copyToClipboardWithToast(
    context: Context,
    label: String,
    text: String,
    toastText: String
) {
    copyToClipboard(context, label, text)
    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
}
