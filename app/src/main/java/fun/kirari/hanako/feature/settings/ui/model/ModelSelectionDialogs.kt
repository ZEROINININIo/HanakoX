package `fun`.kirari.hanako.feature.settings.ui.model

import `fun`.kirari.hanako.feature.settings.ui.provider.ProviderSelectDialog

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.BuildConfig
import `fun`.kirari.hanako.platform.clipboard.copyToClipboardWithToast
import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.LOCAL_OCR_MODEL_ID
import `fun`.kirari.hanako.core.data.LOCAL_OCR_PROVIDER_ID
import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.core.data.ModelSelection
import `fun`.kirari.hanako.core.data.availableProviders
import `fun`.kirari.hanako.core.data.displayName
import `fun`.kirari.hanako.core.debug.AppDebugLogEntry
import `fun`.kirari.hanako.core.common.formatDebugTime
import `fun`.kirari.hanako.core.network.ProviderModelsApi
import `fun`.kirari.hanako.feature.settings.ui.model.components.CustomModelDialog
import `fun`.kirari.hanako.feature.settings.ui.model.components.ModelPickerDialog

@Composable
internal fun ModelSelectionDialogs(
    state: ModelSelectionDialogState,
    settings: AppSettings,
    debugEntries: List<AppDebugLogEntry>,
    context: Context,
    onStateChange: (ModelSelectionDialogState) -> Unit,
    onUpdateModelSelection: (ModelPurpose, ModelSelection) -> Unit,
    onUpdateModelSelectionWithFavorite: (ModelPurpose, ModelSelection, Boolean) -> Unit,
    onToggleFavoriteModel: (String, String) -> Unit,
    onSyncLocalOcrInstallation: () -> Unit,
    providerModelsApi: ProviderModelsApi
) {
    ProviderSelectionDialogHost(
        state = state,
        settings = settings,
        onStateChange = onStateChange,
        onUpdateModelSelection = onUpdateModelSelection
    )
    ModelPickerDialogHost(
        state = state,
        settings = settings,
        onStateChange = onStateChange,
        onUpdateModelSelectionWithFavorite = onUpdateModelSelectionWithFavorite,
        onToggleFavoriteModel = onToggleFavoriteModel,
        providerModelsApi = providerModelsApi
    )
    LocalOcrDialogHost(
        state = state,
        settings = settings,
        debugEntries = debugEntries,
        context = context,
        onStateChange = onStateChange,
        onUpdateModelSelection = onUpdateModelSelection,
        onSyncLocalOcrInstallation = onSyncLocalOcrInstallation
    )
    LiteDownloadDialogHost(
        state = state,
        context = context,
        onStateChange = onStateChange
    )
}

internal data class ModelSelectionDialogState(
    val providerModelsPreviewId: String? = null,
    val modelPickerTarget: ModelPurpose? = null,
    val customModelTarget: ModelPurpose? = null,
    val customModelDialogTitle: String? = null,
    val providerPickerTarget: ModelPurpose? = null,
    val modelPickerProviderId: String? = null,
    val showLocalOcrDialog: Boolean = false,
    val showLiteDownloadDialog: Boolean = false
)

