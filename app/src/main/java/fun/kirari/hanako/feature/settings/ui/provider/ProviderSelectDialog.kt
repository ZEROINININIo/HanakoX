package `fun`.kirari.hanako.feature.settings.ui.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.LOCAL_OCR_PROVIDER_ID
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.data.displayName

@Composable
internal fun ProviderSelectDialog(
    providers: List<ModelProviderConfig>,
    includeLocalOcr: Boolean,
    localOcrInstalled: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (includeLocalOcr) {
                        item(LOCAL_OCR_PROVIDER_ID) {
                            LocalOcrProviderButton(
                                localOcrInstalled = localOcrInstalled,
                                onPick = { onPick(LOCAL_OCR_PROVIDER_ID) }
                            )
                        }
                    }
                    items(providers, key = { it.id }) { provider ->
                        ProviderButton(provider = provider, onPick = onPick)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun LocalOcrProviderButton(
    localOcrInstalled: Boolean,
    onPick: () -> Unit
) {
    OutlinedButton(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("本地ML Kit")
            Text(
                if (localOcrInstalled) {
                    "不支持latex公式等识别且准确率较低，正式搜题建议使用在线 OCR 例如 qwen-vl-ocr"
                } else {
                    "点击查看本地 ML Kit 说明"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProviderButton(
    provider: ModelProviderConfig,
    onPick: (String) -> Unit
) {
    OutlinedButton(
        onClick = { onPick(provider.id) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(provider.name)
            Text(
                provider.kind.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
