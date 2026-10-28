package `fun`.kirari.hanako.feature.settings.ui.provider

import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.feature.settings.ui.provider.components.ProviderEditor
import `fun`.kirari.hanako.core.ui.components.SectionCard
import `fun`.kirari.hanako.feature.settings.presentation.ConnectionTestState
import `fun`.kirari.hanako.feature.settings.presentation.ConnectionTestStatus

@Composable
fun GenericProviderDetailScreen(
    provider: ModelProviderConfig,
    connectionTestState: ConnectionTestState,
    onUpdateProvider: (ModelProviderConfig) -> Unit,
    onViewModels: () -> Unit,
    onTestConnection: (ModelProviderConfig) -> Unit
) {
    val context = LocalContext.current
    var showDeepSeekGuide by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "提供方配置") {
                ProviderEditor(
                    provider = provider,
                    readOnly = false,
                    onChange = onUpdateProvider,
                    onImportResult = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
        item {
            ProviderOperationsCard(
                provider = provider,
                connectionTestState = connectionTestState,
                onViewModels = onViewModels,
                onTestConnection = onTestConnection
            )
        }
        item {
            SectionCard(title = "DeepSeek 国内 API 配置教程") {
                Text(
                    "使用 DeepSeek 官方 API 时，提供方类型选 OpenAI Compatible，填入官方 Base URL，再粘贴 API Key。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { showDeepSeekGuide = true }) { Text("查看配置步骤") }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (showDeepSeekGuide) {
        AlertDialog(
            onDismissRequest = { showDeepSeekGuide = false },
            title = { Text("配置 DeepSeek API") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. 在 DeepSeek 开放平台登录并创建 API Key。")
                    Text("2. 回到提供方配置，提供方类型选择 OpenAI Compatible。")
                    Text("3. Base URL 填写 https://api.deepseek.com。")
                    Text("4. 填入 API Key；保存后点「查看可用模型」获取当前模型名称，再选择文本模型并测试连接。")
                    Text(
                        "模型名称可能随 DeepSeek 服务更新，以应用获取到的可用模型列表为准。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.deepseek.com/")))
                }) { Text("打开 DeepSeek 平台") }
            },
            dismissButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://api-docs.deepseek.com/quick_start/")))
                }) { Text("官方 API 文档") }
            }
        )
    }
}

@Composable
private fun ProviderOperationsCard(
    provider: ModelProviderConfig,
    connectionTestState: ConnectionTestState,
    onViewModels: () -> Unit,
    onTestConnection: (ModelProviderConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "操作",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Surface(
                    onClick = onViewModels,
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "查看可用模型",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            Icons.Default.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
                ConnectionTestRow(
                    provider = provider,
                    state = connectionTestState,
                    onTestConnection = onTestConnection
                )
            }
        }
    }
}

@Composable
private fun ConnectionTestRow(
    provider: ModelProviderConfig,
    state: ConnectionTestState,
    onTestConnection: (ModelProviderConfig) -> Unit
) {
    val isTesting = state.status == ConnectionTestStatus.TESTING
    val isSuccess = state.status == ConnectionTestStatus.SUCCESS
    val isFail = state.status == ConnectionTestStatus.FAILED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.NetworkCheck,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                "连接测试",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = isSuccess || isFail, enter = fadeIn(), exit = fadeOut()) {
                val tint = if (isSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        if (isSuccess) "${state.latencyMs}ms" else "失败",
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Button(
                onClick = { onTestConnection(provider) },
                enabled = !isTesting,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("开始测试", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
