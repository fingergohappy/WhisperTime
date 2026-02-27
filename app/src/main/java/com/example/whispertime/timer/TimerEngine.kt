package com.example.whispertime.timer

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
    private val timeSource: TimeSource = TimeSource { System.currentTimeMillis() },
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
    private var timerJob: Job? = null

    fun start(config: TimerConfig) {
        if (_state.value != TimerState.IDLE) return

        this.config = config
        pausedElapsedMs = 0L
        _elapsedMs.value = 0L
        _remainingMs.value = if (config.mode == TimerMode.COUNTDOWN) config.durationMs else null

        val prepareTimeMs = config.prepareTimeMs
        if (prepareTimeMs != null && prepareTimeMs > 0L) {
            _state.value = TimerState.PREPARING
            _prepareRemainingMs.value = prepareTimeMs
            startPrepareLoop(prepareTimeMs)
            return
        }

        _prepareRemainingMs.value = null
        startEpoch = System.currentTimeMillis()
        startElapsed = timeSource.elapsedRealtime()
        _state.value = TimerState.RUNNING
        startTickLoop()
    }

    fun pause() {
        if (_state.value != TimerState.RUNNING) return

        pausedElapsedMs = _elapsedMs.value
        timerJob?.cancel()
        _state.value = TimerState.PAUSED
    }

    fun resume() {
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
        val endEpoch = System.currentTimeMillis()
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

    private fun reset() {
        _state.value = TimerState.IDLE
        _elapsedMs.value = 0L
        _remainingMs.value = null
        _prepareRemainingMs.value = null
        pausedElapsedMs = 0L
        config = null
        timerJob = null
    }

    private fun startPrepareLoop(prepareTimeMs: Long) {
        val prepareStartElapsed = timeSource.elapsedRealtime()
        timerJob = coroutineScope.launch {
            while (isActive) {
                val elapsedPreparing = timeSource.elapsedRealtime() - prepareStartElapsed
                val remainingPreparing = (prepareTimeMs - elapsedPreparing).coerceAtLeast(0L)
                _prepareRemainingMs.value = remainingPreparing

                if (remainingPreparing == 0L) {
                    _prepareRemainingMs.value = null
                    startEpoch = System.currentTimeMillis()
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
                        _shouldAnnounce.tryEmit(-1L)
                        reset()
                        break
                    }
                }

                delay(100L)
            }
        }
    }
}