@Composable
private fun ProviderSelectionDialogHost(
    state: ModelSelectionDialogState,
    settings: AppSettings,
    onStateChange: (ModelSelectionDialogState) -> Unit,
    onUpdateModelSelection: (ModelPurpose, ModelSelection) -> Unit
) {
    val target = state.providerPickerTarget ?: return
    ProviderSelectDialog(
        providers = settings.availableProviders(),
        includeLocalOcr = target == ModelPurpose.OCR,
        localOcrInstalled = settings.localOcr.installed,
        title = "选择${target.displayName}提供方",
        onDismiss = { onStateChange(state.copy(providerPickerTarget = null)) },
        onPick = { providerId ->
            if (providerId == LOCAL_OCR_PROVIDER_ID) {
                when {
                    !BuildConfig.HAS_MLKIT -> onStateChange(
                        state.copy(providerPickerTarget = null, showLiteDownloadDialog = true)
                    )
                    settings.localOcr.installed -> {
                        onUpdateModelSelection(
                            ModelPurpose.OCR,
                            ModelSelection(providerId = LOCAL_OCR_PROVIDER_ID, model = LOCAL_OCR_MODEL_ID)
                        )
                        onStateChange(state.copy(providerPickerTarget = null))
                    }
                    else -> onStateChange(
                        state.copy(providerPickerTarget = null, showLocalOcrDialog = true)
                    )
                }
            } else {
                onStateChange(
                    state.copy(
                        modelPickerProviderId = providerId,
                        modelPickerTarget = target,
                        providerPickerTarget = null
                    )
                )
            }
        }
    )
}

@Composable
private fun ModelPickerDialogHost(
    state: ModelSelectionDialogState,
    settings: AppSettings,
    onStateChange: (ModelSelectionDialogState) -> Unit,
    onUpdateModelSelectionWithFavorite: (ModelPurpose, ModelSelection, Boolean) -> Unit,
    onToggleFavoriteModel: (String, String) -> Unit,
    providerModelsApi: ProviderModelsApi
) {
    val pickerProvider = settings.availableProviders().firstOrNull { it.id == state.modelPickerProviderId }
    val pickerTarget = state.modelPickerTarget
    if (pickerProvider != null && pickerTarget != null) {
        ModelPickerDialog(
            provider = pickerProvider,
            title = modelPickerTitle(pickerTarget),
            onDismiss = {
                onStateChange(state.copy(modelPickerTarget = null, modelPickerProviderId = null))
            },
            onPick = { model, isFavorite ->
                onUpdateModelSelectionWithFavorite(
                    pickerTarget,
                    ModelSelection(providerId = pickerProvider.id, model = model),
                    isFavorite
                )
                onStateChange(state.copy(modelPickerTarget = null, modelPickerProviderId = null))
            },
            onToggleFavorite = { model, _ ->
                onToggleFavoriteModel(pickerProvider.id, model)
            },
            onCustomModelRequest = { dialogTitle ->
                onStateChange(
                    state.copy(
                        customModelTarget = pickerTarget,
                        customModelDialogTitle = dialogTitle
                    )
                )
            },
            trustAllHttpsCertificates = settings.trustAllHttpsCertificates
            ,
            api = providerModelsApi
        )
    }

    state.customModelDialogTitle?.let { title ->
        CustomModelDialog(
            title = title,
            onDismiss = {
                onStateChange(
                    state.copy(
                        customModelDialogTitle = null,
                        customModelTarget = null,
                        modelPickerProviderId = null
                    )
                )
            },
            onConfirm = { model ->
                val purpose = state.customModelTarget ?: ModelPurpose.TEXT
                val providerId = state.modelPickerProviderId ?: return@CustomModelDialog
                onUpdateModelSelectionWithFavorite(
                    purpose,
                    ModelSelection(providerId = providerId, model = model),
                    true
                )
                onStateChange(
                    state.copy(
                        customModelDialogTitle = null,
                        customModelTarget = null,
                        modelPickerTarget = null,
                        modelPickerProviderId = null
                    )
                )
            }
        )
    }

    val previewProvider = settings.availableProviders().firstOrNull { it.id == state.providerModelsPreviewId }
    if (previewProvider != null) {
        ModelPickerDialog(
            provider = previewProvider,
            title = "查看可用模型",
            onDismiss = { onStateChange(state.copy(providerModelsPreviewId = null)) },
            onPick = { _, _ -> onStateChange(state.copy(providerModelsPreviewId = null)) },
            onToggleFavorite = { model, _ ->
                onToggleFavoriteModel(previewProvider.id, model)
            },
            onCustomModelRequest = { },
            trustAllHttpsCertificates = settings.trustAllHttpsCertificates,
            api = providerModelsApi
        )
    }
}

