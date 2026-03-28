package com.example.whispertime.timer

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun interface TimeSource {
    fun elapsedRealtime(): Long
}

class TimerEngine(
    private val timeSource: TimeSource = TimeSource { SystemClock.elapsedRealtime() },
    private val wallClockTimeSource: () -> Long = { System.currentTimeMillis() },
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val _state = MutableStateFlow(TimerState.IDLE)
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    private val _prepareRemainingMs = MutableStateFlow<Long?>(null)
    val prepareRemainingMs: StateFlow<Long?> = _prepareRemainingMs.asStateFlow()

    private val _shouldAnnounce = MutableSharedFlow<Long>(extraBufferCapacity = 10)
    val shouldAnnounce: SharedFlow<Long> = _shouldAnnounce.asSharedFlow()

    private var config: TimerConfig? = null
    private var startEpoch: Long = 0L
    private var startElapsed: Long = 0L
    private var pausedElapsedMs: Long = 0L
    private var lastAnnouncedMs: Long = 0L
    private var completionPending: Boolean = false
    private var timerJob: Job? = null

    fun start(config: TimerConfig) {
        if (_state.value != TimerState.IDLE) return

        this.config = config
        pausedElapsedMs = 0L
        lastAnnouncedMs = 0L
        completionPending = false
        _elapsedMs.value = 0L
        _remainingMs.value = config.durationMs

        if (config.prepareTimeMs != null && config.prepareTimeMs > 0L) {
            _state.value = TimerState.PREPARING
            _prepareRemainingMs.value = config.prepareTimeMs
            startPrepareLoop(config.prepareTimeMs)
        } else {
            startEpoch = wallClockTimeSource()
            startElapsed = timeSource.elapsedRealtime()
            _state.value = TimerState.RUNNING
            startTickLoop()
        }
    }

    fun pause() {
        if (completionPending) return
        if (_state.value != TimerState.RUNNING) return

        pausedElapsedMs = _elapsedMs.value
        timerJob?.cancel()
        _state.value = TimerState.PAUSED
    }

    fun resume() {
        if (completionPending) return
        if (_state.value != TimerState.PAUSED) return

        startElapsed = timeSource.elapsedRealtime()
        _state.value = TimerState.RUNNING
        startTickLoop()
    }

    fun stop(): TimerResult? {
        if (_state.value == TimerState.IDLE) return null
        if (_state.value == TimerState.PREPARING) {
            timerJob?.cancel()
            reset()
            return null
        }

        val currentConfig = config ?: return null
        timerJob?.cancel()
        val elapsed = _elapsedMs.value
        val endEpoch = wallClockTimeSource()
        val result = TimerResult(
            projectId = currentConfig.projectId,
            startTimeEpoch = startEpoch,
            endTimeEpoch = endEpoch,
            durationMs = elapsed
        )
        reset()
        return result
    }

    fun cancel() {
        timerJob?.cancel()
        reset()
    }

    fun restore(session: ResolvedActiveTimerSession) {
        timerJob?.cancel()
        config = session.config
        startEpoch = session.sessionStartEpochMs ?: 0L
        startElapsed = timeSource.elapsedRealtime()
        pausedElapsedMs = session.elapsedMs
        lastAnnouncedMs = session.lastAnnouncedElapsedMs
        completionPending = session.shouldComplete
        _elapsedMs.value = session.elapsedMs
        _remainingMs.value = session.remainingMs
        _prepareRemainingMs.value = session.prepareRemainingMs

        when {
            session.shouldComplete -> {
                _state.value = TimerState.PAUSED
                _shouldAnnounce.tryEmit(-1L)
            }

            session.state == TimerState.PREPARING -> {
                _state.value = TimerState.PREPARING
                startPrepareLoop(session.prepareRemainingMs ?: 0L)
            }

            session.state == TimerState.RUNNING -> {
                _state.value = TimerState.RUNNING
                startTickLoop()
            }

            else -> {
                _state.value = TimerState.PAUSED
            }
        }
    }

    private fun reset() {
        _state.value = TimerState.IDLE
        _elapsedMs.value = 0L
        _remainingMs.value = null
        _prepareRemainingMs.value = null
        pausedElapsedMs = 0L
        lastAnnouncedMs = 0L
        completionPending = false
        config = null
        timerJob = null
    }

    private fun startPrepareLoop(prepareTimeMs: Long) {
        val prepareStart = timeSource.elapsedRealtime()
        timerJob = coroutineScope.launch {
            while (isActive) {
                val elapsed = timeSource.elapsedRealtime() - prepareStart
                val remaining = (prepareTimeMs - elapsed).coerceAtLeast(0L)
                _prepareRemainingMs.value = remaining
                if (remaining == 0L) {
                    _prepareRemainingMs.value = null
                    startEpoch = wallClockTimeSource()
                    startElapsed = timeSource.elapsedRealtime()
                    _state.value = TimerState.RUNNING
                    startTickLoop()
                    break
                }
                delay(100L)
            }
        }
    }

    private fun startTickLoop() {
        timerJob = coroutineScope.launch {
            while (isActive) {
                val now = timeSource.elapsedRealtime()
                val elapsed = pausedElapsedMs + (now - startElapsed)
                _elapsedMs.value = elapsed

                val currentConfig = config ?: break

                if (currentConfig.mode == TimerMode.COUNTDOWN && currentConfig.durationMs != null) {
                    val remaining = (currentConfig.durationMs - elapsed).coerceAtLeast(0L)
                    _remainingMs.value = remaining
                    if (remaining == 0L) {
                        if (!completionPending) {
                            completionPending = true
                            _state.value = TimerState.PAUSED
                            _shouldAnnounce.tryEmit(-1L)
                        }
                        break
                    }
                }

                val intervalMs = currentConfig.voiceIntervalMs
                if (intervalMs != null && intervalMs > 0L) {
                    if (elapsed - lastAnnouncedMs >= intervalMs) {
                        lastAnnouncedMs = (elapsed / intervalMs) * intervalMs
                        _shouldAnnounce.tryEmit(elapsed)
                    }
                }

                delay(100L)
            }
        }
    }
}
