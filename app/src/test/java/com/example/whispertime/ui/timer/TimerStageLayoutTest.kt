package com.example.whispertime.ui.timer

import com.example.whispertime.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerStageLayoutTest {

    @Test
    fun runningTimer_usesCenteredLayoutWithoutChrome() {
        val layout = timerStageLayout(TimerState.RUNNING)

        assertEquals(TimerCircleAlignment.CENTER, layout.circleAlignment)
        assertEquals(0, layout.circleTopPaddingDp)
        assertEquals(0, layout.circleOffsetYDp)
        assertEquals(false, layout.showAmbientChrome)
    }

    @Test
    fun pausedTimer_keepsCenteredLayoutWithoutChrome() {
        val layout = timerStageLayout(TimerState.PAUSED)

        assertEquals(TimerCircleAlignment.CENTER, layout.circleAlignment)
        assertEquals(0, layout.circleTopPaddingDp)
        assertEquals(0, layout.circleOffsetYDp)
        assertEquals(false, layout.showAmbientChrome)
    }

    @Test
    fun idleTimer_keepsTopAnchoredLayoutWithChrome() {
        val layout = timerStageLayout(TimerState.IDLE)

        assertEquals(TimerCircleAlignment.TOP_CENTER, layout.circleAlignment)
        assertEquals(36, layout.circleTopPaddingDp)
        assertEquals(0, layout.circleOffsetYDp)
        assertEquals(true, layout.showAmbientChrome)
    }
}
