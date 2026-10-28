package `fun`.kirari.hanako.feature.settings.ui.automation

import `fun`.kirari.hanako.feature.settings.ui.settings.SwitchSettingRow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.AutomationSettings

@Composable
internal fun AutoModeSettingsCard(
    automationSettings: AutomationSettings,
    timeoutInput: String,
    hasNotificationPermission: Boolean,
    onTimeoutInputChange: (String) -> Unit,
    onToggleCompletionNotification: (Boolean) -> Unit,
    onOpenNotificationPermission: () -> Unit,
    onToggleStaticMode: (Boolean) -> Unit,
    onNavigateStaticVibrationSettings: () -> Unit,
    onUpdateTimeoutSeconds: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SwitchSettingRow(
            title = "完成后发送通知",
            subtitle = if (hasNotificationPermission) {
                "处理完成后发送系统通知。"
            } else {
                "当前未授予 Hanako 通知权限"
            },
            checked = automationSettings.completionNotificationEnabled,
            onCheckedChange = onToggleCompletionNotification,
            onTextClick = onOpenNotificationPermission,
            subtitleColor = if (hasNotificationPermission) {
                null
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
        SwitchSettingRow(
            title = "静态模式",
            subtitle = "隐藏悬浮球动画，使用振动表示识别结果",
            checked = automationSettings.staticModeEnabled,
            onCheckedChange = onToggleStaticMode,
            onTextClick = onNavigateStaticVibrationSettings
        )
        TimeoutField(
            value = timeoutInput,
            onValueChange = { value ->
                val digits = value.filter(Char::isDigit)
                onTimeoutInputChange(digits)
                digits.toIntOrNull()?.takeIf { it > 0 }?.let(onUpdateTimeoutSeconds)
            }
        )
    }
}

@Composable
private fun TimeoutField(
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "自动模式超时时间",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            "首字延迟超时，当前用于流式请求在收到第一段输出前的等待时间。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("秒") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp)
        )
        Text(
            "默认 30 秒。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
