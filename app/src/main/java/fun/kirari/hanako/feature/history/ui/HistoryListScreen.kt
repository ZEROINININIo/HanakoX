@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package `fun`.kirari.hanako.feature.history.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.HistoryCommandResult
import `fun`.kirari.hanako.core.data.HistoryGroup
import `fun`.kirari.hanako.core.data.HistoryMarkerColor
import `fun`.kirari.hanako.core.data.historyMetadataFor
import `fun`.kirari.hanako.core.data.historyDisplayTitle
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingStatus
import `fun`.kirari.hanako.core.model.decodeHistoryBitmap
import `fun`.kirari.hanako.core.model.loadHistoryBitmap
import `fun`.kirari.hanako.feature.home.presentation.RegisterScrollToTopHandler
import `fun`.kirari.hanako.core.ui.components.SectionCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySubScreen(
    scrollRoute: String,
    settings: AppSettings,
    history: List<ProcessingResult> = settings.history,
    onClearHistory: () -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onOpenHistoryDetail: (String) -> Unit,
    onCreateGroup: (String, (HistoryCommandResult) -> Unit) -> Unit = { _, _ -> },
    onRenameGroup: (String, String, (HistoryCommandResult) -> Unit) -> Unit = { _, _, _ -> },
    onDeleteGroup: (String, (HistoryCommandResult) -> Unit) -> Unit = { _, _ -> },
    onSetGroups: (Set<String>, Set<String>, (HistoryCommandResult) -> Unit) -> Unit = { _, _, _ -> },
    onSetMarkerColor: (Set<String>, HistoryMarkerColor?, (HistoryCommandResult) -> Unit) -> Unit = { _, _, _ -> },
    onCreateQuestionCard: (ProcessingResult) -> Unit = {},
    onCreateQuestionCards: (List<ProcessingResult>) -> Unit = {},
    onOpenGroup: (String) -> Unit = {}
) {
    var actionTargetId by remember { mutableStateOf<String?>(null) }
    var groupPickerTargetIds by remember { mutableStateOf<Set<String>?>(null) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    var deleteGroupTarget by remember { mutableStateOf<HistoryGroup?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var previewId by remember { mutableStateOf<String?>(null) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<HistoryGroup?>(null) }
    val listState = rememberLazyListState()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(previewId) {
        if (previewId != null) {
            kotlinx.coroutines.delay(900)
            previewId = null
        }
    }
    val metadataById = remember(settings.historyMetadata) { settings.historyMetadata.associateBy { it.historyId } }

    BackHandler(enabled = pagerState.currentPage != 0 || selectionMode) {
        when {
            selectionMode -> { selectionMode = false; selectedIds = emptySet(); previewId = null }
            else -> scope.launch { pagerState.animateScrollToPage(0) }
        }
    }
    RegisterScrollToTopHandler(route = scrollRoute) { scope.launch { listState.animateScrollToItem(0) } }

    val visibleHistory = history

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = true,
            modifier = Modifier.fillMaxSize(),
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut()
        ) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                listOf("全部记录", "分组").forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = !selectionMode
            ) { page ->
                if (page == 0) LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                item {
                    HistoryListHeader(
                        title = "全部记录",
                        storageText = formatHistorySize(historyStorageBytes(history)),
                        clearEnabled = history.isNotEmpty() && !selectionMode,
                        selectionMode = selectionMode,
                        selectedCount = selectedIds.size,
                        onClearHistory = onClearHistory,
                        onCloseSelection = { selectionMode = false; selectedIds = emptySet() },
                        onSelectAll = { selectedIds = if (selectedIds.size == visibleHistory.size) emptySet() else visibleHistory.map { it.id }.toSet() }
                    )
                }
                if (visibleHistory.isEmpty()) {
                    item { SectionCard(title = "暂无历史") { Text("悬浮窗处理过的记录会显示在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                } else {
                    items(visibleHistory, key = { it.id }) { result ->
                        val selected = result.id in selectedIds
                        HistoryListItem(
                            result = result,
                            settings = settings,
                            selected = selected,
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) selectedIds = selectedIds.toggle(result.id) else onOpenHistoryDetail(result.id)
                            },
                            onLongClick = {
                                if (selectionMode) {
                                    previewId = result.id
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else {
                                    actionTargetId = result.id
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        )
                        if (previewId == result.id) {
                            HistoryPreviewOverlay(result = result, onDismiss = { previewId = null })
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(88.dp)) }
                } else GroupBrowser(
                    groups = settings.historyGroups,
                    history = history,
                    metadataById = metadataById,
                    onSelectGroup = { id -> id.takeIf { it.isNotEmpty() }?.let(onOpenGroup) },
                    onCreate = { showCreateGroup = true },
                    onRename = { renameTarget = it },
                    onDelete = { deleteGroupTarget = it }
                )
            }
        }
        }
        if (selectionMode) {
            SelectionDock(
                selectedCount = selectedIds.size,
                onMove = { groupPickerTargetIds = selectedIds },
                onColor = { color -> onSetMarkerColor(selectedIds, color) {} },
                onCreateCard = { onCreateQuestionCards(history.filter { it.id in selectedIds }) },
                onDelete = { deleteTargetId = "__batch__" }
            )
        }
    }

    val actionTarget = history.firstOrNull { it.id == actionTargetId }
    if (actionTarget != null) {
        HistoryActionSheet(
            result = actionTarget,
            selectedColor = settings.historyMetadataFor(actionTarget).markerColor,
            onDismiss = { actionTargetId = null },
            onMove = { groupPickerTargetIds = setOf(actionTarget.id); actionTargetId = null },
            onColor = { color -> onSetMarkerColor(setOf(actionTarget.id), color) {}; actionTargetId = null },
            onMultiSelect = { selectionMode = true; selectedIds = setOf(actionTarget.id); actionTargetId = null },
            onCreateCard = { onCreateQuestionCard(actionTarget); actionTargetId = null },
            onDelete = { deleteTargetId = actionTarget.id; actionTargetId = null }
        )
    }

    groupPickerTargetIds?.let { targetIds ->
        GroupPickerSheet(
            groups = settings.historyGroups,
            initial = targetIds.flatMap { id -> settings.historyMetadataFor(history.first { it.id == id }).groupIds }.toSet(),
            onDismiss = { groupPickerTargetIds = null },
            onConfirm = { groupIds -> onSetGroups(targetIds, groupIds) {}; groupPickerTargetIds = null },
            onCreateGroup = { showCreateGroup = true }
        )
    }

    if (showCreateGroup) {
        GroupNameDialog(title = "新建分组", onDismiss = { showCreateGroup = false }) { name ->
            onCreateGroup(name) { showCreateGroup = false }
        }
    }
    renameTarget?.let { group ->
        GroupNameDialog(title = "重命名分组", initial = group.name, onDismiss = { renameTarget = null }) { name ->
            onRenameGroup(group.id, name) { renameTarget = null }
        }
    }
    val deleteTarget = when (deleteTargetId) {
        "__batch__" -> null
        else -> history.firstOrNull { it.id == deleteTargetId }
    }
    if (deleteTargetId == "__batch__") {
        ConfirmDialog("删除所选记录", "确认删除 ${selectedIds.size} 条历史记录？截图、对话和卡片产物都会删除。", onDismiss = { deleteTargetId = null }) {
            selectedIds.forEach(onDeleteHistoryItem)
            selectedIds = emptySet(); selectionMode = false; deleteTargetId = null
        }
    } else if (deleteTarget != null) {
        ConfirmDialog("删除历史记录", "确认删除 ${settings.historyDisplayTitle(deleteTarget)}？截图、对话和卡片产物都会删除。", onDismiss = { deleteTargetId = null }) {
            onDeleteHistoryItem(deleteTarget.id); deleteTargetId = null
        }
    }
    deleteGroupTarget?.let { group ->
        ConfirmDialog("删除分组", "删除“${group.name}”后，记录会保留，只移除分组关系。", onDismiss = { deleteGroupTarget = null }) {
            onDeleteGroup(group.id) { deleteGroupTarget = null }
        }
    }
}

@Composable
private fun HistoryListHeader(
    title: String,
    storageText: String,
    clearEnabled: Boolean,
    selectionMode: Boolean,
    selectedCount: Int,
    onClearHistory: () -> Unit,
    onCloseSelection: () -> Unit,
    onSelectAll: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (selectionMode) "已选 $selectedCount 项" else title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        if (selectionMode) {
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, contentDescription = "全选", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onCloseSelection) { Icon(Icons.Default.ArrowBack, contentDescription = "退出多选", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            Text(storageText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onClearHistory, enabled = clearEnabled) { Text("清空") }
        }
    }
}

@Composable
private fun HistoryListItem(
    result: ProcessingResult,
    settings: AppSettings,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val thumbnail = remember(result.screenshotPath, result.screenshotBase64) { result.screenshotPath?.loadHistoryBitmap() ?: result.screenshotBase64?.decodeHistoryBitmap() }
    val metadata = settings.historyMetadataFor(result)
    val title = settings.historyDisplayTitle(result)
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(visible = selectionMode, enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut()) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    metadata.markerColor?.let { MarkerDot(it) }
                    StatusIcon(result.status)
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    Text(formatHistoryDateTime(metadata.lastActivityAtMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(buildHistoryMetaLine(result), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(historyPreviewText(result), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Surface(modifier = Modifier.size(width = 72.dp, height = 92.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                if (thumbnail != null) Image(thumbnail.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.outline) }
            }
        }
    }
}

@Composable private fun StatusIcon(status: ProcessingStatus) {
    val (icon, color) = when (status) {
        ProcessingStatus.SUCCESS -> Icons.Default.Check to MaterialTheme.colorScheme.tertiary
        ProcessingStatus.RUNNING -> Icons.Default.Memory to MaterialTheme.colorScheme.primary
        ProcessingStatus.ERROR, ProcessingStatus.TIMEOUT -> Icons.Default.Warning to MaterialTheme.colorScheme.error
    }
    Icon(icon, contentDescription = status.displayName(), tint = color, modifier = Modifier.size(18.dp))
}

@Composable private fun MarkerDot(color: HistoryMarkerColor) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(historyMarkerColor(color)))
}

@Composable private fun historyMarkerColor(color: HistoryMarkerColor): Color {
    val dark = isSystemInDarkTheme()
    return when (color) {
        HistoryMarkerColor.MIST_BLUE -> Color(if (dark) 0xFF8FC7F2 else 0xFF276C9E)
        HistoryMarkerColor.SAGE -> Color(if (dark) 0xFF8FD1A2 else 0xFF2D7A43)
        HistoryMarkerColor.LILAC -> Color(if (dark) 0xFFD1A8F0 else 0xFF78459B)
        HistoryMarkerColor.OCHRE -> Color(if (dark) 0xFFF0C56E else 0xFF9A6500)
        HistoryMarkerColor.ROSE -> Color(if (dark) 0xFFF0A2B3 else 0xFFB33D59)
        HistoryMarkerColor.TEAL -> Color(if (dark) 0xFF70D3CC else 0xFF087B78)
        HistoryMarkerColor.SLATE -> Color(if (dark) 0xFFC4CBD2 else 0xFF59636D)
    }
}

private fun buildHistoryMetaLine(result: ProcessingResult): String = buildList {
    add(result.route.displayName())
    result.lastSearchAtMillis?.let { add("搜题 ${formatHistoryDateTime(it)}") }
}.joinToString(" · ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HistoryActionSheet(result: ProcessingResult, selectedColor: HistoryMarkerColor?, onDismiss: () -> Unit, onMove: () -> Unit, onColor: (HistoryMarkerColor?) -> Unit, onMultiSelect: () -> Unit, onCreateCard: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(result.assistantName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("选择对这条记录的操作", color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionRow(Icons.Default.Folder, "移动到分组", onMove)
            Text("标记颜色", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            ColorGrid(selected = selectedColor, onSelect = onColor)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ActionRow(Icons.Default.SelectAll, "多选", onMultiSelect)
            ActionRow(Icons.Default.Share, "创建题目卡片", onCreateCard)
            ActionRow(Icons.Default.DeleteOutline, "删除历史记录", onDelete, MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick, onLongClick = null).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(text, style = MaterialTheme.typography.titleMedium, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GroupPickerSheet(groups: List<HistoryGroup>, initial: Set<String>, onDismiss: () -> Unit, onConfirm: (Set<String>) -> Unit, onCreateGroup: () -> Unit) {
    var selected by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("移动到分组", style = MaterialTheme.typography.titleLarge)
            ActionRow(Icons.Default.Folder, "新建分组", onCreateGroup)
            groups.forEach { group ->
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = { selected = selected.toggle(group.id) }, onLongClick = null).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(selected.contains(group.id), { selected = selected.toggle(group.id) })
                    Text(group.name, modifier = Modifier.weight(1f))
                }
            }
            OutlinedButton(onClick = { onConfirm(selected) }, modifier = Modifier.fillMaxWidth()) { Text("完成") }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable private fun ColorGrid(selected: HistoryMarkerColor?, onSelect: (HistoryMarkerColor?) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        HistoryMarkerColor.entries.forEach { color ->
            IconButton(
                onClick = { onSelect(if (selected == color) null else color) },
                modifier = Modifier.weight(1f)
            ) { Box(Modifier.size(24.dp).clip(CircleShape).background(historyMarkerColor(color))) }
        }
    }
}

@Composable internal fun BoxScope.SelectionDock(selectedCount: Int, onMove: () -> Unit, onColor: (HistoryMarkerColor?) -> Unit, onCreateCard: () -> Unit, onDelete: () -> Unit) {
    var colorsExpanded by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().align(Alignment.BottomCenter), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ActionDockButton(Icons.Default.Folder, "分组", onMove)
            Box {
                ActionDockButton(Icons.Default.MoreVert, "颜色", { colorsExpanded = true })
                DropdownMenu(expanded = colorsExpanded, onDismissRequest = { colorsExpanded = false }) {
                    HistoryMarkerColor.entries.forEach { color ->
                        DropdownMenuItem(text = { Text(color.name) }, onClick = { colorsExpanded = false; onColor(color) })
                    }
                    DropdownMenuItem(text = { Text("清除颜色") }, onClick = { colorsExpanded = false; onColor(null) })
                }
            }
            ActionDockButton(Icons.Default.Share, "卡片", onCreateCard)
            ActionDockButton(Icons.Default.DeleteOutline, "删除", onDelete, MaterialTheme.colorScheme.error)
        }
    }
}

