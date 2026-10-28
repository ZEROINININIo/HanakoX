package `fun`.kirari.hanako.solve.runtime

import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.data.historyMetadataFor
import `fun`.kirari.hanako.core.data.normalizedHistoryMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class WorkflowResultStore(
    private val repository: WorkflowHistoryRepository,
    private val scope: CoroutineScope,
    private val persistDelayMillis: Long = 250L
) {
    private val persistJobs = mutableMapOf<String, Job>()
    private val persistVersions = mutableMapOf<String, Long>()
    private val removedHistoryIds = mutableSetOf<String>()
    private val _liveResults = MutableStateFlow<Map<String, ProcessingResult>>(emptyMap())
    val liveResults: StateFlow<Map<String, ProcessingResult>> = _liveResults.asStateFlow()

    suspend fun upsert(result: ProcessingResult) {
        if (result.id in removedHistoryIds) return
        publish(result)
        persistNow(result)
    }

    suspend fun update(
        historyId: String,
        transform: (ProcessingResult) -> ProcessingResult
    ): ProcessingResult? {
        if (historyId in removedHistoryIds) return null
        val liveResult = _liveResults.value[historyId]
        if (liveResult != null) {
            val updated = transform(liveResult)
            publish(updated)
            persistLater(updated)
            return updated
        }

        val existing = latest(historyId) ?: return null
        val updated = transform(existing)
        publish(updated)
        persistLater(updated)
        return updated
    }

    suspend fun updateNow(
        historyId: String,
        transform: (ProcessingResult) -> ProcessingResult
    ): ProcessingResult? {
        if (historyId in removedHistoryIds) return null
        val existing = latest(historyId) ?: return null
        val updated = transform(existing)
        publish(updated)
        persistNow(updated)
        return updated
    }

    suspend fun latest(historyId: String): ProcessingResult? {
        if (historyId in removedHistoryIds) return null
        _liveResults.value[historyId]?.let { return it }
        val settings = repository.read()
        return settings.history.firstOrNull { it.id == historyId }
    }

    fun remove(historyId: String) {
        removedHistoryIds += historyId
        persistJobs.remove(historyId)?.cancel()
        persistVersions.remove(historyId)
        _liveResults.update { it - historyId }
    }

    fun clear() {
        removedHistoryIds += _liveResults.value.keys
        persistJobs.values.forEach { it.cancel() }
        persistJobs.clear()
        persistVersions.clear()
        _liveResults.value = emptyMap()
    }

    fun mergedWith(persisted: List<ProcessingResult>): List<ProcessingResult> {
        val live = _liveResults.value
        val persistedIds = persisted.mapTo(mutableSetOf()) { it.id }
        return live.values.filterNot { it.id in persistedIds } +
            persisted.map { live[it.id] ?: it }
    }

    fun restore(result: ProcessingResult) {
        publish(result)
    }

    private fun publish(result: ProcessingResult) {
        if (result.id in removedHistoryIds) return
        _liveResults.update { current -> current + (result.id to result) }
    }

    private suspend fun persistNow(result: ProcessingResult) {
        if (result.id in removedHistoryIds) return
        persistJobs.remove(result.id)?.cancel()
        persistVersions[result.id] = (persistVersions[result.id] ?: 0L) + 1L
        repository.update { current ->
            val history = listOf(result) + current.history.filterNot { it.id == result.id }
            val previous = current.historyMetadataFor(result)
            val changed = current.history.firstOrNull { it.id == result.id }?.let { it != result } == true
            val metadata = current.normalizedHistoryMetadata().map { entry ->
                if (entry.historyId == result.id) entry.copy(
                    lastActivityAtMillis = maxOf(entry.lastActivityAtMillis, result.createdAtMillis),
                    contentRevision = if (changed) entry.contentRevision + 1 else entry.contentRevision
                ) else entry
            }
            current.copy(lastResult = result, history = history, historyMetadata = metadata)
        }
    }

    private fun persistLater(result: ProcessingResult) {
        val historyId = result.id
        if (historyId in removedHistoryIds) return
        val version = (persistVersions[historyId] ?: 0L) + 1L
        persistVersions[historyId] = version
        persistJobs.remove(historyId)?.cancel()
        persistJobs[historyId] = scope.launch {
            delay(persistDelayMillis)
            if (persistVersions[historyId] != version) return@launch
            persistNow(_liveResults.value[historyId] ?: result)
        }
    }
}
