package `fun`.kirari.hanako.app.navigation

import `fun`.kirari.hanako.feature.home.ui.Screen

internal const val ROUTE_HOME_SHELL = "home_shell"
internal const val ROUTE_HANAKO_HISTORY = "hanako_history"
internal const val ROUTE_HANAKO_HISTORY_DETAIL = "hanako_history_detail"
internal const val ROUTE_HANAKO_HISTORY_GROUP_DETAIL = "hanako_history_group_detail"
internal const val ROUTE_SETTINGS_PROVIDER = "settings_provider"
internal const val ROUTE_SETTINGS_PROVIDER_DETAIL = "settings_provider_detail"
internal const val ROUTE_SETTINGS_MODEL = "settings_model"
internal const val ROUTE_SETTINGS_WEB_SEARCH = "settings_web_search"
internal const val ROUTE_SETTINGS_ASSISTANT = "settings_assistant"
internal const val ROUTE_SETTINGS_ASSISTANT_DETAIL = "settings_assistant_detail"
internal const val ROUTE_SETTINGS_MORE = "settings_more"
internal const val ROUTE_SETTINGS_STATIC_VIBRATION = "settings_static_vibration"
internal const val ROUTE_SETTINGS_DEBUG_LOGS = "settings_debug_logs"
internal const val ARG_PROVIDER_ID = "providerId"
internal const val ARG_ASSISTANT_ID = "assistantId"
internal const val ARG_HISTORY_ID = "historyId"
internal const val ARG_GROUP_ID = "groupId"
internal const val ROUTE_HANAKO_HISTORY_DETAIL_PATTERN = "$ROUTE_HANAKO_HISTORY_DETAIL/{$ARG_HISTORY_ID}"
internal const val ROUTE_HANAKO_HISTORY_GROUP_DETAIL_PATTERN = "$ROUTE_HANAKO_HISTORY_GROUP_DETAIL/{$ARG_GROUP_ID}"
internal const val ROUTE_SETTINGS_PROVIDER_DETAIL_PATTERN = "$ROUTE_SETTINGS_PROVIDER_DETAIL/{$ARG_PROVIDER_ID}"
internal const val ROUTE_SETTINGS_ASSISTANT_DETAIL_PATTERN = "$ROUTE_SETTINGS_ASSISTANT_DETAIL/{$ARG_ASSISTANT_ID}"

internal fun providerDetailRoute(providerId: String): String = "$ROUTE_SETTINGS_PROVIDER_DETAIL/$providerId"
internal fun assistantDetailRoute(assistantId: String): String = "$ROUTE_SETTINGS_ASSISTANT_DETAIL/$assistantId"
internal fun historyDetailRoute(historyId: String): String = "$ROUTE_HANAKO_HISTORY_DETAIL/$historyId"
internal fun historyGroupDetailRoute(groupId: String): String = "$ROUTE_HANAKO_HISTORY_GROUP_DETAIL/$groupId"

internal fun appTitle(route: String?, currentScreen: Screen): String = when (route) {
    ROUTE_HOME_SHELL -> currentScreen.title
        ROUTE_HANAKO_HISTORY -> "历史记录"
    ROUTE_SETTINGS_PROVIDER -> "模型提供方"
    ROUTE_SETTINGS_MODEL -> "模型设置"
    ROUTE_SETTINGS_WEB_SEARCH -> "联网搜索"
    ROUTE_SETTINGS_ASSISTANT -> "助手配置"
    ROUTE_SETTINGS_MORE -> "更多"
    ROUTE_SETTINGS_STATIC_VIBRATION -> "静态模式振动"
    ROUTE_SETTINGS_DEBUG_LOGS -> "调试日志"
    null -> currentScreen.title
    else -> when {
        route.startsWith("$ROUTE_HANAKO_HISTORY_DETAIL/") -> "历史详情"
        route.startsWith("$ROUTE_HANAKO_HISTORY_GROUP_DETAIL/") -> "分组"
        route.startsWith("$ROUTE_SETTINGS_PROVIDER_DETAIL/") -> "编辑提供方"
        route.startsWith("$ROUTE_SETTINGS_ASSISTANT_DETAIL/") -> "编辑助手"
        else -> currentScreen.title
    }
}