@Composable private fun ActionDockButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = onClick) { Icon(icon, label, tint = tint) }; Text(label, style = MaterialTheme.typography.labelSmall, color = tint) }
}

@Composable
internal fun HistoryGroupDetailScreen(
    groupId: String,
    history: List<ProcessingResult>,
    settings: AppSettings,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    previewId: String?,
    onOpenHistoryDetail: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onLongPress: (ProcessingResult) -> Unit,
    onDismissPreview: () -> Unit
) {
    val records = history.filter { result ->
        if (groupId == "__ungrouped__") settings.historyMetadataFor(result).groupIds.isEmpty()
        else groupId in settings.historyMetadataFor(result).groupIds
    }
    Column(Modifier.fillMaxSize()) {
        if (records.isEmpty()) {
            SectionCard(title = "暂无记录", modifier = Modifier.padding(16.dp)) {
                Text("加入这个分组的历史记录会显示在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(records, key = { it.id }) { result ->
                    HistoryListItem(
                        result = result,
                        settings = settings,
                        selected = result.id in selectedIds,
                        selectionMode = selectionMode,
                        onClick = {
                            if (selectionMode) onToggleSelection(result.id) else onOpenHistoryDetail(result.id)
                        },
                        onLongClick = { onLongPress(result) }
                    )
                    if (previewId == result.id) {
                        HistoryPreviewOverlay(result = result, onDismiss = onDismissPreview)
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable private fun GroupBrowser(groups: List<HistoryGroup>, history: List<ProcessingResult>, metadataById: Map<String, `fun`.kirari.hanako.core.data.HistoryRecordMetadata>, onSelectGroup: (String) -> Unit, onCreate: () -> Unit, onRename: (HistoryGroup) -> Unit, onDelete: (HistoryGroup) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("管理分组", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCreate) { Text("新建分组", color = MaterialTheme.colorScheme.primary) }
        }
        GroupRow("未分组", history.count { metadataById[it.id]?.groupIds.isNullOrEmpty() }, onClick = { onSelectGroup("__ungrouped__") })
        groups.forEach { group ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                GroupRow(group.name, history.count { group.id in (metadataById[it.id]?.groupIds ?: emptyList()) }, onClick = { onSelectGroup(group.id) }, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRename(group) }) { Icon(Icons.Default.Edit, "重命名 ${group.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { onDelete(group) }) { Icon(Icons.Default.DeleteOutline, "删除 ${group.name}", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable private fun GroupRow(name: String, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick, onLongClick = null).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary); Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface); Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable private fun HistoryPreviewOverlay(result: ProcessingResult, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 28.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, tonalElevation = 6.dp) {
        Column(Modifier.padding(14.dp)) { Text(result.assistantName, style = MaterialTheme.typography.titleSmall); Text(historyPreviewText(result), maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)); TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("关闭") } }
    }
}

@Composable internal fun GroupNameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { androidx.compose.material3.OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("分组名称") }) }, confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable internal fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(onClick = onConfirm) { Text("确认", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value
