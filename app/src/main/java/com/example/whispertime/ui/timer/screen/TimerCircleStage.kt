package com.example.whispertime.ui.timer.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState
import com.example.whispertime.ui.timer.TimerCircleAlignment
import com.example.whispertime.ui.timer.TimerStageLayout
import com.example.whispertime.ui.timer.timerCircleUiState

/**
 * 计时页中间圆盘舞台，集中处理圆盘展示、进度动画和空闲态快捷设置。
 *
 * @param modifier 外层布局修饰符。
 * @param stageLayout 当前计时舞台的对齐和显隐配置。
 * @param timerState 当前计时状态。
 * @param selectedMode 当前选择的计时模式。
 * @param elapsedMs 正计时已过毫秒数。
 * @param remainingMs 倒计时剩余毫秒数。
 * @param prepareRemainingMs 准备倒计时剩余毫秒数。
 * @param prepareSecondsText 准备时间输入文本。
 * @param countdownSecondsText 倒计时时长输入文本。
 * @param voiceIntervalSecondsText 语音提醒间隔输入文本。
 * @param bottomPanelState 底部面板展开状态。
 * @param showDrawer 项目抽屉是否正在显示。
 * @param onBottomPanelStateChange 底部面板状态变更回调。
 * @param onSelectedModeChange 计时模式变更回调。
 * @param onPrepareSecondsTextChange 准备时间输入变更回调。
 * @param onCountdownSecondsTextChange 倒计时时长输入变更回调。
 * @param onVoiceIntervalSecondsTextChange 语音提醒间隔输入变更回调。
 * @param onCircleClick 圆盘点击回调。
 * @param onStop 停止计时回调。
 */
