package com.example.whispertime.ui.timer

import com.example.whispertime.timer.TimerState

/**
 * 计时圆盘的提示文本和按钮展示状态。
 *
 * @property showStopButton 是否显示显式停止按钮。
 * @property primaryHintText 主提示文案。
 * @property secondaryHintText 次级提示文案。
 */
internal data class TimerCircleUiState(
    val showStopButton: Boolean,
    val primaryHintText: String,
    val secondaryHintText: String? = null
)

/** 根据计时状态映射圆盘提示和操作按钮。 */
internal fun timerCircleUiState(timerState: TimerState): TimerCircleUiState {
    return when (timerState) {
        TimerState.PREPARING -> TimerCircleUiState(
            showStopButton = false,
            primaryHintText = "Tap to stop"
        )

        TimerState.PAUSED -> TimerCircleUiState(
            showStopButton = true,
            primaryHintText = "STOP",
            secondaryHintText = "Tap circle to resume"
        )

        TimerState.RUNNING -> TimerCircleUiState(
            showStopButton = false,
            primaryHintText = "Tap to pause"
        )

        TimerState.IDLE -> TimerCircleUiState(
            showStopButton = false,
            primaryHintText = "Tap to start"
        )
    }
}
