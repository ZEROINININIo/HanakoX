package `fun`.kirari.hanako.app.navigation


import `fun`.kirari.hanako.feature.settings.ui.assistant.AssistantDetailScreen
import `fun`.kirari.hanako.feature.settings.ui.assistant.AssistantSettingsScreen
import `fun`.kirari.hanako.feature.settings.ui.automation.StaticVibrationSettingsScreen
import `fun`.kirari.hanako.feature.settings.ui.debug.DebugLogScreen
import `fun`.kirari.hanako.feature.home.ui.HanakoHomeScreen
import `fun`.kirari.hanako.feature.home.ui.GettingStartedDialog
import `fun`.kirari.hanako.feature.home.ui.MainShellScreen
import `fun`.kirari.hanako.feature.home.ui.Screen
import `fun`.kirari.hanako.feature.settings.ui.model.ModelSelectionDialogState
import `fun`.kirari.hanako.feature.settings.ui.model.ModelSelectionDialogs
import `fun`.kirari.hanako.feature.settings.ui.model.ModelSettingsScreen
import `fun`.kirari.hanako.feature.settings.presentation.ConnectionTestState
import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.feature.history.presentation.HistoryDetailOperation
import `fun`.kirari.hanako.feature.home.presentation.LocalScrollToTopController
import `fun`.kirari.hanako.feature.home.presentation.rememberScrollToTopController
import `fun`.kirari.hanako.feature.settings.ui.provider.GenericProviderDetailScreen
import `fun`.kirari.hanako.feature.settings.ui.provider.KirariProviderDetailScreen
import `fun`.kirari.hanako.feature.settings.ui.provider.ProviderDetailScreen
import `fun`.kirari.hanako.feature.settings.ui.provider.ProviderSettingsScreen
import `fun`.kirari.hanako.feature.settings.ui.search.WebSearchSettingsScreen
import `fun`.kirari.hanako.feature.settings.ui.settings.MoreSettingsScreen
import `fun`.kirari.hanako.feature.settings.ui.settings.SettingsMenuScreen
import `fun`.kirari.hanako.feature.settings.ui.settings.AboutScreen
import `fun`.kirari.hanako.feature.settings.ui.update.AppUpdateDialog

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.app.NotificationManagerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import `fun`.kirari.hanako.app.HanakoApplication
import `fun`.kirari.hanako.platform.capture.ScreenCaptureManager
import `fun`.kirari.hanako.platform.capture.ScreenCaptureStartResult
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.core.data.availableProviders
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import `fun`.kirari.hanako.feature.overlay.state.OverlayRuntimeState
import `fun`.kirari.hanako.feature.overlay.service.OverlayService
import `fun`.kirari.hanako.feature.history.ui.HistoryDetailScreen
import `fun`.kirari.hanako.feature.history.ui.HistorySubScreen
import `fun`.kirari.hanako.feature.history.ui.HistoryGroupDetailScreen
import `fun`.kirari.hanako.feature.history.ui.HistoryActionSheet
import `fun`.kirari.hanako.feature.history.ui.GroupPickerSheet
import `fun`.kirari.hanako.feature.history.ui.GroupNameDialog
import `fun`.kirari.hanako.feature.history.ui.ConfirmDialog
import `fun`.kirari.hanako.feature.history.ui.SelectionDock
import `fun`.kirari.hanako.core.data.historyMetadataFor
import `fun`.kirari.hanako.core.data.historyDisplayTitle
import `fun`.kirari.hanako.core.data.QuestionCardArtifact
import `fun`.kirari.hanako.core.model.loadHistoryBitmap
import `fun`.kirari.hanako.core.ui.image.ImagePreviewOverlay
import `fun`.kirari.hanako.core.ui.image.saveBitmapToPictures

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HanakoApp(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    val debugEntries by AppDebugLogStore.entries.collectAsState()
    val appUpdateState by viewModel.appUpdateState.collectAsState()
    val kirariAuthMessage by viewModel.kirariAuthMessage.collectAsState()
    val kirariAuthenticatedProviderId by viewModel.kirariAuthenticatedProviderId.collectAsState()
    val context = LocalContext.current
    val gettingStartedPrefs = remember(context) {
        context.getSharedPreferences("hanako_x_onboarding", android.content.Context.MODE_PRIVATE)
    }
    var showGettingStarted by remember {
        mutableStateOf(!gettingStartedPrefs.getBoolean("completed", false))
    }
    val dismissGettingStarted = {
        gettingStartedPrefs.edit().putBoolean("completed", true).apply()
        showGettingStarted = false
    }
    val overlayEnabled by OverlayRuntimeState.running.collectAsState()
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotificationPermission by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var modelSelectionDialogState by remember { mutableStateOf(ModelSelectionDialogState()) }
    var historyModelPickerResultId by rememberSaveable { mutableStateOf<String?>(null) }
    var historyQuoteFocused by remember { mutableStateOf(false) }
    var questionCardPreview by remember { mutableStateOf<QuestionCardArtifact?>(null) }
    val previewQuestionCard: (String) -> Unit = { resultId ->
        viewModel.createQuestionCard(resultId) { artifact ->
            if (artifact == null) {
                Toast.makeText(context, "题目卡片创建失败", Toast.LENGTH_SHORT).show()
            } else {
                questionCardPreview = artifact
                Toast.makeText(context, "长按保存", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val saveQuestionCards: (List<String>) -> Unit = { resultIds ->
        if (resultIds.isNotEmpty()) {
            var completed = 0
            var saved = 0
            resultIds.forEach { resultId ->
                viewModel.createQuestionCard(resultId) { artifact ->
                    artifact?.path?.loadHistoryBitmap()?.let { bitmap ->
                        if (saveBitmapToPictures(context, bitmap, "hanako_question_card_${artifact.id}.png")) saved++
                    }
                    completed++
                    if (completed == resultIds.size) {
                        val failed = resultIds.size - saved
                        Toast.makeText(
                            context,
                            if (failed == 0) "已保存 ${saved} 张图片" else if (saved == 0) "保存失败，请检查相册权限或存储空间" else "已保存 ${saved} 张图片，${failed} 张保存失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
    val providerModelsApi = remember { HanakoApplication.instance.container.providerModelsApi }
    val scrollToTopController = rememberScrollToTopController()

    LaunchedEffect(modelSelectionDialogState) {
        if (
            historyModelPickerResultId != null &&
            modelSelectionDialogState.providerPickerTarget == null &&
            modelSelectionDialogState.modelPickerTarget == null &&
            modelSelectionDialogState.customModelTarget == null &&
            modelSelectionDialogState.customModelDialogTitle == null
        ) {
            historyModelPickerResultId = null
        }
    }

    var currentScreen by rememberSaveable { mutableStateOf(Screen.Hanako) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topBarScrollToTopEnabled = scrollToTopController.canScrollToTop(currentRoute)

    LaunchedEffect(currentRoute) {
        if (currentRoute?.startsWith("$ROUTE_HANAKO_HISTORY_DETAIL/") != true) {
            historyQuoteFocused = false
        }
    }

    LaunchedEffect(kirariAuthMessage) {
        val message = kirariAuthMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeKirariAuthMessage()
    }

    LaunchedEffect(kirariAuthenticatedProviderId) {
        val providerId = kirariAuthenticatedProviderId ?: return@LaunchedEffect
        currentScreen = Screen.Settings
        navController.navigate(providerDetailRoute(providerId)) {
            launchSingleTop = true
            restoreState = true
        }
        viewModel.consumeKirariAuthenticatedProvider()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = currentRoute == ROUTE_HOME_SHELL && currentScreen == Screen.Settings) {
        currentScreen = Screen.Hanako
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Scaffold(
            modifier = if (historyQuoteFocused) Modifier.blur(1.5.dp) else Modifier,
            topBar = {
                CenterAlignedTopAppBar(
                    modifier = Modifier
                        .then(if (topBarScrollToTopEnabled) {
                            Modifier.clickable { scrollToTopController.scrollToTop(currentRoute) }
                        } else Modifier),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (currentRoute?.startsWith("$ROUTE_HANAKO_HISTORY_GROUP_DETAIL/") == true) {
                                    val groupId = backStackEntry?.arguments?.getString(ARG_GROUP_ID)
                                    settings.historyGroups.firstOrNull { it.id == groupId }?.name ?: "分组"
                                } else appTitle(currentRoute, currentScreen),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (
                                currentRoute == ROUTE_HOME_SHELL &&
                                currentScreen == Screen.Hanako &&
                                appUpdateState.availableUpdate != null
                            ) {
                                UpgradeIconButton(onClick = viewModel::showUpdateDialog)
                            }
                        }
                    },
                    navigationIcon = {
                        if (currentRoute != null && currentRoute != ROUTE_HOME_SHELL) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = currentRoute == ROUTE_HOME_SHELL,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Screen.entries.forEach { screen ->
                            NavigationBarItem(
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) }
                            )
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            CompositionLocalProvider(LocalScrollToTopController provides scrollToTopController) {
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME_SHELL,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(padding),
                    enterTransition = {
                        slideInHorizontally { it } + fadeIn()
                    },
                    exitTransition = {
                        slideOutHorizontally { -it / 2 } + fadeOut()
                    },
                    popEnterTransition = {
                        slideInHorizontally { -it / 2 } + fadeIn()
                    },
                    popExitTransition = {
                        slideOutHorizontally { it }
                    }
                ) {
                    composable(ROUTE_HOME_SHELL) {
                        MainShellScreen(
                            currentScreen = currentScreen,
                            onScreenChange = { currentScreen = it },
                            hanakoContent = {
                                HanakoHomeScreen(
                                    settings = settings,
                                    overlayEnabled = overlayEnabled,
                                    hasOverlayPermission = hasOverlayPermission,
                                    onOpenOverlayPermission = {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                        )
                                    },
                                    onToggleOverlay = { enabled ->
                                        if (enabled) {
                                            when (
                                                val result = ScreenCaptureManager.requestStart(
                                                    context = context,
                                                    method = settings.screenCaptureMethod,
                                                    launchMode = CaptureLaunchMode.NORMAL
                                                )
                                            ) {
                                                ScreenCaptureStartResult.Started -> Unit
                                                is ScreenCaptureStartResult.UserActionRequired -> {
                                                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                                }
                                                is ScreenCaptureStartResult.Failed -> {
                                                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            context.stopService(Intent(context, OverlayService::class.java))
                                            ScreenCaptureManager.stop(context, settings.screenCaptureMethod)
                                        }
                                    },
                                    onSelectRoute = viewModel::setRoute,
                                    onToggleAnswerStyle = viewModel::toggleAnswerStyle,
                                    onOpenHistory = { navController.navigate(ROUTE_HANAKO_HISTORY) }
                                )
                            },
                            settingsContent = {
                                SettingsMenuScreen(
                                    onNavigateProvider = { navController.navigate(ROUTE_SETTINGS_PROVIDER) },
                                    onNavigateModel = { navController.navigate(ROUTE_SETTINGS_MODEL) },
                                    onNavigateWebSearch = { navController.navigate(ROUTE_SETTINGS_WEB_SEARCH) },
                                    onNavigateAssistant = { navController.navigate(ROUTE_SETTINGS_ASSISTANT) },
                                    onNavigateMore = { navController.navigate(ROUTE_SETTINGS_MORE) },
                                    onNavigateDebugLogs = { navController.navigate(ROUTE_SETTINGS_DEBUG_LOGS) },
                                    onNavigateAbout = { navController.navigate(ROUTE_SETTINGS_ABOUT) }
                                )
                            }
                        )
                    }
                    composable(ROUTE_HANAKO_HISTORY) {
                        val mergedHistory by viewModel.mergedHistory.collectAsState()
                        HistorySubScreen(
                            scrollRoute = ROUTE_HANAKO_HISTORY,
                            settings = settings,
                            history = mergedHistory,
                            onClearHistory = viewModel::clearHistory,
                            onDeleteHistoryItem = viewModel::deleteHistoryItem,
                            onOpenHistoryDetail = { resultId ->
                                navController.navigate(historyDetailRoute(resultId))
                            },
                            onCreateGroup = viewModel::createHistoryGroup,
                            onRenameGroup = viewModel::renameHistoryGroup,
                            onDeleteGroup = viewModel::deleteHistoryGroup,
                            onSetGroups = viewModel::setHistoryGroups,
                            onSetMarkerColor = viewModel::setHistoryMarkerColor,
                            onCreateQuestionCard = { result -> previewQuestionCard(result.id) },
                            onCreateQuestionCards = { results -> saveQuestionCards(results.map { it.id }) },
                            onOpenGroup = { groupId -> navController.navigate(historyGroupDetailRoute(groupId)) }
                        )
                    }
                    composable(ROUTE_HANAKO_HISTORY_GROUP_DETAIL_PATTERN) { entry ->
                        val groupId = entry.arguments?.getString(ARG_GROUP_ID) ?: return@composable
                        val mergedHistory by viewModel.mergedHistory.collectAsState()
                        var actionTargetId by remember { mutableStateOf<String?>(null) }
                        var groupPickerTargetIds by remember { mutableStateOf<Set<String>?>(null) }
                        var deleteTargetIds by remember { mutableStateOf<Set<String>?>(null) }
                        var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
                        var selectionMode by remember { mutableStateOf(false) }
                        var previewId by remember { mutableStateOf<String?>(null) }
                        var showCreateGroup by remember { mutableStateOf(false) }
                        Box(Modifier.fillMaxSize()) {
                            HistoryGroupDetailScreen(
                                groupId = groupId,
                                history = mergedHistory,
                                settings = settings,
                                selectedIds = selectedIds,
                                selectionMode = selectionMode,
                                previewId = previewId,
                                onOpenHistoryDetail = { resultId -> navController.navigate(historyDetailRoute(resultId)) },
                                onToggleSelection = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                                onLongPress = { result ->
                                    if (selectionMode) previewId = result.id else actionTargetId = result.id
                                },
                                onDismissPreview = { previewId = null }
                            )
                            if (selectionMode) {
                                SelectionDock(
                                    selectedCount = selectedIds.size,
                                    onMove = { groupPickerTargetIds = selectedIds },
                                    onColor = { color -> viewModel.setHistoryMarkerColor(selectedIds, color) {} },
                                    onCreateCard = { saveQuestionCards(selectedIds.toList()) },
                                    onDelete = { deleteTargetIds = selectedIds }
                                )
                            }
                        }
                        val actionTarget = mergedHistory.firstOrNull { it.id == actionTargetId }
                        if (actionTarget != null) {
                            HistoryActionSheet(
                                result = actionTarget,
                                selectedColor = settings.historyMetadataFor(actionTarget).markerColor,
                                onDismiss = { actionTargetId = null },
                                onMove = { groupPickerTargetIds = setOf(actionTarget.id); actionTargetId = null },
                                onColor = { color -> viewModel.setHistoryMarkerColor(setOf(actionTarget.id), color) {}; actionTargetId = null },
                                onMultiSelect = { selectionMode = true; selectedIds = setOf(actionTarget.id); actionTargetId = null },
                                onCreateCard = { previewQuestionCard(actionTarget.id); actionTargetId = null },
                                onDelete = { deleteTargetIds = setOf(actionTarget.id); actionTargetId = null }
                            )
                        }
                        groupPickerTargetIds?.let { targetIds ->
                            GroupPickerSheet(
                                groups = settings.historyGroups,
                                initial = targetIds.flatMap { id -> mergedHistory.firstOrNull { it.id == id }?.let(settings::historyMetadataFor)?.groupIds.orEmpty() }.toSet(),
                                onDismiss = { groupPickerTargetIds = null },
                                onConfirm = { groupIds ->
                                    viewModel.setHistoryGroups(targetIds, groupIds) {
                                        groupPickerTargetIds = null
                                        selectedIds = emptySet()
                                        selectionMode = false
                                    }
                                },
                                onCreateGroup = { showCreateGroup = true }
                            )
                        }
                        if (showCreateGroup) {
                            GroupNameDialog("新建分组", onDismiss = { showCreateGroup = false }) { name ->
                                viewModel.createHistoryGroup(name) { showCreateGroup = false }
                            }
                        }
                        deleteTargetIds?.let { ids ->
                            ConfirmDialog(
                                title = "删除历史记录",
                                message = if (ids.size == 1) {
                                    mergedHistory.firstOrNull { it.id in ids }?.let { "确认删除 ${settings.historyDisplayTitle(it)}？" } ?: "确认删除这条记录？"
                                } else "确认删除 ${ids.size} 条历史记录？",
                                onDismiss = { deleteTargetIds = null },
                                onConfirm = {
                                    ids.forEach(viewModel::deleteHistoryItem)
                                    deleteTargetIds = null
                                    selectedIds = emptySet()
                                    selectionMode = false
                                }
                            )
                        }
                    }
                    composable(ROUTE_HANAKO_HISTORY_DETAIL_PATTERN) { entry ->
                        val resultId = entry.arguments?.getString(ARG_HISTORY_ID)
                        val detailStates by viewModel.historyDetailStates.collectAsState()
                        val detailState = resultId?.let(detailStates::get)
                        val operation = detailState?.operation
                        val conversationModelPurpose = detailState?.conversationModelPurpose
                        HistoryDetailScreen(
                            scrollRoute = ROUTE_HANAKO_HISTORY_DETAIL_PATTERN,
                            result = detailState?.result,
                            regenerating = operation is HistoryDetailOperation.RegeneratingInitialAnswer,
                            chatSending = operation is HistoryDetailOperation.SendingFollowUp,
                            runningAnswerVersionIndex =
                                (operation as? HistoryDetailOperation.RegeneratingInitialAnswer)?.answerVersionIndex,
                            conversationModelLabel = detailState?.conversationModelLabel ?: "选择模型",
                            onRegenerate = { viewModel.regenerateHistoryResult(it.id) },
                            onSendFollowUp = { prompt, quotedFragments ->
                                resultId?.let { viewModel.sendHistoryFollowUp(it, prompt, quotedFragments) }
                            },
                            onSelectConversationModel = {
                                if (resultId != null && conversationModelPurpose != null) {
                                    historyModelPickerResultId = resultId
                                    modelSelectionDialogState = modelSelectionDialogState.copy(
                                        providerPickerTarget = conversationModelPurpose
                                    )
                                }
                            },
                            onRetryFollowUp = {
                                resultId?.let { viewModel.retryLatestHistoryFollowUp(it) }
                            },
                            onBlockFocusChanged = { historyQuoteFocused = it }
                        )
                    }
                    composable(ROUTE_SETTINGS_PROVIDER) {
                        ProviderSettingsScreen(
                            scrollRoute = ROUTE_SETTINGS_PROVIDER,
                            settings = settings,
                            onAddProvider = viewModel::addProvider,
                            onDeleteProvider = viewModel::deleteProvider,
                            onOpenProvider = { providerId ->
                                viewModel.selectProvider(providerId)
                                navController.navigate(providerDetailRoute(providerId))
                            }
                        )
                    }
                    composable(ROUTE_SETTINGS_PROVIDER_DETAIL_PATTERN) { entry ->
                        val providerId = entry.arguments?.getString(ARG_PROVIDER_ID)
                        val provider = settings.availableProviders().firstOrNull { it.id == providerId }
                        if (provider != null) {
                        val connectionTestStates by viewModel.connectionTestManager.states.collectAsState()
                        val connectionTestState = connectionTestStates[provider.id] ?: ConnectionTestState()
                        val providerMetaState by viewModel.providerMetaState.collectAsState()
                        val kirariAccountState by viewModel.kirariAccountState.collectAsState()
                        ProviderDetailScreen(
                            provider = provider,
                            connectionTestState = connectionTestState,
                            providerMetaState = providerMetaState,
                            kirariAccountState = kirariAccountState,
                            hasKirariClientId = viewModel.hasKirariClientId(),
                            onUpdateProvider = viewModel::updateProvider,
                            onViewModels = {
                                modelSelectionDialogState = modelSelectionDialogState.copy(
                                    providerModelsPreviewId = provider.id
                                )
                            },
                            onTestConnection = viewModel::testProviderConnection,
                            onClearConnectionTest = { viewModel.resetConnectionTest(provider.id) },
                            onLoadProviderMeta = viewModel::loadProviderMeta,
                            onClearProviderMeta = viewModel::resetProviderMeta,
                            shouldSuggestKirariAutoSetup = viewModel.shouldSuggestKirariAutoSetup(settings, providerMetaState),
                            onApplyKirariAutoSetup = viewModel::applyKirariAutoSetup,
                            onLoginKirari = {
                                viewModel.startKirariLogin { authorizationUrl ->
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl))
                                    )
                                }
                            },
                            onLogoutKirari = viewModel::logoutKirari
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                    }
                    composable(ROUTE_SETTINGS_MODEL) {
                        ModelSettingsScreen(
                            settings = settings,
                            onPickModel = {
                                modelSelectionDialogState = modelSelectionDialogState.copy(
                                    providerPickerTarget = it
                                )
                            }
                        )
                    }
                    composable(ROUTE_SETTINGS_WEB_SEARCH) {
                        val webSearchQuotaState by viewModel.webSearchQuotaState.collectAsState()
                        WebSearchSettingsScreen(
                            webSearchSettings = settings.webSearch,
                            webSearchQuotaState = webSearchQuotaState,
                            onUpdateWebSearchSettings = { transform ->
                                viewModel.updateWebSearchSettings(transform)
                            },
                            onQueryWebSearchQuota = viewModel::queryWebSearchQuota,
                            onResetWebSearchQuotaState = viewModel::resetWebSearchQuotaState
                        )
                    }
                    composable(ROUTE_SETTINGS_ASSISTANT) {
                        AssistantSettingsScreen(
                            settings = settings,
                            onAddAssistant = viewModel::addAssistant,
                            onDeleteAssistant = viewModel::deleteAssistant,
                            onSelectAssistant = viewModel::selectAssistant,
                            onOpenAssistant = { assistantId ->
                                navController.navigate(assistantDetailRoute(assistantId))
                            }
                        )
                    }
                    composable(ROUTE_SETTINGS_ASSISTANT_DETAIL_PATTERN) { entry ->
                        val assistantId = entry.arguments?.getString(ARG_ASSISTANT_ID)
                        val assistant = settings.assistants.firstOrNull { it.id == assistantId }
                        if (assistant != null) {
                            AssistantDetailScreen(
                                assistant = assistant,
                                onUpdateAssistant = viewModel::updateAssistant
                            )
                        } else {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        }
                    }
                    composable(ROUTE_SETTINGS_MORE) {
                        MoreSettingsScreen(
                            scrollRoute = ROUTE_SETTINGS_MORE,
                            automationSettings = settings.automation,
                            selectedMethod = settings.screenCaptureMethod,
                            trustAllHttpsCertificates = settings.trustAllHttpsCertificates,
                            kirariSettings = settings.kirari,
                            hasKirariClientId = viewModel.hasKirariClientId(),
                            hasNotificationPermission = hasNotificationPermission,
                            onToggleCompletionNotification = { enabled ->
                                viewModel.updateAutomationSettings {
                                    it.copy(completionNotificationEnabled = enabled)
                                }
                            },
                            onOpenNotificationPermission = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                runCatching {
                                    context.startActivity(intent)
                                }.onFailure {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            },
                            onToggleStaticMode = { enabled ->
                                viewModel.updateAutomationSettings {
                                    it.copy(staticModeEnabled = enabled)
                                }
                            },
                            onNavigateStaticVibrationSettings = { navController.navigate(ROUTE_SETTINGS_STATIC_VIBRATION) },
                            onUpdateAutomationSettings = { automationSettings ->
                                viewModel.updateAutomationSettings { automationSettings }
                            },
                            onSelectMethod = viewModel::setScreenCaptureMethod,
                            onUpdateTimeoutSeconds = { seconds ->
                                viewModel.updateAutomationSettings {
                                    it.copy(autoModeTimeoutSeconds = seconds)
                                }
                            },
                            onToggleTrustAllHttpsCertificates = viewModel::setTrustAllHttpsCertificates,
                            onUpdateKirariServerUrl = { serverUrl ->
                                viewModel.updateKirariSettings { it.copy(serverUrl = serverUrl.trim()) }
                            },
                            onLoginKirari = {
                                viewModel.startKirariLogin { authorizationUrl ->
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl))
                                    )
                                }
                            },
                            onLogoutKirari = viewModel::logoutKirari,
                            onOpenGettingStarted = { showGettingStarted = true }
                        )
                    }
                    composable(ROUTE_SETTINGS_STATIC_VIBRATION) {
                        StaticVibrationSettingsScreen(
                            automationSettings = settings.automation,
                            onUpdateSettings = { transform ->
                                viewModel.updateAutomationSettings(transform)
                            }
                        )
                    }
                    composable(ROUTE_SETTINGS_DEBUG_LOGS) {
                        DebugLogScreen(
                            onClearLogs = viewModel::clearDebugLogs
                        )
                    }
                    composable(ROUTE_SETTINGS_ABOUT) {
                        AboutScreen()
                    }
                }
            }
        }
    }

    questionCardPreview?.let { artifact ->
        val bitmap = remember(artifact.path) { artifact.path.loadHistoryBitmap() }
        if (bitmap != null) {
            ImagePreviewOverlay(
                visible = true,
                bitmap = bitmap,
                fileName = "hanako_question_card_${artifact.id}",
                onDismiss = { questionCardPreview = null }
            )
        } else {
            LaunchedEffect(artifact.path) {
                Toast.makeText(context, "题目卡片预览失败", Toast.LENGTH_SHORT).show()
                questionCardPreview = null
            }
        }
    }

    ModelSelectionDialogs(
        state = modelSelectionDialogState,
        settings = settings,
        debugEntries = debugEntries,
        context = context,
        onStateChange = { modelSelectionDialogState = it },
        onUpdateModelSelection = { purpose, selection ->
            val resultId = historyModelPickerResultId
            if (resultId != null) {
                viewModel.selectHistoryConversationModel(resultId, selection)
                historyModelPickerResultId = null
            } else {
                viewModel.updateModelSelection(purpose, selection)
            }
        },
        onUpdateModelSelectionWithFavorite = { purpose, selection, favoriteModel ->
            val resultId = historyModelPickerResultId
            if (resultId != null) {
                viewModel.selectHistoryConversationModel(
                    resultId = resultId,
                    selection = selection,
                    addToFavorites = favoriteModel
                )
                historyModelPickerResultId = null
            } else {
                viewModel.updateModelSelectionWithFavorite(purpose, selection, favoriteModel)
            }
        },
        onToggleFavoriteModel = viewModel::toggleFavoriteModel,
        onSyncLocalOcrInstallation = viewModel::syncLocalOcrInstallation,
        providerModelsApi = providerModelsApi
    )

    val availableUpdate = appUpdateState.availableUpdate
    if (availableUpdate != null && appUpdateState.dialogVisible) {
        AppUpdateDialog(
            update = availableUpdate,
            onDismiss = viewModel::dismissUpdateDialog
        )
    }

    if (showGettingStarted) {
        GettingStartedDialog(onDismiss = dismissGettingStarted)
    }
}

@Composable
private fun UpgradeIconButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(20.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "查看更新",
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
