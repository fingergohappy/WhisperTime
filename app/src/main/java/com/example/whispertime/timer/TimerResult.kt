package com.example.whispertime.timer

data class TimerResult(
    val projectId: Long,
    val startTimeEpoch: Long,
    val endTimeEpoch: Long,
    val durationMs: Long,
)
