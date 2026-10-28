package `fun`.kirari.hanako.feature.settings.ui.search

import `fun`.kirari.hanako.feature.settings.ui.settings.SwitchSettingRow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.SearchProviderKind
import `fun`.kirari.hanako.core.data.WebSearchSettings
import `fun`.kirari.hanako.core.data.defaultBaseUrl
import `fun`.kirari.hanako.core.ui.components.SectionCard
import `fun`.kirari.hanako.feature.settings.presentation.WebSearchQuotaState
import `fun`.kirari.hanako.feature.settings.presentation.WebSearchQuotaStatus

@Composable
fun WebSearchSettingsScreen(
    webSearchSettings: WebSearchSettings,
    webSearchQuotaState: WebSearchQuotaState,
    onUpdateWebSearchSettings: ((WebSearchSettings) -> WebSearchSettings) -> Unit,
    onQueryWebSearchQuota: () -> Unit,
    onResetWebSearchQuotaState: () -> Unit
) {
    // 进入页面时，如果是 Tavily 且有 Key，自动查询一次
    LaunchedEffect(Unit) {
        if (webSearchSettings.provider.kind == SearchProviderKind.TAVILY && webSearchSettings.provider.apiKey.isNotBlank()) {
            onQueryWebSearchQuota()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 基础设置模块
        item {
            SectionCard(title = "基础设置") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SwitchSettingRow(
                        title = "启用联网搜索",
                        subtitle = "开启后允许当前任务模型在需要时通过 web_search 工具获取最新信息。",
                        checked = webSearchSettings.enabled,
                        onCheckedChange = { enabled ->
                            onUpdateWebSearchSettings { it.copy(enabled = enabled) }
                        }
                    )

                    SwitchSettingRow(
                        title = "自动模式也使用联网搜索",
                        subtitle = "自动答题模式下也允许调用搜索工具。",
                        checked = webSearchSettings.automationAlsoSearch,
                        onCheckedChange = { enabled ->
                            onUpdateWebSearchSettings { it.copy(automationAlsoSearch = enabled) }
                        }
                    )
                }
            }
        }

        // 服务商配置模块（额度查询内嵌于此）
        item {
            SectionCard(title = "服务商配置") {
                WebSearchProviderConfig(
                    webSearchSettings = webSearchSettings,
                    webSearchQuotaState = webSearchQuotaState,
                    onUpdateWebSearchSettings = onUpdateWebSearchSettings,
                    onQueryWebSearchQuota = onQueryWebSearchQuota,
                    onResetWebSearchQuotaState = onResetWebSearchQuotaState
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebSearchProviderConfig(
    webSearchSettings: WebSearchSettings,
    webSearchQuotaState: WebSearchQuotaState,
    onUpdateWebSearchSettings: ((WebSearchSettings) -> WebSearchSettings) -> Unit,
    onQueryWebSearchQuota: () -> Unit,
    onResetWebSearchQuotaState: () -> Unit
) {
    var showApiKey by remember { mutableStateOf(false) }
    var searchProviderExpanded by remember { mutableStateOf(false) }

    // 监听输入变化：如果用户修改了配置（URL, Key, Kind），重置查询状态（避免边输入边查询）
    var previousProvider by remember { mutableStateOf(webSearchSettings.provider) }
    LaunchedEffect(webSearchSettings.provider) {
        if (previousProvider != webSearchSettings.provider) {
            previousProvider = webSearchSettings.provider
            onResetWebSearchQuotaState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = searchProviderExpanded,
            onExpandedChange = { searchProviderExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = webSearchSettings.provider.kind.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("搜索引擎") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = searchProviderExpanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )

            ExposedDropdownMenu(
                expanded = searchProviderExpanded,
                onDismissRequest = { searchProviderExpanded = false }
            ) {
                SearchProviderKind.entries.forEach { kind ->
                    DropdownMenuItem(
                        text = { Text(kind.displayName, fontWeight = FontWeight.Medium) },
                        onClick = {
                            onUpdateWebSearchSettings {
                                it.copy(
                                    provider = it.provider.copy(
                                        kind = kind,
                                        baseUrl = kind.defaultBaseUrl
                                    )
                                )
                            }
                            searchProviderExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = webSearchSettings.provider.baseUrl,
            onValueChange = { url ->
                onUpdateWebSearchSettings {
                    it.copy(provider = it.provider.copy(baseUrl = url))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("API URL") },
            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = webSearchSettings.provider.apiKey,
            onValueChange = { key ->
                onUpdateWebSearchSettings {
                    it.copy(provider = it.provider.copy(apiKey = key))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("API Key") },
            leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                IconButton(onClick = { showApiKey = !showApiKey }) {
                    Crossfade(targetState = showApiKey, label = "apikey_visibility") { isVisible ->
                        Icon(
                            imageVector = if (isVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (isVisible) "隐藏" else "显示"
                        )
                    }
                }
            }
        )

        // Tavily 专属额度内嵌 UI，作为 API Key 的状态扩展自然地显示在下方
        AnimatedVisibility(
            visible = webSearchSettings.provider.kind == SearchProviderKind.TAVILY,
            enter = fadeIn(tween(300)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(200)) + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow))
        ) {
            InlineTavilyQuotaDisplay(
                state = webSearchQuotaState,
                onQuery = onQueryWebSearchQuota
            )
        }
    }
}

@Composable
private fun InlineTavilyQuotaDisplay(
    state: WebSearchQuotaState,
    onQuery: () -> Unit
) {
    val backgroundColor = when (state.status) {
        WebSearchQuotaStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        WebSearchQuotaStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 左侧状态图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = state.status, label = "inline_quota_icon") { status ->
                    Icon(
                        imageVector = when (status) {
                            WebSearchQuotaStatus.SUCCESS -> Icons.Rounded.CheckCircle
                            WebSearchQuotaStatus.FAILED -> Icons.Rounded.ErrorOutline
                            else -> Icons.Rounded.Wallet
                        },
                        contentDescription = null,
                        tint = when (status) {
                            WebSearchQuotaStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                            WebSearchQuotaStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 中间文字信息
            AnimatedContent(
                targetState = state.status,
                label = "inline_quota_text",
                modifier = Modifier.weight(1f)
            ) { status ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    when (status) {
                        WebSearchQuotaStatus.SUCCESS -> {
                            Text(
                                text = state.summary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = state.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        WebSearchQuotaStatus.FAILED -> {
                            Text(
                                text = "验证失败",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1
                            )
                        }
                        WebSearchQuotaStatus.LOADING -> {
                            Text(
                                text = "正在验证 API Key...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "请求 Tavily 接口中",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        WebSearchQuotaStatus.IDLE -> {
                            Text(
                                text = "未验证",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "点击右侧按钮测试 Key 有效性",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 右侧操作按钮
            if (state.status == WebSearchQuotaStatus.LOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                FilledTonalIconButton(
                    onClick = onQuery,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "查询余额",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
