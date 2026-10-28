package `fun`.kirari.hanako.feature.history.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import `fun`.kirari.hanako.core.model.ProcessingStatus

@Composable
internal fun historyStatusColor(status: ProcessingStatus): Color {
    return when (status) {
        ProcessingStatus.RUNNING -> MaterialTheme.colorScheme.primary
        ProcessingStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
        ProcessingStatus.ERROR -> MaterialTheme.colorScheme.error
        ProcessingStatus.TIMEOUT -> MaterialTheme.colorScheme.error
    }
}

internal fun ProcessingStatus.displayName(): String = when (this) {
    ProcessingStatus.RUNNING -> "进行中"
    ProcessingStatus.SUCCESS -> "成功"
    ProcessingStatus.ERROR -> "失败"
    ProcessingStatus.TIMEOUT -> "超时"
}
