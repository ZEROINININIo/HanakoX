package `fun`.kirari.hanako.feature.settings.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val DISCLAIMER_CONFIRMATION = "我已阅读并同意"

@Composable
fun DisclaimerConfirmationGate(onConfirmed: () -> Unit) {
    var confirmationInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("进入 Hanako X 前，请确认") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "本应用提供的 AI 回答仅供学习与参考，可能存在错误或不完整内容，请自行核实并对使用结果负责。请遵守学校、考试及相关平台规则，不要将本应用用于违规作弊或其他不当用途。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "请阅读上方说明，并输入“$DISCLAIMER_CONFIRMATION”后继续。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                OutlinedTextField(
                    value = confirmationInput,
                    onValueChange = { confirmationInput = it },
                    modifier = Modifier.padding(top = 4.dp),
                    label = { Text("输入确认语") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmed,
                enabled = confirmationInput.trim() == DISCLAIMER_CONFIRMATION
            ) {
                Text("确认并进入")
            }
        }
    )
}
