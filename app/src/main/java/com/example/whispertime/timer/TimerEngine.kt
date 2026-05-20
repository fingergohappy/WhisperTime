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

/** 单调时钟来源，抽象出来便于测试计时推进。 */
fun interface TimeSource {
    /** 返回自启动以来的单调时间毫秒数。 */
    fun elapsedRealtime(): Long
}

/** 计时核心状态机，负责准备倒计时、正/倒计时推进和播报信号。 */
class TimerEngine(
    /** 单调时钟来源，用于计算运行时长。 */
    private val timeSource: TimeSource = TimeSource { SystemClock.elapsedRealtime() },
    /** 墙钟时间来源，用于生成历史记录的开始和结束时间。 */
    private val wallClockTimeSource: () -> Long = { System.currentTimeMillis() },
    /** 计时循环所在协程作用域。 */
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    /** 内部计时状态。 */
    private val _state = MutableStateFlow(TimerState.IDLE)
    /** 对外暴露的计时状态。 */
    val state: StateFlow<TimerState> = _state.asStateFlow()

    /** 内部已计时时长。 */
    private val _elapsedMs = MutableStateFlow(0L)
    /** 对外暴露的已计时时长。 */
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    /** 内部倒计时剩余时长。 */
    private val _remainingMs = MutableStateFlow<Long?>(null)
    /** 对外暴露的倒计时剩余时长。 */
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    /** 内部准备倒计时剩余时长。 */
    private val _prepareRemainingMs = MutableStateFlow<Long?>(null)
    /** 对外暴露的准备倒计时剩余时长。 */
    val prepareRemainingMs: StateFlow<Long?> = _prepareRemainingMs.asStateFlow()

    /** 内部播报信号流，-1L 表示倒计时完成。 */
    private val _shouldAnnounce = MutableSharedFlow<Long>(extraBufferCapacity = 10)
    /** 对外暴露的播报信号流。 */
    val shouldAnnounce: SharedFlow<Long> = _shouldAnnounce.asSharedFlow()

    /** 当前计时配置。 */
    private var config: TimerConfig? = null
    /** 当前计时开始的墙钟时间。 */
    private var startEpoch: Long = 0L
    /** 当前运行片段开始的单调时钟时间。 */
    private var startElapsed: Long = 0L
    /** 暂停前累计的已计时时长。 */
    private var pausedElapsedMs: Long = 0L
    /** 最近一次触发播报的毫秒位置。 */
    private var lastAnnouncedMs: Long = 0L
    /** 倒计时完成信号是否已经发出。 */
    private var completionPending: Boolean = false
    /** 当前计时或准备循环任务。 */
    private var timerJob: Job? = null

    /** 按指定配置启动计时，非空闲状态下忽略重复启动。 */
    fun start(config: TimerConfig) {
        if (_state.value != TimerState.IDLE) return

        this.config = config
        pausedElapsedMs = 0L
        lastAnnouncedMs = 0L
        completionPending = false
        _elapsedMs.value = 0L
        _remainingMs.value = config.durationMs

        if (config.prepareTimeMs != null && config.prepareTimeMs > 0L) {
            // 有准备时间时先进入 PREPARING，正式计时开始时间在准备结束时确定。
            _state.value = TimerState.PREPARING
            _prepareRemainingMs.value = config.prepareTimeMs
            startPrepareLoop(config.prepareTimeMs)
        } else {
            // 没有准备时间时立即进入 RUNNING。
            startEpoch = wallClockTimeSource()
            startElapsed = timeSource.elapsedRealtime()
            _state.value = TimerState.RUNNING
            startTickLoop()
        }
    }

    /** 暂停运行中的计时，保留当前已计时时长。 */
    fun pause() {
        if (completionPending) return
        if (_state.value != TimerState.RUNNING) return

        pausedElapsedMs = _elapsedMs.value
        timerJob?.cancel()
        _state.value = TimerState.PAUSED
    }

    /** 从暂停状态恢复运行。 */
    fun resume() {
        if (completionPending) return
        if (_state.value != TimerState.PAUSED) return

        startElapsed = timeSource.elapsedRealtime()
        _state.value = TimerState.RUNNING
        startTickLoop()
    }

    /** 停止当前计时，运行阶段返回可保存为历史记录的结果。 */
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

    /** 取消计时且不生成历史记录。 */
    fun cancel() {
        timerJob?.cancel()
        reset()
    }

    /** 根据持久化恢复结果重建引擎状态和循环任务。 */
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
                // 恢复时倒计时已结束，交由服务处理完成播报和记录保存。
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

    /** 清空引擎状态回到空闲。 */
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

    /** 启动准备倒计时循环，结束后自动切换到正式计时循环。 */
    private fun startPrepareLoop(prepareTimeMs: Long) {
        val prepareStart = timeSource.elapsedRealtime()
        timerJob = coroutineScope.launch {
            while (isActive) {
                val elapsed = timeSource.elapsedRealtime() - prepareStart
                val remaining = (prepareTimeMs - elapsed).coerceAtLeast(0L)
                _prepareRemainingMs.value = remaining
                if (remaining == 0L) {
                    // 准备结束时才记录正式计时开始时间，避免历史记录包含准备阶段。
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

    /** 启动 100ms 精度的计时循环，负责时长推进、倒计时完成和周期播报。 */
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
                        // 完成信号只发送一次，避免服务重复保存记录或重复播报结束。
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
                        // 将播报点对齐到间隔整数倍，避免循环抖动导致越积越偏。
                        lastAnnouncedMs = (elapsed / intervalMs) * intervalMs
                        _shouldAnnounce.tryEmit(elapsed)
                    }
                }

                delay(100L)
            }
        }
    }
}
