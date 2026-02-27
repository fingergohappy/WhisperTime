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

/**
 * 时间源接口，用于提供当前时间。
 * 使用接口以便在单元测试中通过模拟时间来测试计时逻辑。
 */
fun interface TimeSource {
    /**
     * 返回当前系统时间（毫秒）。
     */
    fun elapsedRealtime(): Long
}

/**
 * 计时器核心引擎。
 * 负责处理计时的开始、暂停、恢复、停止和取消逻辑。
 * 通过 Kotlin Flow 暴露计时状态、已用时间、剩余时间等数据，支持正计时和倒计时模式。
 *
 * @property timeSource 时间提供源，默认为系统当前时间。
 * @property coroutineScope 协程作用域，用于运行计时循环，默认为 Default 调度器。
 */
class TimerEngine(
    private val timeSource: TimeSource = TimeSource { System.currentTimeMillis() },
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val _state = MutableStateFlow(TimerState.IDLE)

    /**
     * 当前计时器的运行状态。
     */
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)

    /**
     * 自开始以来已累计经过的时间（毫秒）。
     * 在暂停期间该值保持不变，恢复后继续累加。
     */
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _remainingMs = MutableStateFlow<Long?>(null)

    /**
     * 在倒计时模式下的剩余时间（毫秒）。
     */
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    private val _prepareRemainingMs = MutableStateFlow<Long?>(null)

    /**
     * 准备阶段的剩余时间（毫秒）。
     */
    val prepareRemainingMs: StateFlow<Long?> = _prepareRemainingMs.asStateFlow()

    private val _shouldAnnounce = MutableSharedFlow<Long>(extraBufferCapacity = 10)

    /**
     * 触发语音播报的信号流。
     * 发出的值为当前的已用时间（毫秒）。
     * 特殊值 -1L 表示计时正常结束（倒计时归零）。
     */
    val shouldAnnounce: SharedFlow<Long> = _shouldAnnounce.asSharedFlow()

    private var config: TimerConfig? = null
    private var startEpoch: Long = 0L
    private var startElapsed: Long = 0L
    private var pausedElapsedMs: Long = 0L
    private var lastAnnouncedMs: Long = 0L
    private var timerJob: Job? = null

    /**
     * 根据配置启动计时器。
     * 仅在 IDLE 状态下有效。如果配置了准备时间，则先进入 PREPARING 状态。
     */
    fun start(config: TimerConfig) {
        if (_state.value != TimerState.IDLE) return

        this.config = config
        pausedElapsedMs = 0L
        lastAnnouncedMs = 0L
        _elapsedMs.value = 0L
        _remainingMs.value = config.durationMs

        if (config.prepareTimeMs != null && config.prepareTimeMs > 0L) {
            _state.value = TimerState.PREPARING
            _prepareRemainingMs.value = config.prepareTimeMs
            startPrepareLoop(config.prepareTimeMs)
        } else {
            startEpoch = System.currentTimeMillis()
            startElapsed = timeSource.elapsedRealtime()
            _state.value = TimerState.RUNNING
            startTickLoop()
        }
    }

    /**
     * 暂停当前正在运行的计时。
     * 仅在 RUNNING 状态下有效。暂停会取消当前的计时协程任务并记录当前进度。
     */
    fun pause() {
        if (_state.value != TimerState.RUNNING) return

        pausedElapsedMs = _elapsedMs.value
        timerJob?.cancel()
        _state.value = TimerState.PAUSED
    }

    /**
     * 恢复已暂停的计时。
     * 仅在 PAUSED 状态下有效。恢复会重新启动计时协程任务。
     */
    fun resume() {
        if (_state.value != TimerState.PAUSED) return

        startElapsed = timeSource.elapsedRealtime()
        _state.value = TimerState.RUNNING
        startTickLoop()
    }

    /**
     * 停止计时并返回结果。
     * 如果处于准备阶段，直接重置并返回 null。
     * 停止会记录结束时间戳并重置引擎状态。
     */
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

    /**
     * 强制取消计时，不返回任何结果并重置所有状态。
     */
    fun cancel() {
        timerJob?.cancel()
        reset()
    }

    /**
     * 重置所有内部变量和 Flow 状态到初始状态。
     */
    private fun reset() {
        _state.value = TimerState.IDLE
        _elapsedMs.value = 0L
        _remainingMs.value = null
        _prepareRemainingMs.value = null
        pausedElapsedMs = 0L
        lastAnnouncedMs = 0L
        config = null
        timerJob = null
    }

    /**
     * 启动准备阶段的计时循环。
     * 在准备时间结束以后，自动切换到 RUNNING 状态并开始主计时循环。
     */
    private fun startPrepareLoop(prepareTimeMs: Long) {
        val prepareStart = timeSource.elapsedRealtime()
        timerJob = coroutineScope.launch {
            while (isActive) {
                val elapsed = timeSource.elapsedRealtime() - prepareStart
                val remaining = (prepareTimeMs - elapsed).coerceAtLeast(0L)
                _prepareRemainingMs.value = remaining
                if (remaining == 0L) {
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

    /**
     * 启动主计时循环。
     * 负责更新已用时间、剩余时间，并根据配置的时间间隔触发语音播报信号。
     * 在倒计时结束时会自动触发完成信号并重置计时器。
     */
    private fun startTickLoop() {
        timerJob = coroutineScope.launch {
            while (isActive) {
                val now = timeSource.elapsedRealtime()
                val elapsed = pausedElapsedMs + (now - startElapsed)
                _elapsedMs.value = elapsed

                val currentConfig = config ?: break

                // 倒计时逻辑：计算剩余时间并在归零时触发结束
                if (currentConfig.mode == TimerMode.COUNTDOWN && currentConfig.durationMs != null) {
                    val remaining = (currentConfig.durationMs - elapsed).coerceAtLeast(0L)
                    _remainingMs.value = remaining
                    if (remaining == 0L) {
                        _shouldAnnounce.tryEmit(-1L)
                        reset()
                        break
                    }
                }

                // 语音播报触发逻辑：按设定的间隔频率发出信号
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
