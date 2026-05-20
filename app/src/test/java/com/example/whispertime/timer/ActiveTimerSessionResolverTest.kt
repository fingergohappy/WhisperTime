package com.example.whispertime.timer

import com.example.whispertime.service.ActiveTimerSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 活跃计时会话恢复器测试，覆盖准备、运行、暂停和倒计时完成边界。 */
class ActiveTimerSessionResolverTest {

    /** 验证准备阶段恢复时会扣除离开期间流逝的准备时间。 */
    @Test
    fun preparing_restoresRemainingPrepareTime() {
        val session = ActiveTimerSession(
            projectId = 1L,
            projectName = "Warmup",
            mode = TimerMode.COUNT_UP,
            durationMs = null,
            voiceIntervalMs = 5_000L,
            vibrationEnabled = false,
            state = TimerState.PREPARING,
            prepareRemainingMs = 4_000L,
            prepareReferenceEpochMs = 100_000L,
            prepareReferenceElapsedRealtimeMs = 2_000L,
            sessionStartEpochMs = null,
            elapsedMs = 0L,
            runningReferenceElapsedRealtimeMs = null,
            lastAnnouncedElapsedMs = 0L
        )

        val resolved = ActiveTimerSessionResolver.resolve(session, nowElapsedRealtimeMs = 3_500L)

        assertEquals(TimerState.PREPARING, resolved.state)
        assertEquals(2_500L, resolved.prepareRemainingMs)
        assertEquals(0L, resolved.elapsedMs)
        assertFalse(resolved.shouldComplete)
    }

    /** 验证准备阶段在后台结束时会恢复为运行状态。 */
    @Test
    fun preparing_thatExpiresWhileAway_restoresRunningState() {
        val session = ActiveTimerSession(
            projectId = 1L,
            projectName = "Warmup",
            mode = TimerMode.COUNTDOWN,
            durationMs = 10_000L,
            voiceIntervalMs = 5_000L,
            vibrationEnabled = false,
            state = TimerState.PREPARING,
            prepareRemainingMs = 3_000L,
            prepareReferenceEpochMs = 20_000L,
            prepareReferenceElapsedRealtimeMs = 1_000L,
            sessionStartEpochMs = null,
            elapsedMs = 0L,
            runningReferenceElapsedRealtimeMs = null,
            lastAnnouncedElapsedMs = 0L
        )

        val resolved = ActiveTimerSessionResolver.resolve(session, nowElapsedRealtimeMs = 5_500L)

        assertEquals(TimerState.RUNNING, resolved.state)
        assertEquals(1_500L, resolved.elapsedMs)
        assertEquals(8_500L, resolved.remainingMs)
        assertEquals(23_000L, resolved.sessionStartEpochMs)
        assertFalse(resolved.shouldComplete)
    }

    /** 验证运行阶段恢复时会从参考时钟补齐已计时时长。 */
    @Test
    fun running_restoresElapsedFromReferencePoint() {
        val session = ActiveTimerSession(
            projectId = 1L,
            projectName = "Run",
            mode = TimerMode.COUNTDOWN,
            durationMs = 12_000L,
            voiceIntervalMs = 3_000L,
            vibrationEnabled = false,
            state = TimerState.RUNNING,
            prepareRemainingMs = null,
            prepareReferenceEpochMs = null,
            prepareReferenceElapsedRealtimeMs = null,
            sessionStartEpochMs = 77_000L,
            elapsedMs = 4_000L,
            runningReferenceElapsedRealtimeMs = 2_000L,
            lastAnnouncedElapsedMs = 3_000L
        )

        val resolved = ActiveTimerSessionResolver.resolve(session, nowElapsedRealtimeMs = 5_500L)

        assertEquals(TimerState.RUNNING, resolved.state)
        assertEquals(7_500L, resolved.elapsedMs)
        assertEquals(4_500L, resolved.remainingMs)
        assertEquals(3_000L, resolved.lastAnnouncedElapsedMs)
    }

    /** 验证暂停阶段恢复时不会继续累加离开期间的时间。 */
    @Test
    fun paused_keepsFrozenElapsedTime() {
        val session = ActiveTimerSession(
            projectId = 1L,
            projectName = "Pause",
            mode = TimerMode.COUNT_UP,
            durationMs = null,
            voiceIntervalMs = 3_000L,
            vibrationEnabled = false,
            state = TimerState.PAUSED,
            prepareRemainingMs = null,
            prepareReferenceEpochMs = null,
            prepareReferenceElapsedRealtimeMs = null,
            sessionStartEpochMs = 90_000L,
            elapsedMs = 8_000L,
            runningReferenceElapsedRealtimeMs = null,
            lastAnnouncedElapsedMs = 6_000L
        )

        val resolved = ActiveTimerSessionResolver.resolve(session, nowElapsedRealtimeMs = 50_000L)

        assertEquals(TimerState.PAUSED, resolved.state)
        assertEquals(8_000L, resolved.elapsedMs)
        assertEquals(6_000L, resolved.lastAnnouncedElapsedMs)
    }

    /** 验证倒计时在后台运行到期时会请求完成处理。 */
    @Test
    fun countdown_thatExpiredWhileRunning_requestsCompletion() {
        val session = ActiveTimerSession(
            projectId = 1L,
            projectName = "Done",
            mode = TimerMode.COUNTDOWN,
            durationMs = 5_000L,
            voiceIntervalMs = 1_000L,
            vibrationEnabled = false,
            state = TimerState.RUNNING,
            prepareRemainingMs = null,
            prepareReferenceEpochMs = null,
            prepareReferenceElapsedRealtimeMs = null,
            sessionStartEpochMs = 90_000L,
            elapsedMs = 4_500L,
            runningReferenceElapsedRealtimeMs = 10_000L,
            lastAnnouncedElapsedMs = 4_000L
        )

        val resolved = ActiveTimerSessionResolver.resolve(session, nowElapsedRealtimeMs = 11_000L)

        assertEquals(TimerState.PAUSED, resolved.state)
        assertEquals(5_000L, resolved.elapsedMs)
        assertEquals(0L, resolved.remainingMs)
        assertTrue(resolved.shouldComplete)
    }
}
