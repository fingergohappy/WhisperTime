package com.example.whispertime.ui.timer

import com.example.whispertime.timer.TimerState

internal enum class TimerCircleAlignment {
    TOP_CENTER,
    CENTER
}

internal data class TimerStageLayout(
    val circleAlignment: TimerCircleAlignment,
    val circleTopPaddingDp: Int,
    val circleOffsetYDp: Int,
    val showAmbientChrome: Boolean
)

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

internal fun timerStageLayout(timerState: TimerState): TimerStageLayout {
    return timerStageLayout(
        isTimerActive = timerState == TimerState.RUNNING ||
            timerState == TimerState.PREPARING ||
            timerState == TimerState.PAUSED
    )
}
