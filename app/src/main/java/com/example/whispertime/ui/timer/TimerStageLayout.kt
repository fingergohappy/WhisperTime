package com.example.whispertime.ui.timer

import com.example.whispertime.timer.TimerState

/** 计时圆盘在页面中的垂直布局位置。 */
internal enum class TimerCircleAlignment {
    /** 靠近顶部居中，适合空闲态展示设置和历史面板。 */
    TOP_CENTER,

    /** 页面中心，适合运行或暂停时聚焦计时。 */
    CENTER
}

/**
 * 计时舞台布局配置。
 *
 * @property circleAlignment 圆盘对齐位置。
 * @property circleTopPaddingDp 圆盘顶部内边距。
 * @property circleOffsetYDp 圆盘动画偏移。
 * @property showAmbientChrome 是否展示顶部项目信息和底部面板。
 */
internal data class TimerStageLayout(
    val circleAlignment: TimerCircleAlignment,
    val circleTopPaddingDp: Int,
    val circleOffsetYDp: Int,
    val showAmbientChrome: Boolean
)

/** 根据是否聚焦计时决定舞台布局。 */
internal fun timerStageLayout(isTimerActive: Boolean): TimerStageLayout {
    return if (isTimerActive) {
        TimerStageLayout(
            circleAlignment = TimerCircleAlignment.CENTER,
            circleTopPaddingDp = 0,
            circleOffsetYDp = 0,
            showAmbientChrome = false
        )
    } else {
        TimerStageLayout(
            circleAlignment = TimerCircleAlignment.TOP_CENTER,
            circleTopPaddingDp = 36,
            circleOffsetYDp = 0,
            showAmbientChrome = true
        )
    }
}

/** 根据计时状态决定舞台布局。 */
internal fun timerStageLayout(timerState: TimerState): TimerStageLayout {
    return timerStageLayout(
        isTimerActive = timerState == TimerState.RUNNING ||
            timerState == TimerState.PREPARING ||
            timerState == TimerState.PAUSED
    )
}
