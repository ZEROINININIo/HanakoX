package `fun`.kirari.hanako.solve.runtime

import `fun`.kirari.hanako.solve.model.WorkflowTaskKind
import `fun`.kirari.hanako.solve.model.WorkflowTaskStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkflowTaskRegistryTest {

    @Test
    fun registerAndMark_exposesTaskStateThroughStateFlow() = runTest {
        val registry = WorkflowTaskRegistry()

        registry.register(
            taskId = "task-1",
            historyId = "history-1",
            kind = WorkflowTaskKind.REGENERATE_ANSWER,
            answerVersionIndex = 2
        )
        registry.mark("task-1", WorkflowTaskStatus.ERROR, "failed")

        val task = registry.tasks.value.getValue("task-1")
        assertEquals("history-1", task.historyId)
        assertEquals(WorkflowTaskKind.REGENERATE_ANSWER, task.kind)
        assertEquals(2, task.answerVersionIndex)
        assertEquals(WorkflowTaskStatus.ERROR, task.status)
        assertEquals("failed", task.errorMessage)
    }

    @Test
    fun cancelRunningHistoryTasks_onlyCancelsMatchingRunningTasks() = runTest {
        val registry = WorkflowTaskRegistry()
        val matchingJob = Job()
        val otherJob = Job()
        registry.register("task-1", "history-1", WorkflowTaskKind.ANSWER)
        registry.register("task-2", "history-2", WorkflowTaskKind.ANSWER)
        registry.trackJob("task-1", matchingJob)
        registry.trackJob("task-2", otherJob)

        registry.cancelRunningHistoryTasks("history-1")

        assertTrue(matchingJob.isCancelled)
        assertFalse(otherJob.isCancelled)
        assertEquals(WorkflowTaskStatus.CANCELLED, registry.tasks.value.getValue("task-1").status)
        assertEquals(WorkflowTaskStatus.RUNNING, registry.tasks.value.getValue("task-2").status)
    }

    @Test
    fun cancelAll_cancelsTrackedJobsAndMarksRunningTasksCancelled() = runTest {
        val registry = WorkflowTaskRegistry()
        val firstJob = Job()
        val secondJob = Job()
        registry.register("task-1", "history-1", WorkflowTaskKind.ANSWER)
        registry.register("task-2", "history-2", WorkflowTaskKind.AUTOMATION)
        registry.trackJob("task-1", firstJob)
        registry.trackJob("task-2", secondJob)

        registry.cancelAll()

        assertTrue(firstJob.isCancelled)
        assertTrue(secondJob.isCancelled)
        assertEquals(
            setOf(WorkflowTaskStatus.CANCELLED),
            registry.tasks.value.values.map { it.status }.toSet()
        )
        assertFalse(registry.isRunning("history-1"))
        assertFalse(registry.isRunning("history-2"))
    }
}
