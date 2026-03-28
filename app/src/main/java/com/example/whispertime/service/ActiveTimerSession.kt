package com.example.whispertime.service

import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState

data class ActiveTimerSession(
    val projectId: Long,
    val projectName: String,
    val mode: TimerMode,
    val durationMs: Long?,
    val voiceIntervalMs: Long?,
    val vibrationEnabled: Boolean,
    val state: TimerState,
    val prepareRemainingMs: Long?,
    val prepareReferenceEpochMs: Long?,
    val prepareReferenceElapsedRealtimeMs: Long?,
    val sessionStartEpochMs: Long?,
    val elapsedMs: Long,
    val runningReferenceElapsedRealtimeMs: Long?,
    val lastAnnouncedElapsedMs: Long
)
