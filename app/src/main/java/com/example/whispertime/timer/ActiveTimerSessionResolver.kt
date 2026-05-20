package com.example.whispertime.timer

import com.example.whispertime.service.ActiveTimerSession

/**
 * 恢复后的活跃计时状态，供 [TimerEngine] 继续运行。
 *
 * @property config 计时配置。
 * @property state 恢复后应该进入的状态。
 * @property elapsedMs 已经过的计时时长。
 * @property remainingMs 倒计时剩余时长，正计时时为空。
 * @property prepareRemainingMs 准备倒计时剩余时长。
 * @property sessionStartEpochMs 正式计时开始的墙钟时间。
 * @property lastAnnouncedElapsedMs 最近播报的已计时时长。
 * @property shouldComplete 恢复时是否已经到达倒计时终点。
 */
data class ResolvedActiveTimerSession(
    val config: TimerConfig,
    val state: TimerState,
    val elapsedMs: Long,
    val remainingMs: Long?,
    val prepareRemainingMs: Long?,
    val sessionStartEpochMs: Long?,
    val lastAnnouncedElapsedMs: Long,
    val shouldComplete: Boolean
)

/** 活跃会话恢复器，基于保存时刻和当前单调时钟推导最新状态。 */
object ActiveTimerSessionResolver {
    /** 解析持久化会话，补偿应用休眠或进程重启期间流逝的时间。 */
    fun resolve(
        session: ActiveTimerSession,
        nowElapsedRealtimeMs: Long
    ): ResolvedActiveTimerSession {
        val config = TimerConfig(
            projectId = session.projectId,
            projectName = session.projectName,
            mode = session.mode,
            durationMs = session.durationMs,
            voiceIntervalMs = session.voiceIntervalMs,
            vibrationEnabled = session.vibrationEnabled,
            prepareTimeMs = session.prepareRemainingMs
        )

        return when (session.state) {
            TimerState.PREPARING -> resolvePreparing(session, config, nowElapsedRealtimeMs)
            TimerState.RUNNING -> resolveRunning(
                config = config,
                // RUNNING 会话必须具备开始时间和运行参考时钟，否则保存数据无效。
                sessionStartEpochMs = requireNotNull(session.sessionStartEpochMs),
                baseElapsedMs = session.elapsedMs,
                runningReferenceElapsedRealtimeMs = requireNotNull(session.runningReferenceElapsedRealtimeMs),
                lastAnnouncedElapsedMs = session.lastAnnouncedElapsedMs,
                nowElapsedRealtimeMs = nowElapsedRealtimeMs
            )

            TimerState.PAUSED -> resolvePaused(session, config)
            TimerState.IDLE -> ResolvedActiveTimerSession(
                config = config,
                state = TimerState.IDLE,
                elapsedMs = 0L,
                remainingMs = config.durationMs,
                prepareRemainingMs = null,
                sessionStartEpochMs = null,
                lastAnnouncedElapsedMs = 0L,
                shouldComplete = false
            )
        }
    }

