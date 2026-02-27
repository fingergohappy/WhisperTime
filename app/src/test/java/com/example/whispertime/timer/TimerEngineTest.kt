package com.example.whispertime.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
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
}
