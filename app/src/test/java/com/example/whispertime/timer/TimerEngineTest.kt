package com.example.whispertime.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun countdown_autoStopsAtZero() = runTest(UnconfinedTestDispatcher()) {
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
                durationMs = 500L
            )
        )

        fakeTime = 600L
        advanceTimeBy(300L)

        assertEquals(TimerState.IDLE, engine.state.value)
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

        assertEquals(TimerState.PAUSED, engine.state.value)
        assertEquals(beforePause, engine.elapsedMs.value)

        engine.resume()
        fakeTime = 4700L
        advanceTimeBy(200L)
        assertTrue(engine.elapsedMs.value > beforePause)

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
        assertEquals(42L, result?.projectId)
        assertTrue((result?.durationMs ?: 0L) >= 2000L)
        assertEquals(TimerState.IDLE, engine.state.value)
    }

    @Test
    fun prepare_transitionsToRunningAndClearsPrepareRemaining() = runTest(UnconfinedTestDispatcher()) {
        var fakeTime = 0L
        val engine = TimerEngine(
            timeSource = TimeSource { fakeTime },
            coroutineScope = this
        )

        engine.start(
            TimerConfig(
                projectId = 1L,
                projectName = "Prepare",
                mode = TimerMode.COUNT_UP,
                prepareTimeMs = 3000L
            )
        )

        assertEquals(TimerState.PREPARING, engine.state.value)
        assertEquals(3000L, engine.prepareRemainingMs.value)

        fakeTime = 1000L
        advanceTimeBy(200L)
        assertTrue((engine.prepareRemainingMs.value ?: 0L) in 1L..2999L)

        fakeTime = 3200L
        advanceTimeBy(300L)

        assertEquals(TimerState.RUNNING, engine.state.value)
        assertNull(engine.prepareRemainingMs.value)

        fakeTime = 3700L
        advanceTimeBy(200L)
        assertTrue(engine.elapsedMs.value > 0L)

        engine.cancel()
    }

    @Test
    fun voiceAnnouncement_triggersAtInterval() = runTest(UnconfinedTestDispatcher()) {
        var fakeTime = 0L
        val engine = TimerEngine(
            timeSource = TimeSource { fakeTime },
            coroutineScope = this
        )
        val signals = mutableListOf<Long>()
        val collector = launch {
            engine.shouldAnnounce.collect { signals += it }
        }

        engine.start(
            TimerConfig(
                projectId = 1L,
                projectName = "Voice Interval",
                mode = TimerMode.COUNT_UP,
                voiceIntervalMs = 1000L
            )
        )

        fakeTime = 900L
        advanceTimeBy(200L)
        assertTrue(signals.isEmpty())

        fakeTime = 1000L
        advanceTimeBy(200L)
        assertEquals(listOf(1000L), signals)

        advanceTimeBy(200L)
        assertEquals(listOf(1000L), signals)

        fakeTime = 2300L
        advanceTimeBy(200L)
        assertEquals(listOf(1000L, 2000L), signals)

        fakeTime = 4100L
        advanceTimeBy(200L)
        assertEquals(listOf(1000L, 2000L, 3000L, 4000L), signals)

        collector.cancel()
        engine.cancel()
    }
}
