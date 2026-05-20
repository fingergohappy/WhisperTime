package com.example.whispertime.timer

/** 计时引擎的有限状态机状态。 */
enum class TimerState {
    /** 空闲状态，没有活跃计时。 */
    IDLE,

    /** 准备倒计时状态。 */
    PREPARING,

    /** 正式运行状态。 */
    RUNNING,

    /** 暂停状态，保留已计时时长。 */
    PAUSED
}
