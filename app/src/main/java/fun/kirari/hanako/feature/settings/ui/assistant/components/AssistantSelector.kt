package `fun`.kirari.hanako.feature.settings.ui.assistant.components

import `fun`.kirari.hanako.core.ui.components.DraftOutlinedTextField

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.AssistantPreset

@Composable
fun AssistantSelector(
    assistant: AssistantPreset,
    onChange: (AssistantPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DraftOutlinedTextField(
                fieldKey = "${assistant.id}:name",
                value = assistant.name,
                onCommit = { onChange(assistant.copy(name = it)) },
                label = "助手名称"
            )
            DraftOutlinedTextField(
                fieldKey = "${assistant.id}:ocrPrompt",
                value = assistant.ocrPrompt,
                onCommit = { onChange(assistant.copy(ocrPrompt = it)) },
                label = "OCR 模型提示词",
                minLines = 4
            )
            DraftOutlinedTextField(
                fieldKey = "${assistant.id}:textPrompt",
                value = assistant.textPrompt,
                onCommit = { onChange(assistant.copy(textPrompt = it)) },
                label = "LLM 提示词",
                minLines = 5
            )
            DraftOutlinedTextField(
                fieldKey = "${assistant.id}:visionPrompt",
                value = assistant.visionPrompt,
                onCommit = { onChange(assistant.copy(visionPrompt = it)) },
                label = "多模态模型提示词",
                minLines = 5
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI 总结标题", style = MaterialTheme.typography.titleSmall)
                    Text("启用后在答案完成后异步生成标题（调用链将在后续迭代接入）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = assistant.titleSummary.enabled,
                    onCheckedChange = { enabled -> onChange(assistant.copy(titleSummary = assistant.titleSummary.copy(enabled = enabled))) }
                )
            }
            DraftOutlinedTextField(
                fieldKey = "${assistant.id}:titleSummaryPrompt",
                value = assistant.titleSummary.prompt,
                onCommit = { onChange(assistant.copy(titleSummary = assistant.titleSummary.copy(prompt = it))) },
                label = "标题总结提示词",
                minLines = 3
            )
        }
    }
}
