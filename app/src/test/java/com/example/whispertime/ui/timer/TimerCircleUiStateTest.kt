package com.example.whispertime.ui.timer

import com.example.whispertime.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerCircleUiStateTest {

    @Test
    fun runningTimer_hidesStopButtonAndShowsPauseHint() {
        val uiState = timerCircleUiState(TimerState.RUNNING)

        assertFalse(uiState.showStopButton)
        assertEquals("Tap to pause", uiState.primaryHintText)
    }

    @Test
    fun pausedTimer_showsStopButtonWithResumeHint() {
        val uiState = timerCircleUiState(TimerState.PAUSED)

        assertTrue(uiState.showStopButton)
        assertEquals("Tap circle to resume", uiState.secondaryHintText)
    }

    @Test
    fun idleTimer_hidesStopButtonAndShowsStartHint() {
        val uiState = timerCircleUiState(TimerState.IDLE)

        assertFalse(uiState.showStopButton)
        assertEquals("Tap to start", uiState.primaryHintText)
    }
}
