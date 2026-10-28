package `fun`.kirari.hanako.feature.history.presentation

import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.core.data.ModelSelection
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.solve.application.ActiveSolveTask
import `fun`.kirari.hanako.solve.application.SolveHistoryState
import `fun`.kirari.hanako.solve.model.WorkflowTaskKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryWorkflowControllerTest {

    @Test
    fun detailState_projectsResultOperationAndConversationModelAtomically() {
        val result = ProcessingResult(
            id = "history-1",
            assistantName = "assistant",
            route = ProcessingRoute.OCR_THEN_LLM
        )
        val override = ModelSelection("provider", "conversation-model")
        val state = SolveHistoryState(
            results = listOf(result),
            activeTasks = mapOf(
                result.id to ActiveSolveTask(
                    taskId = "conversation-task",
                    historyId = result.id,
                    kind = WorkflowTaskKind.CONVERSATION,
                    answerVersionIndex = null,
                    conversationTurnId = "turn-1"
                )
            )
        )

        val detail = buildHistoryDetailStates(
            state = state,
            modelOverrides = mapOf(result.id to override),
            settings = AppSettings()
        ).getValue(result.id)

        assertEquals(result, detail.result)
        assertEquals(ModelPurpose.TEXT, detail.conversationModelPurpose)
        assertEquals(override, detail.conversationModelSelection)
        assertEquals("conversation-model", detail.conversationModelLabel)
        assertTrue(detail.operation is HistoryDetailOperation.SendingFollowUp)
        assertEquals(
            "turn-1",
            (detail.operation as HistoryDetailOperation.SendingFollowUp).activeTurnId
        )
    }
}
