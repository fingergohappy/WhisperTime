package com.example.whispertime.timer

/**
 * 计时结束后的结果数据。
 *
 * @property projectId 关联的项目 ID。
 * @property startTimeEpoch 开始的时间戳（Unix 时间，毫秒）。
 * @property endTimeEpoch 结束的时间戳（Unix 时间，毫秒）。
 * @property durationMs 实际计时经过的总时长（毫秒）。
 */
data class TimerResult(
    val projectId: Long,
    val startTimeEpoch: Long,
    val endTimeEpoch: Long,
    val durationMs: Long
)