@Composable
private fun LocalOcrDialogHost(
    state: ModelSelectionDialogState,
    settings: AppSettings,
    debugEntries: List<AppDebugLogEntry>,
    context: Context,
    onStateChange: (ModelSelectionDialogState) -> Unit,
    onUpdateModelSelection: (ModelPurpose, ModelSelection) -> Unit,
    onSyncLocalOcrInstallation: () -> Unit
) {
    if (!state.showLocalOcrDialog) return
    val localOcrLogText = remember(debugEntries) {
        debugEntries
            .filter { it.tag == "LocalOcr" || it.tag == "LocalOcrUi" }
            .takeLast(12)
            .joinToString("\n\n") { entry ->
                "${formatDebugTime(entry.timestamp)} ${entry.level}/${entry.tag}\n${entry.message}"
            }
    }
    AlertDialog(
        onDismissRequest = { onStateChange(state.copy(showLocalOcrDialog = false)) },
        title = { Text("本地ML Kit") },
        text = {
            LocalOcrDialogContent(
                lastMessage = settings.localOcr.lastMessage,
                localOcrLogText = localOcrLogText,
                context = context
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.widthIn(min = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        if (settings.localOcr.installed) {
                            onUpdateModelSelection(
                                ModelPurpose.OCR,
                                ModelSelection(providerId = LOCAL_OCR_PROVIDER_ID, model = LOCAL_OCR_MODEL_ID)
                            )
                            onStateChange(state.copy(showLocalOcrDialog = false))
                        }
                    }
                ) {
                    Text("立即使用")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSyncLocalOcrInstallation) {
                    Text("刷新状态")
                }
                TextButton(onClick = { onStateChange(state.copy(showLocalOcrDialog = false)) }) {
                    Text("取消")
                }
            }
        }
    )
}

@Composable
private fun LiteDownloadDialogHost(
    state: ModelSelectionDialogState,
    context: Context,
    onStateChange: (ModelSelectionDialogState) -> Unit
) {
    if (!state.showLiteDownloadDialog) return
    AlertDialog(
        onDismissRequest = { onStateChange(state.copy(showLiteDownloadDialog = false)) },
        title = { Text("本地ML Kit不可用") },
        text = {
            Text("当前 Lite 版本不包含本地 ML Kit OCR 功能，建议使用 OCR 大模型 API。如需使用此功能，请下载包含 ML Kit 的 Full 版本。")
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStateChange(state.copy(showLiteDownloadDialog = false))
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/zyf2007/HanakoAI")
                        )
                    )
                }
            ) {
                Text("前往下载")
            }
        },
        dismissButton = {
            TextButton(onClick = { onStateChange(state.copy(showLiteDownloadDialog = false)) }) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun LocalOcrDialogContent(
    lastMessage: String?,
    localOcrLogText: String,
    context: Context
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            buildString {
                append("不支持latex公式等识别，正式搜题建议使用qwen-vl-ocr等在线ocr模型。")
                lastMessage?.takeIf { it.isNotBlank() }?.let {
                    append("\n\n")
                    append(it)
                }
                append("\n\n点击“立即使用”后会直接切换到本地 OCR。")
            }
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                copyToClipboardWithToast(
                    context = context,
                    label = "Hanako Local OCR Logs",
                    text = if (localOcrLogText.isNotBlank()) localOcrLogText else "暂无 Local OCR 日志",
                    toastText = "本地 OCR 日志已复制"
                )
            }
        ) {
            Text("一键复制日志")
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Text(
                text = if (localOcrLogText.isBlank()) "暂无 Local OCR 日志" else localOcrLogText,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun modelPickerTitle(purpose: ModelPurpose): String = when (purpose) {
    ModelPurpose.TEXT -> "选择文本模型"
    ModelPurpose.VISION -> "选择多模态模型"
    ModelPurpose.OCR -> "选择 OCR 模型"
}
