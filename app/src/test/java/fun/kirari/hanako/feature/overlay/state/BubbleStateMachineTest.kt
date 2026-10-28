package `fun`.kirari.hanako.feature.overlay.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleStateMachineTest {

    @Test
    fun idle_startProcessing_transitionsToProcessing() {
        val machine = BubbleStateMachine()

        machine.dispatch(BubbleEvent.StartProcessing)

        assertEquals(BubbleState.Processing, machine.currentState)
    }

    @Test
    fun processing_copyComplete_transitionsToCopied() {
        val machine = BubbleStateMachine()
        machine.forceState(BubbleState.Processing)

        machine.dispatch(BubbleEvent.CopyComplete("已复制"))

        assertEquals(BubbleState.Copied("已复制"), machine.currentState)
    }

    @Test
    fun processing_lettersComplete_transitionsToShowingLetters() {
        val machine = BubbleStateMachine()
        machine.forceState(BubbleState.Processing)

        machine.dispatch(BubbleEvent.LettersComplete("AC"))

        assertEquals(BubbleState.ShowingLetters("AC"), machine.currentState)
    }

    @Test
    fun processing_timeoutAndCancel_returnToIdle() {
        val machine = BubbleStateMachine()
        machine.forceState(BubbleState.Processing)

        machine.dispatch(BubbleEvent.Timeout)
        assertEquals(BubbleState.Idle, machine.currentState)

        machine.forceState(BubbleState.Processing)
        machine.dispatch(BubbleEvent.CancelProcessing)
        assertEquals(BubbleState.Idle, machine.currentState)
    }

    @Test
    fun longPress_wrapsCurrentStateInMenuExpanded() {
        val machine = BubbleStateMachine()
        val source = BubbleState.ShowingLetters("B")
        machine.forceState(source)

        machine.dispatch(BubbleEvent.LongPress(anchorX = 12, anchorY = 34))

        assertEquals(
            BubbleState.MenuExpanded(source, anchorX = 12, anchorY = 34),
            machine.currentState
        )
    }

    @Test
    fun menuExpanded_closeMenuAndTaps_restorePreviousState() {
        val previous = BubbleState.Copied("done")
        val machine = BubbleStateMachine()

        machine.forceState(BubbleState.MenuExpanded(previous, 1, 2))
        machine.dispatch(BubbleEvent.CloseMenu)
        assertEquals(previous, machine.currentState)

        machine.forceState(BubbleState.MenuExpanded(previous, 1, 2))
        machine.dispatch(BubbleEvent.SingleTap)
        assertEquals(previous, machine.currentState)

        machine.forceState(BubbleState.MenuExpanded(previous, 1, 2))
        machine.dispatch(BubbleEvent.DoubleTap)
        assertEquals(previous, machine.currentState)
    }

    @Test
    fun copiedAndError_resetPaths_returnToIdle() {
        val machine = BubbleStateMachine()

        machine.forceState(BubbleState.Copied("x"))
        machine.dispatch(BubbleEvent.SingleTap)
        assertEquals(BubbleState.Idle, machine.currentState)

        machine.forceState(BubbleState.Error("boom"))
        machine.dispatch(BubbleEvent.Reset)
        assertEquals(BubbleState.Idle, machine.currentState)

        machine.forceState(BubbleState.Error("boom"))
        machine.dispatch(BubbleEvent.SingleTap)
        assertEquals(BubbleState.Idle, machine.currentState)
    }

    @Test
    fun multiPageCapture_flow_withoutBitmaps_isTracked() {
        val machine = BubbleStateMachine()

        machine.dispatch(BubbleEvent.EnterMultiPageCapture)
        assertEquals(BubbleState.MultiPageCapture(), machine.currentState)
        assertTrue(machine.canCapture())
        assertFalse(machine.canSendCaptures())
        assertTrue(machine.getCapturedBitmaps().isEmpty())

        machine.dispatch(BubbleEvent.CaptureStart)
        assertEquals(BubbleState.MultiPageCapturing(), machine.currentState)
        assertFalse(machine.canCapture())

        machine.dispatch(BubbleEvent.CaptureFailed)
        assertEquals(BubbleState.MultiPageCapture(), machine.currentState)

        machine.dispatch(BubbleEvent.SendCaptures)
        assertEquals(BubbleState.Processing, machine.currentState)
    }

    @Test
    fun multiPageSuccess_animationDone_returnsToCaptureState() {
        val machine = BubbleStateMachine()
        machine.forceState(BubbleState.MultiPageCaptureSuccess(captureCount = 2))

        machine.dispatch(BubbleEvent.CaptureSuccessAnimationDone)

        assertEquals(BubbleState.MultiPageCapture(captureCount = 2), machine.currentState)
    }

    @Test
    fun invalidTransition_keepsCurrentState() {
        val machine = BubbleStateMachine()
        machine.forceState(BubbleState.Idle)

        machine.dispatch(BubbleEvent.DoubleTap)

        assertEquals(BubbleState.Idle, machine.currentState)
    }
}
