package com.example.whispertime.timer

/**
 * 单次计时启动配置。
 *
 * @property projectId 所属项目主键。
 * @property projectName 所属项目名称。
 * @property mode 计时模式。
 * @property durationMs 倒计时总时长，正计时时为空。
 * @property voiceIntervalMs 语音播报间隔，未启用时为空。
 * @property vibrationEnabled 是否开启震动提醒。
 * @property prepareTimeMs 正式计时前的准备倒计时时长。
 */
data class TimerConfig(
    val projectId: Long,
    val projectName: String,
    val mode: TimerMode,
    val durationMs: Long? = null,
    val voiceIntervalMs: Long? = null,
    val vibrationEnabled: Boolean = false,
    val prepareTimeMs: Long? = null
)

/** 计时方向。 */
enum class TimerMode {
    /** 正计时。 */
    COUNT_UP,

    /** 倒计时。 */
    COUNTDOWN
}
