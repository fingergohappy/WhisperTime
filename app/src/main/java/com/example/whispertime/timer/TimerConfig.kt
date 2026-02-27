package com.example.whispertime.timer

/**
 * 计时器的配置参数。
 *
 * @property projectId 关联的项目 ID。
 * @property projectName 项目名称。
 * @property mode 计时模式（正计时或倒计时）。
 * @property durationMs 目标时长（毫秒）。在倒计时模式下必填。
 * @property voiceIntervalMs 语音播报的时间间隔（毫秒）。如果为 null 或 0，则不触发间隔播报。
 * @property prepareTimeMs 准备时间（毫秒）。如果设置了该值，计时开始前会进入准备阶段。
 */
data class TimerConfig(
    val projectId: Long,
    val projectName: String,
    val mode: TimerMode,
    val durationMs: Long? = null,
    val voiceIntervalMs: Long? = null,
    val prepareTimeMs: Long? = null
)

/**
 * 计时模式枚举。
 */
enum class TimerMode {
    /**
     * 正计时模式（时间增加）。
     */
    COUNT_UP,

    /**
     * 倒计时模式（时间减少）。
     */
    COUNTDOWN
}
