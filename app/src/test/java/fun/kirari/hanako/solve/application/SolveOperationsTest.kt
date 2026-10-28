package `fun`.kirari.hanako.solve.application

import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.ModelSelection
import `fun`.kirari.hanako.core.model.ProcessingRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class SolveOperationsTest {
    private val textSelection = ModelSelection("text-provider", "text-model")
    private val visionSelection = ModelSelection("vision-provider", "vision-model")
    private val overrideSelection = ModelSelection("conversation-provider", "conversation-model")

    @Test
    fun ocrConversation_overridesOnlyTextModel() {
        val settings = AppSettings(
            textModelSelection = textSelection,
            visionModelSelection = visionSelection
        )

        val updated = settings.withConversationModel(
            ProcessingRoute.OCR_THEN_LLM,
            overrideSelection
        )

        assertEquals(overrideSelection, updated.textModelSelection)
        assertEquals(visionSelection, updated.visionModelSelection)
    }

    @Test
    fun multimodalConversation_overridesOnlyVisionModel() {
        val settings = AppSettings(
            textModelSelection = textSelection,
            visionModelSelection = visionSelection
        )

        val updated = settings.withConversationModel(
            ProcessingRoute.MULTIMODAL_DIRECT,
            overrideSelection
        )

        assertEquals(textSelection, updated.textModelSelection)
        assertEquals(overrideSelection, updated.visionModelSelection)
    }
}
