@file:OptIn(ExperimentalMaterial3Api::class)

package `fun`.kirari.hanako.core.ui.image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ImagePreviewOverlay(
    visible: Boolean,
    bitmap: Bitmap?,
    fileName: String,
    onDismiss: () -> Unit,
    sourceBounds: android.graphics.Rect? = null
) {
    if (!visible || bitmap == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showSheet by remember { mutableStateOf(false) }

    val animProgress = remember { Animatable(0f) }
    val doubleTapScale = remember { Animatable(1f) }
    val sheetAnimProgress = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        scale = 1f; offsetX = 0f; offsetY = 0f; showSheet = false
        doubleTapScale.snapTo(1f)
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(showSheet) {
        if (showSheet) {
            sheetAnimProgress.snapTo(0f)
            sheetAnimProgress.animateTo(1f, spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow
            ))
        } else {
            sheetAnimProgress.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
        }
    }

    BackHandler(enabled = visible) {
        if (showSheet) {
            showSheet = false
        } else {
            scope.launch {
                animProgress.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                onDismiss()
            }
        }
    }

    val progress = animProgress.value
    val imageScale = 0.9f + 0.1f * progress
    val bgAlpha = progress

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.5f, 5f)
                        scale = newScale
                        if (newScale > 1f) {
                            val maxOffsetX = (newScale - 1f) * size.width / 2f
                            val maxOffsetY = (newScale - 1f) * size.height / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale * doubleTapScale.value * imageScale
                        scaleY = scale * doubleTapScale.value * imageScale
                        translationX = offsetX
                        translationY = offsetY
                        alpha = progress
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                scope.launch {
                animProgress.animateTo(0f, tween(110, easing = FastOutSlowInEasing))
                                    onDismiss()
                                }
                            },
                            onDoubleTap = {
                                scope.launch {
                                    if (doubleTapScale.value > 1.05f) {
                                        doubleTapScale.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        doubleTapScale.animateTo(2f, tween(200, easing = FastOutSlowInEasing))
                                    }
                                }
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showSheet = true
                            }
                        )
                    },
                contentScale = ContentScale.Fit
            )
        }

        if (sheetAnimProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f * sheetAnimProgress.value))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { showSheet = false })
                    }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationY = (1f - sheetAnimProgress.value) * size.height
                    }
            ) {
                ImagePreviewBottomSheet(
                    onSave = {
                        val saved = saveBitmapToPictures(context, bitmap, "$fileName.png")
                        Toast.makeText(
                            context,
                            if (saved) "已保存 1 张图片" else "保存失败，请检查相册权限或存储空间",
                            Toast.LENGTH_SHORT
                        ).show()
                        showSheet = false
                    },
                    onShare = {
                        shareBitmap(context, bitmap, fileName)
                        showSheet = false
                    },
                    onDismiss = { showSheet = false }
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewBottomSheet(
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }
            BottomSheetItem(
                icon = Icons.Default.SaveAlt,
                label = "保存到相册",
                onClick = onSave
            )
            BottomSheetItem(
                icon = Icons.Default.Share,
                label = "分享图片",
                onClick = onShare
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun BottomSheetItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

internal fun saveBitmapToPictures(context: Context, bitmap: Bitmap, fileName: String): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Hanako")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return runCatching {
        resolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: error("openOutputStream failed")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    }.getOrElse {
        resolver.delete(uri, null, null)
        false
    }
}

private fun shareBitmap(context: Context, bitmap: Bitmap, fileName: String) {
    val cacheDir = File(context.cacheDir, "shared_images")
    cacheDir.mkdirs()
    val file = File(cacheDir, "$fileName.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "分享图片"))
}
