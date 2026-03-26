package com.example.whispertime.timer

data class TimerConfig(
    val projectId: Long,
    val projectName: String,
    val mode: TimerMode,
    val durationMs: Long? = null,
    val voiceIntervalMs: Long? = null,
    val vibrationEnabled: Boolean = false,
    val prepareTimeMs: Long? = null
)

enum class TimerMode {
    COUNT_UP,
    COUNTDOWN
}
