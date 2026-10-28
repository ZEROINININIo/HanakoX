package `fun`.kirari.hanako.feature.settings.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.BuildConfig

@Composable
fun SettingsMenuScreen(
    onNavigateProvider: () -> Unit,
    onNavigateModel: () -> Unit,
    onNavigateWebSearch: () -> Unit,
    onNavigateAssistant: () -> Unit,
    onNavigateMore: () -> Unit,
    onNavigateDebugLogs: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsEntryCard(
                title = "模型提供方",
                subtitle = "配置 API 地址与模型",
                icon = Icons.Default.Build,
                onClick = onNavigateProvider
            )
        }
        item {
            SettingsEntryCard(
                title = "模型设置",
                subtitle = "为 OCR、文本、多模态分别指定提供方和模型",
                icon = Icons.Default.Memory,
                onClick = onNavigateModel
            )
        }
        item {
            SettingsEntryCard(
                title = "联网搜索",
                subtitle = "配置搜索工具、搜索引擎和 API Key",
                icon = Icons.Default.Search,
                onClick = onNavigateWebSearch
            )
        }
        item {
            SettingsEntryCard(
                title = "助手配置",
                subtitle = "管理助手名称与提示词",
                icon = Icons.Default.Person,
                onClick = onNavigateAssistant
            )
        }
        item {
            SettingsEntryCard(
                title = "更多",
                subtitle = "自动模式、屏幕录制方式、网络兼容",
                icon = Icons.Default.SmartToy,
                onClick = onNavigateMore
            )
        }
        item {
            if (BuildConfig.SHOW_DEBUG_LOGS) {
                SettingsEntryCard(
                    title = "调试日志",
                    subtitle = "查看运行日志，便于排查模型、截图与悬浮窗问题",
                    icon = Icons.Default.BugReport,
                    onClick = onNavigateDebugLogs
                )
            }
        }
    }
}

@Composable
private fun SettingsEntryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
