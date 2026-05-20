package com.example.whispertime.timer

import com.example.whispertime.service.ActiveTimerSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 计时引擎单元测试，覆盖正计时、倒计时、暂停、停止、播报和恢复。 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {

    /** 验证正计时启动后已计时时长会随单调时钟增长。 */
    @Test
    fun countUp_elapsedIncreases() = runTest(UnconfinedTestDispatcher()) {
        var fakeTime = 0L
        val engine = TimerEngine(
            timeSource = TimeSource { fakeTime },
            coroutineScope = this
        )

        engine.start(
            TimerConfig(
                projectId = 1L,
                projectName = "Test",
                mode = TimerMode.COUNT_UP,
                voiceIntervalMs = null
            )
        )

        fakeTime = 1000L
        advanceTimeBy(200L)

        assertTrue(engine.elapsedMs.value > 0L)
        engine.cancel()
    }

    /** 验证倒计时启动后剩余时长会减少。 */
    @Test
    fun countdown_remainingDecreases() = runTest(UnconfinedTestDispatcher()) {
        var fakeTime = 0L
        val engine = TimerEngine(
            timeSource = TimeSource { fakeTime },
            coroutineScope = this
        )

        engine.start(
            TimerConfig(
                projectId = 1L,
                projectName = "Countdown",
                mode = TimerMode.COUNTDOWN,
                durationMs = 5000L
            )
        )

        fakeTime = 1500L
        advanceTimeBy(200L)

        val remaining = engine.remainingMs.value
        assertNotNull(remaining)
        assertTrue((remaining ?: 5000L) < 5000L)
        engine.cancel()
    }

    /** 验证倒计时到零时会暂停并发送完成信号。 */
    @Test
    fun countdown_signalsCompletionAtZero() = runTest(UnconfinedTestDispatcher()) {
        var fakeTime = 0L
        val engine = TimerEngine(
            timeSource = TimeSource { fakeTime },
            coroutineScope = this
        )
        val announcements = mutableListOf<Long>()
        // 收集引擎播报信号，用于断言完成信号是否发出。
        val collectorJob = launch {
            engine.shouldAnnounce.collect { announcements.add(it) }
        }

        engine.start(
            TimerConfig(
                projectId = 1L,
                projectName = "Countdown",
                mode = TimerMode.COUNTDOWN,
                durationMs = 500L
            )
        )

        fakeTime = 600L
        advanceTimeBy(300L)

        assertEquals(TimerState.PAUSED, engine.state.value)
        assertTrue(announcements.contains(-1L))
        collectorJob.cancel()
    }

    /** 验证暂停后已计时时长保持冻结。 */
    @Test
    fun pause_freezesElapsed() = runTest(UnconfinedTestDispatcher()) {
        var fakeTime = 0L
        val engine = TimerEngine(
            timeSource = TimeSource { fakeTime },
            coroutineScope = this
        )

        engine.start(
            TimerConfig(
                projectId = 1L,
                projectName = "Pause",
                mode = TimerMode.COUNT_UP
            )
        )

        fakeTime = 1200L
        advanceTimeBy(200L)
        val beforePause = engine.elapsedMs.value

        engine.pause()

        fakeTime = 4000L
        advanceTimeBy(1000L)

        assertEquals(beforePause, engine.elapsedMs.value)
        engine.cancel()
    }

    /** 验证停止计时会返回可保存的计时结果并重置状态。 */
    @Test
    fun stop_returnsCorrectResult() = runTest(UnconfinedTestDispatcher()) {
        var fakeTime = 0L
        val engine = TimerEngine(
            timeSource = TimeSource { fakeTime },
            coroutineScope = this
        )

        engine.start(
            TimerConfig(
                projectId = 42L,
                projectName = "Stop",
                mode = TimerMode.COUNT_UP
            )
        )

        fakeTime = 2100L
        advanceTimeBy(200L)
        val result = engine.stop()

        assertNotNull(result)
        assertTrue((result?.durationMs ?: 0L) >= 2000L)
        assertEquals(42L, result?.projectId)
        assertEquals(TimerState.IDLE, engine.state.value)
    }

    /** 验证语音播报间隔会按配置周期触发。 */
    @Test
    fun voiceAnnouncement_triggersAtInterval() = runTest(UnconfinedTestDispatcher()) {
        var fakeTime = 0L
        val engine = TimerEngine(
            timeSource = TimeSource { fakeTime },
            coroutineScope = this
        )
        val announcements = mutableListOf<Long>()
        // 收集周期播报信号，验证至少跨过两个间隔点。
        val collectorJob = launch {
            engine.shouldAnnounce.collect { announcements.add(it) }
        }

        engine.start(
            TimerConfig(
                projectId = 1L,
                projectName = "Voice",
                mode = TimerMode.COUNT_UP,
                voiceIntervalMs = 1000L
            )
        )

        fakeTime = 1000L
        advanceTimeBy(200L)
        fakeTime = 2000L
        advanceTimeBy(200L)
        fakeTime = 2500L
        advanceTimeBy(200L)

        assertTrue(announcements.size >= 2)
        collectorJob.cancel()
        engine.cancel()
    }

    /** 验证恢复运行中的倒计时会根据参考时钟重新计算剩余时间。 */
    @Test
    fun restore_runningCountdown_recomputesRemainingTime() = runTest(UnconfinedTestDispatcher()) {
        var fakeTime = 5_500L
        val engine = TimerEngine(
            timeSource = TimeSource { fakeTime },
            coroutineScope = this
        )
        // 构造进程恢复后的解析结果，模拟后台已经流逝的时间。
        val resolved = ActiveTimerSessionResolver.resolve(
            session = ActiveTimerSession(
                projectId = 1L,
                projectName = "Restore",
                mode = TimerMode.COUNTDOWN,
                durationMs = 12_000L,
                voiceIntervalMs = 3_000L,
                vibrationEnabled = false,
                state = TimerState.RUNNING,
                prepareRemainingMs = null,
                prepareReferenceEpochMs = null,
                prepareReferenceElapsedRealtimeMs = null,
                sessionStartEpochMs = 100_000L,
                elapsedMs = 4_000L,
                runningReferenceElapsedRealtimeMs = 2_000L,
                lastAnnouncedElapsedMs = 3_000L
            ),
            nowElapsedRealtimeMs = fakeTime
        )

        engine.restore(resolved)

        assertEquals(TimerState.RUNNING, engine.state.value)
        assertEquals(7_500L, engine.elapsedMs.value)
        assertEquals(4_500L, engine.remainingMs.value)

        fakeTime = 6_500L
        advanceTimeBy(200L)

        assertTrue((engine.remainingMs.value ?: 0L) < 4_500L)
        engine.cancel()
    }
}
