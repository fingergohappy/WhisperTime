package com.example.whispertime.timer

/**
 * 计时器的状态。
 */
enum class TimerState {
    /**
     * 空闲状态，未开始计时。
     */
    IDLE,

    /**
     * 准备状态，正在进行开始前的倒计时（如果配置了准备时间）。
     */
    PREPARING,

    /**
     * 运行状态，计时正在进行中。
     */
    RUNNING,

    /**
     * 暂停状态，计时已暂停。
     */
    PAUSED
}
