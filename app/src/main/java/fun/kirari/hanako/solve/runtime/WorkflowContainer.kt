package `fun`.kirari.hanako.solve.runtime

import android.content.Context
import `fun`.kirari.hanako.core.data.SettingsRepository
import `fun`.kirari.hanako.platform.capture.ocr.LocalOcrManager
import `fun`.kirari.hanako.core.network.UnifiedLLMClient
import `fun`.kirari.hanako.core.network.search.SearchOrchestrator
import `fun`.kirari.hanako.solve.workflow.ProcessingPipeline
import `fun`.kirari.hanako.solve.workflow.ConversationWorkflow
import `fun`.kirari.hanako.solve.workflow.HanakoWorkflowFactory
import `fun`.kirari.hanako.solve.application.SolveOperations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class WorkflowContainer(
    appContext: Context,
    settingsRepository: SettingsRepository,
    unifiedLLMClient: UnifiedLLMClient,
    localOcrManager: LocalOcrManager,
    searchOrchestrator: SearchOrchestrator?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val historyRepository = SettingsWorkflowHistoryRepository(settingsRepository)
    private val pipeline = ProcessingPipeline()
    private val workflowFactory = HanakoWorkflowFactory(
        appContext = appContext,
        unifiedClient = unifiedLLMClient,
        localOcrManager = localOcrManager,
        searchOrchestrator = searchOrchestrator,
        pipeline = pipeline
    )
    private val conversationWorkflow = ConversationWorkflow(
        unifiedClient = unifiedLLMClient,
        pipeline = pipeline
    )
    private val resultStore = WorkflowResultStore(
        repository = historyRepository,
        scope = scope
    )
    private val titleSummaryService = TitleSummaryService(
        repository = historyRepository,
        client = unifiedLLMClient
    )
    private val taskRegistry = WorkflowTaskRegistry()
    private val taskManager = WorkflowTaskManager(
        repository = historyRepository,
        resultStore = resultStore,
        taskRegistry = taskRegistry,
        workflowFactory = workflowFactory,
        conversationWorkflow = conversationWorkflow,
        titleSummaryService = titleSummaryService,
        scope = scope
    )
    private val interruptedTaskReconciliation = scope.launch {
        taskManager.reconcileInterruptedTasks()
    }
    val operations = SolveOperations(
        pipeline = pipeline,
        taskManager = taskManager
    )
}
