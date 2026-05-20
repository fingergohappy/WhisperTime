package com.example.whispertime.timer

/**
 * 计时结束后的结果，用于生成历史记录。
 *
 * @property projectId 所属项目主键。
 * @property startTimeEpoch 计时开始墙钟时间。
 * @property endTimeEpoch 计时结束墙钟时间。
 * @property durationMs 实际持续时长。
 */
data class TimerResult(
    val projectId: Long,
    val startTimeEpoch: Long,
    val endTimeEpoch: Long,
    val durationMs: Long
)
