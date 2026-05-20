package com.example.whispertime.ui.timer

import com.example.whispertime.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test

/** 计时舞台布局状态映射测试。 */
class TimerStageLayoutTest {

    /** 运行态圆盘居中并隐藏环境装饰。 */
    @Test
    fun runningTimer_usesCenteredLayoutWithoutChrome() {
        val layout = timerStageLayout(TimerState.RUNNING)

        assertEquals(TimerCircleAlignment.CENTER, layout.circleAlignment)
        assertEquals(0, layout.circleTopPaddingDp)
        assertEquals(0, layout.circleOffsetYDp)
        assertEquals(false, layout.showAmbientChrome)
    }

    /** 暂停态保持居中布局并隐藏环境装饰。 */
    @Test
    fun pausedTimer_keepsCenteredLayoutWithoutChrome() {
        val layout = timerStageLayout(TimerState.PAUSED)

        assertEquals(TimerCircleAlignment.CENTER, layout.circleAlignment)
        assertEquals(0, layout.circleTopPaddingDp)
        assertEquals(0, layout.circleOffsetYDp)
        assertEquals(false, layout.showAmbientChrome)
    }

    /** 空闲态圆盘靠上并展示环境装饰。 */
    @Test
    fun idleTimer_keepsTopAnchoredLayoutWithChrome() {
        val layout = timerStageLayout(TimerState.IDLE)

        assertEquals(TimerCircleAlignment.TOP_CENTER, layout.circleAlignment)
        assertEquals(36, layout.circleTopPaddingDp)
        assertEquals(0, layout.circleOffsetYDp)
        assertEquals(true, layout.showAmbientChrome)
    }
}
