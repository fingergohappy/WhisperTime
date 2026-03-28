package com.example.whispertime.timer

import com.example.whispertime.service.ActiveTimerSession

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

object ActiveTimerSessionResolver {
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

    private fun buildResolvedState(
        config: TimerConfig,
        state: TimerState,
        elapsedMs: Long,
        sessionStartEpochMs: Long?,
        lastAnnouncedElapsedMs: Long
    ): ResolvedActiveTimerSession {
        if (config.mode == TimerMode.COUNTDOWN && config.durationMs != null) {
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
