package `fun`.kirari.hanako.feature.settings.ui.provider.components

import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.data.creatableProviderKinds
import `fun`.kirari.hanako.core.data.displayName
import `fun`.kirari.hanako.core.data.parseImportedProviderConfig
import `fun`.kirari.hanako.core.data.requestPreviewUrl
import `fun`.kirari.hanako.core.ui.components.DraftOutlinedTextField
import `fun`.kirari.llm.core.ProviderKind

@Composable
fun ProviderEditor(
    provider: ModelProviderConfig,
    onChange: (ModelProviderConfig) -> Unit,
    onImportResult: (String) -> Unit = {},
    readOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "提供方配置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(
                enabled = !readOnly,
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val text = clipboard
                        ?.takeIf { it.hasPrimaryClip() }
                        ?.primaryClip
                        ?.takeIf { clip ->
                            val description = clipboard.primaryClipDescription
                            description == null ||
                                description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                                description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
                        }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        .orEmpty()
                    val imported = parseImportedProviderConfig(text)
                    if (imported != null) {
                        onChange(
                            provider.copy(
                                kind = imported.kind,
                                name = imported.name ?: provider.name,
                                baseUrl = imported.baseUrl,
                                apiKey = imported.apiKey
                            )
                        )
                        onImportResult("已粘贴导入配置")
                    } else {
                        onImportResult("未识别到支持的提供方配置")
                    }
                }
            ) {
                Text("粘贴")
            }
        }

        ProviderTypeSelector(
            kind = provider.kind,
            availableKinds = if (provider.kind == ProviderKind.KIRARI_NETWORK) {
                ProviderKind.entries.toList()
            } else {
                creatableProviderKinds()
            },
            enabled = !readOnly,
            onChange = { nextKind ->
                onChange(provider.copy(kind = nextKind))
            }
        )

        EditableField(
            fieldKey = "${provider.id}:name",
            value = provider.name,
            onCommit = { onChange(provider.copy(name = it)) },
            label = "提供方名称",
            readOnly = readOnly
        )

        EditableField(
            fieldKey = "${provider.id}:baseUrl",
            value = provider.baseUrl,
            onCommit = { onChange(provider.copy(baseUrl = it)) },
            label = "Base URL",
            readOnly = readOnly
        )

        if (provider.baseUrl.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = provider.requestPreviewUrl(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        EditableField(
            fieldKey = "${provider.id}:apiKey",
            value = provider.apiKey,
            onCommit = { onChange(provider.copy(apiKey = it)) },
            label = "API Key",
            password = true,
            readOnly = readOnly
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProviderTypeSelector(
    kind: ProviderKind,
    availableKinds: List<ProviderKind>,
    enabled: Boolean,
    onChange: (ProviderKind) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = {
            if (enabled) {
                expanded = !expanded
            }
        }
    ) {
        OutlinedTextField(
            value = kind.displayName,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
            label = { Text("提供方类型") },
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            shape = RoundedCornerShape(12.dp),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            availableKinds.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.displayName) },
                    onClick = {
                        expanded = false
                        onChange(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun EditableField(
    fieldKey: String,
    value: String,
    onCommit: (String) -> Unit,
    label: String,
    password: Boolean = false,
    readOnly: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }
    DraftOutlinedTextField(
        fieldKey = fieldKey,
        value = value,
        onCommit = onCommit,
        label = label,
        readOnly = readOnly,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (!password) {
            null
        } else {
            {
                IconButton(onClick = { visible = !visible }, enabled = !readOnly) {
                    Icon(
                        imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (visible) "隐藏密码" else "显示密码"
                    )
                }
            }
        }
    )
}

@Composable
fun ModelButtonField(
    label: String,
    value: String,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value.ifBlank { "未选择" },
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = if (value.isBlank()) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.outline
            )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onPick)
        )
    }
}