    /** 恢复准备倒计时，必要时直接跨入正式计时状态。 */
    private fun resolvePreparing(
        session: ActiveTimerSession,
        config: TimerConfig,
        nowElapsedRealtimeMs: Long
    ): ResolvedActiveTimerSession {
        val prepareRemainingMs = requireNotNull(session.prepareRemainingMs)
        val prepareReferenceEpochMs = requireNotNull(session.prepareReferenceEpochMs)
        val prepareReferenceElapsedRealtimeMs = requireNotNull(session.prepareReferenceElapsedRealtimeMs)
        val elapsedSinceReference = (nowElapsedRealtimeMs - prepareReferenceElapsedRealtimeMs).coerceAtLeast(0L)
        val remainingPrepareMs = prepareRemainingMs - elapsedSinceReference

        return if (remainingPrepareMs > 0L) {
            // 准备时间还没结束，继续保留 PREPARING 状态。
            ResolvedActiveTimerSession(
                config = config,
                state = TimerState.PREPARING,
                elapsedMs = 0L,
                remainingMs = config.durationMs,
                prepareRemainingMs = remainingPrepareMs,
                sessionStartEpochMs = null,
                lastAnnouncedElapsedMs = session.lastAnnouncedElapsedMs,
                shouldComplete = false
            )
        } else {
            // 准备阶段已在后台结束，把超出的时间计入正式计时。
            val sessionStartEpochMs = prepareReferenceEpochMs + prepareRemainingMs
            val runningElapsedMs = (-remainingPrepareMs).coerceAtLeast(0L)
            resolveRunning(
                config = config.copy(prepareTimeMs = null),
                sessionStartEpochMs = sessionStartEpochMs,
                baseElapsedMs = runningElapsedMs,
                runningReferenceElapsedRealtimeMs = nowElapsedRealtimeMs,
                lastAnnouncedElapsedMs = session.lastAnnouncedElapsedMs,
                nowElapsedRealtimeMs = nowElapsedRealtimeMs
            )
        }
    }

    /** 恢复运行状态，使用单调时钟差值补齐后台流逝时长。 */
    private fun resolveRunning(
        config: TimerConfig,
        sessionStartEpochMs: Long,
        baseElapsedMs: Long,
        runningReferenceElapsedRealtimeMs: Long,
        lastAnnouncedElapsedMs: Long,
        nowElapsedRealtimeMs: Long
    ): ResolvedActiveTimerSession {
        val elapsedMs = baseElapsedMs + (nowElapsedRealtimeMs - runningReferenceElapsedRealtimeMs).coerceAtLeast(0L)
        return buildResolvedState(
            config = config.copy(prepareTimeMs = null),
            state = TimerState.RUNNING,
            elapsedMs = elapsedMs,
            sessionStartEpochMs = sessionStartEpochMs,
            lastAnnouncedElapsedMs = lastAnnouncedElapsedMs
        )
    }

    /** 恢复暂停状态，暂停期间不累加流逝时间。 */
    private fun resolvePaused(
        session: ActiveTimerSession,
        config: TimerConfig
    ): ResolvedActiveTimerSession {
        return buildResolvedState(
            config = config.copy(prepareTimeMs = null),
            state = TimerState.PAUSED,
            elapsedMs = session.elapsedMs,
            sessionStartEpochMs = session.sessionStartEpochMs,
            lastAnnouncedElapsedMs = session.lastAnnouncedElapsedMs
        )
    }

    /** 根据计时模式生成统一的恢复状态，并处理倒计时完成边界。 */
    private fun buildResolvedState(
        config: TimerConfig,
        state: TimerState,
        elapsedMs: Long,
        sessionStartEpochMs: Long?,
        lastAnnouncedElapsedMs: Long
    ): ResolvedActiveTimerSession {
        if (config.mode == TimerMode.COUNTDOWN && config.durationMs != null) {
            // 倒计时恢复时需要把已过时间钳制到总时长，并标记是否需要自动完成。
            val clampedElapsedMs = elapsedMs.coerceAtMost(config.durationMs)
            val remainingMs = (config.durationMs - elapsedMs).coerceAtLeast(0L)
            val completed = remainingMs == 0L
            return ResolvedActiveTimerSession(
                config = config,
                state = if (completed) TimerState.PAUSED else state,
                elapsedMs = clampedElapsedMs,
                remainingMs = remainingMs,
                prepareRemainingMs = null,
                sessionStartEpochMs = sessionStartEpochMs,
                lastAnnouncedElapsedMs = lastAnnouncedElapsedMs,
                shouldComplete = completed
            )
        }

        return ResolvedActiveTimerSession(
            config = config,
            state = state,
            elapsedMs = elapsedMs,
            remainingMs = null,
            prepareRemainingMs = null,
            sessionStartEpochMs = sessionStartEpochMs,
            lastAnnouncedElapsedMs = lastAnnouncedElapsedMs,
            shouldComplete = false
        )
    }
}
