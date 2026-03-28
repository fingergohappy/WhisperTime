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

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {

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

    @Test
    fun countdown_signalsCompletionAtZero() = runTest(UnconfinedTestDispatcher()) {
        var fakeTime = 0L
        val engine = TimerEngine(
            timeSource = TimeSource { fakeTime },
            coroutineScope = this
        )
        val announcements = mutableListOf<Long>()
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

    @Test
    fun voiceAnnouncement_triggersAtInterval() = runTest(UnconfinedTestDispatcher()) {
        var fakeTime = 0L
        val engine = TimerEngine(
            timeSource = TimeSource { fakeTime },
            coroutineScope = this
        )
        val announcements = mutableListOf<Long>()
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

    @Test
    fun restore_runningCountdown_recomputesRemainingTime() = runTest(UnconfinedTestDispatcher()) {
        var fakeTime = 5_500L
        val engine = TimerEngine(
            timeSource = TimeSource { fakeTime },
            coroutineScope = this
        )
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