@Composable
internal fun TimerCircleStage(
    modifier: Modifier = Modifier,
    stageLayout: TimerStageLayout,
    timerState: TimerState,
    selectedMode: TimerMode,
    elapsedMs: Long,
    remainingMs: Long?,
    prepareRemainingMs: Long?,
    prepareSecondsText: String,
    countdownSecondsText: String,
    voiceIntervalSecondsText: String,
    bottomPanelState: BottomPanelState,
    showDrawer: Boolean,
    onBottomPanelStateChange: (BottomPanelState) -> Unit,
    onSelectedModeChange: (TimerMode) -> Unit,
    onPrepareSecondsTextChange: (String) -> Unit,
    onCountdownSecondsTextChange: (String) -> Unit,
    onVoiceIntervalSecondsTextChange: (String) -> Unit,
    onCircleClick: () -> Unit,
    onStop: () -> Unit
) {
    /** 是否处于准备倒计时。 */
    val isPreparing = timerState == TimerState.PREPARING
    /** 空闲且计时值为 0 时展示模式和快捷设置。 */
    val idleAtZero = timerState == TimerState.IDLE && elapsedMs == 0L
    /** 圆盘提示文本和停止按钮状态。 */
    val circleUiState = timerCircleUiState(timerState)

    // 圆盘中央展示的秒数，准备态展示准备剩余，倒计时展示剩余，正计时展示已过。
    val displaySeconds = when {
        isPreparing -> ((prepareRemainingMs ?: 0L) + 999L) / 1000L
        selectedMode == TimerMode.COUNTDOWN -> ((remainingMs ?: 0L) / 1000L).coerceAtLeast(0L)
        else -> (elapsedMs / 1000L).coerceAtLeast(0L)
    }

    // 环形进度按不同计时模式映射到 0..1。
    val ringProgressTarget = when {
        isPreparing -> {
            val total = prepareSecondsText.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
            ((total - displaySeconds).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

        selectedMode == TimerMode.COUNTDOWN -> {
            val total = countdownSecondsText.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
            (1f - (displaySeconds.toFloat() / total.toFloat())).coerceIn(0f, 1f)
        }

        else -> ((elapsedMs / 1000L) % 60L).toFloat() / 60f
    }

    // 临近倒计时结束或正计时整分钟时跳过动画，避免显示进度滞后于语音播报。
    val shouldSkipRingAnimation = when {
        selectedMode == TimerMode.COUNTDOWN && !isPreparing -> {
            val remaining = remainingMs ?: 0L
            remaining <= 3000L || remaining == 0L
        }

        selectedMode == TimerMode.COUNT_UP && !isPreparing -> {
            elapsedMs > 0L && elapsedMs % 60_000L == 0L
        }

        else -> false
    }

    /** 倒计时模式下使用更短动画，降低进度环和语音提示的体感延迟。 */
    val ringAnimationDuration = if (selectedMode == TimerMode.COUNTDOWN && !isPreparing) 300 else 900
    /** 环形进度动画值。 */
    val animatedRingProgress by animateFloatAsState(
        targetValue = ringProgressTarget,
        animationSpec = tween(durationMillis = ringAnimationDuration),
        label = "ring_progress"
    )
    /** 实际绘制使用的环形进度。 */
    val ringProgress = if (shouldSkipRingAnimation) ringProgressTarget else animatedRingProgress
    /** 计时聚焦时的圆盘缩放动画值。 */
    val timerCircleFocusScale by animateFloatAsState(
        targetValue = if (stageLayout.showAmbientChrome) 1f else 1.12f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 180f),
        label = "timer_circle_focus_scale"
    )
    /** 圆盘纵向偏移动画值。 */
    val timerCircleOffsetY by animateDpAsState(
        targetValue = stageLayout.circleOffsetYDp.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 180f),
        label = "timer_circle_focus_offset"
    )

    Box(
        modifier = modifier,
        contentAlignment = when (stageLayout.circleAlignment) {
            TimerCircleAlignment.TOP_CENTER -> Alignment.TopCenter
            TimerCircleAlignment.CENTER -> Alignment.Center
        }
    ) {
        if (!showDrawer && bottomPanelState != BottomPanelState.COLLAPSED) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(showDrawer, bottomPanelState) {
                        detectTapGestures {
                            // 点击圆盘外侧空白区域收起底部面板。
                            onBottomPanelStateChange(BottomPanelState.COLLAPSED)
                        }
                    }
            )
        }

        Column(
            modifier = Modifier.padding(top = stageLayout.circleTopPaddingDp.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            AnimatedVisibility(
                visible = idleAtZero && !isPreparing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedMode == TimerMode.COUNT_UP,
                            onClick = { onSelectedModeChange(TimerMode.COUNT_UP) },
                            label = { Text("正计时") }
                        )
                        FilterChip(
                            selected = selectedMode == TimerMode.COUNTDOWN,
                            onClick = { onSelectedModeChange(TimerMode.COUNTDOWN) },
                            label = { Text("倒计时") }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            TimerCircle(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = timerCircleFocusScale
                        scaleY = timerCircleFocusScale
                    }
                    .offset(y = timerCircleOffsetY),
                primaryColor = MaterialTheme.colorScheme.primary,
                progress = ringProgress,
                isPreparing = isPreparing,
                preparingText = if (displaySeconds > 0L) displaySeconds.toString() else "GO!",
                timeText = formatLarge(displaySeconds),
                showStopButton = circleUiState.showStopButton,
                primaryHintText = circleUiState.primaryHintText,
                secondaryHintText = circleUiState.secondaryHintText,
                onClick = onCircleClick,
                onStop = onStop
            )

            AnimatedVisibility(
                visible = idleAtZero && !isPreparing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SmallSettingField(
                            title = "准备(秒)",
                            value = prepareSecondsText,
                            onValueChange = { onPrepareSecondsTextChange(digitsOnly(it)) }
                        )
                        if (selectedMode == TimerMode.COUNTDOWN) {
                            SmallSettingField(
                                title = "时长(秒)",
                                value = countdownSecondsText,
                                onValueChange = { onCountdownSecondsTextChange(digitsOnly(it)) }
                            )
                        }
                        SmallSettingField(
                            title = "间隔(秒)",
                            value = voiceIntervalSecondsText,
                            onValueChange = { onVoiceIntervalSecondsTextChange(digitsOnly(it)) }
                        )
                    }
                }
            }
        }
    }
}
