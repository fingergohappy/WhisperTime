package com.example.whispertime.ui.timer

import com.example.whispertime.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 计时圆盘 UI 状态映射测试。 */
class TimerCircleUiStateTest {

    /** 运行态隐藏停止按钮并展示暂停提示。 */
    @Test
    fun runningTimer_hidesStopButtonAndShowsPauseHint() {
        val uiState = timerCircleUiState(TimerState.RUNNING)

        assertFalse(uiState.showStopButton)
        assertEquals("Tap to pause", uiState.primaryHintText)
    }

    /** 暂停态显示停止按钮并展示继续提示。 */
    @Test
    fun pausedTimer_showsStopButtonWithResumeHint() {
        val uiState = timerCircleUiState(TimerState.PAUSED)

        assertTrue(uiState.showStopButton)
        assertEquals("Tap circle to resume", uiState.secondaryHintText)
    }

    /** 空闲态隐藏停止按钮并展示开始提示。 */
    @Test
    fun idleTimer_hidesStopButtonAndShowsStartHint() {
        val uiState = timerCircleUiState(TimerState.IDLE)

        assertFalse(uiState.showStopButton)
        assertEquals("Tap to start", uiState.primaryHintText)
    }
}
