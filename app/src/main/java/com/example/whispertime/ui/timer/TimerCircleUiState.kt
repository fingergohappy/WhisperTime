package com.example.whispertime.ui.timer

import com.example.whispertime.timer.TimerState

internal data class TimerCircleUiState(
    val showStopButton: Boolean,
    val primaryHintText: String,
    val secondaryHintText: String? = null
)

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
