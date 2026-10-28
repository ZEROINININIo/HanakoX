package `fun`.kirari.hanako.solve.runtime
import `fun`.kirari.hanako.solve.model.WorkflowTaskKind
import `fun`.kirari.hanako.solve.model.WorkflowTaskState
import `fun`.kirari.hanako.solve.model.WorkflowTaskStatus

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class WorkflowTaskRegistry {
    private val jobs = mutableMapOf<String, Job>()
    private val _tasks = MutableStateFlow<Map<String, WorkflowTaskState>>(emptyMap())
    val tasks: StateFlow<Map<String, WorkflowTaskState>> = _tasks.asStateFlow()

    fun isRunning(historyId: String): Boolean {
        return _tasks.value.values.any {
            it.historyId == historyId && it.status == WorkflowTaskStatus.RUNNING
        }
    }

    fun register(
        taskId: String,
        historyId: String?,
        kind: WorkflowTaskKind,
        answerVersionIndex: Int? = null,
        conversationTurnId: String? = null
    ) {
        _tasks.update {
            it + (taskId to WorkflowTaskState(
                taskId = taskId,
                historyId = historyId,
                kind = kind,
                answerVersionIndex = answerVersionIndex,
                conversationTurnId = conversationTurnId
            ))
        }
    }

    fun bindHistory(taskId: String, historyId: String) {
        _tasks.update { current ->
            current[taskId]?.let { task ->
                current + (taskId to task.copy(historyId = historyId))
            } ?: current
        }
    }

    fun mark(taskId: String, status: WorkflowTaskStatus, errorMessage: String? = null) {
        _tasks.update { current ->
            current[taskId]?.let { task ->
                current + (taskId to task.copy(status = status, errorMessage = errorMessage))
            } ?: current
        }
    }

    fun trackJob(taskId: String, job: Job) {
        jobs[taskId] = job
        job.invokeOnCompletion {
            if (jobs[taskId] === job) jobs.remove(taskId)
        }
    }

    fun cancelTask(taskId: String) {
        jobs.remove(taskId)?.cancel()
        mark(taskId, WorkflowTaskStatus.CANCELLED)
    }

    fun cancelRunningHistoryTasks(historyId: String) {
        _tasks.value.values
            .filter { it.historyId == historyId && it.status == WorkflowTaskStatus.RUNNING }
            .forEach { cancelTask(it.taskId) }
    }

    fun cancelAll() {
        jobs.keys.toList().forEach(::cancelTask)
        _tasks.value.values
            .filter { it.status == WorkflowTaskStatus.RUNNING }
            .forEach { mark(it.taskId, WorkflowTaskStatus.CANCELLED) }
    }
}
