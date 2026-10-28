package `fun`.kirari.hanako.feature.settings.ui.model

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.LOCAL_OCR_PROVIDER_ID
import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.core.data.displayName
import `fun`.kirari.hanako.core.data.resolveModelName
import `fun`.kirari.hanako.core.data.resolveModelProvider

@Composable
fun ModelSettingsScreen(
    settings: AppSettings,
    onPickModel: (ModelPurpose) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 28.dp, bottom = 80.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 顶层大标题区域
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "模型设置",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "为每类任务配置最适合的 AI 模型",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 将设置项收纳进统一的 MD3 大卡片容器中
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column {
                    val purposes = ModelPurpose.entries.toList()
                    purposes.forEachIndexed { index, purpose ->
                        ModelPurposeItem(
                            settings = settings,
                            purpose = purpose,
                            onPick = { onPickModel(purpose) }
                        )

                        // 选项间优雅的细分割线
                        if (index < purposes.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPurposeItem(
    settings: AppSettings,
    purpose: ModelPurpose,
    onPick: () -> Unit
) {
    val provider = settings.resolveModelProvider(purpose)
    val model = settings.resolveModelName(purpose)
    val isLocal = purpose == ModelPurpose.OCR &&
        settings.ocrModelSelection.providerId == LOCAL_OCR_PROVIDER_ID
    val hasSelection = isLocal || provider != null

    val providerLine = when {
        isLocal -> "本地设备"
        provider != null -> provider.name
        else -> "未选择"
    }
    val modelLine = when {
        isLocal -> model
        provider != null && model.isNotBlank() -> model
        else -> ""
    }

Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onPick)
        .padding(horizontal = 16.dp, vertical = 20.dp), // 稍微拉大一点上下间距，代替文字的饱满感
    verticalAlignment = Alignment.CenterVertically
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = purposeIcon(purpose),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp)
        )
    }

    Spacer(modifier = Modifier.width(16.dp))

    // 左侧：仅保留纯粹的功能核心名称
    Text(
        text = "${purpose.displayName}模型",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f) // 一个人吃下左边所有剩余空间
    )

    Spacer(modifier = Modifier.width(16.dp))

    // 右侧：非常宽敞地展示服务商和模型名
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = providerLine,
            style = MaterialTheme.typography.bodyMedium, // 升级到 body 字号
            fontWeight = FontWeight.Medium,
            color = if (hasSelection) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
            textAlign = TextAlign.End
        )
        if (modelLine.isNotBlank()) {
            Text(
                text = modelLine,
                style = MaterialTheme.typography.labelLarge, // 升级到 labelLarge
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
    }

    Spacer(modifier = Modifier.width(12.dp))

    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(20.dp)
    )
}
}

private fun purposeIcon(purpose: ModelPurpose): ImageVector = when (purpose) {
    ModelPurpose.OCR -> Icons.Outlined.DocumentScanner
    ModelPurpose.TEXT -> Icons.AutoMirrored.Outlined.Chat
    ModelPurpose.VISION -> Icons.Outlined.Image
}

private fun purposeHint(purpose: ModelPurpose): String = when (purpose) {
    ModelPurpose.OCR -> "从屏幕截图中识别文字"
    ModelPurpose.TEXT -> "生成解答与文本推理"
    ModelPurpose.VISION -> "理解并分析图像内容"
}
