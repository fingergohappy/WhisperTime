package com.example.whispertime.ui.timer.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 计时圆盘组件，绘制进度环、时间文本和暂停态停止按钮。 */
@Composable
internal fun TimerCircle(
    modifier: Modifier = Modifier,
    primaryColor: Color,
    progress: Float,
    isPreparing: Boolean,
    preparingText: String,
    timeText: String,
    showStopButton: Boolean,
    primaryHintText: String,
    secondaryHintText: String?,
    onClick: () -> Unit,
    onStop: () -> Unit = {}
) {
    /** 圆盘点击交互源。 */
    val interactionSource = remember { MutableInteractionSource() }

    /** 圆盘是否处于按压状态。 */
    val isPressed by interactionSource.collectIsPressedAsState()

    /** 按压缩放动画值。 */
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "timer_circle_scale"
    )

    /** 按压时外圈光晕透明度。 */
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.28f else 0.12f,
        animationSpec = tween(durationMillis = 140),
        label = "timer_circle_glow"
    )

    Box(
        modifier = modifier
            .size(292.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                enabled = true,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 用 Canvas 手绘圆环，避免多个布局叠加导致进度和文本不对齐。
            val radius = size.minDimension / 2f - 10.dp.toPx()
            val stroke = 8.dp.toPx()

            drawCircle(
                color = primaryColor.copy(alpha = glowAlpha),
                radius = radius + 10.dp.toPx(),
                style = Stroke(width = 12.dp.toPx())
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = radius,
                style = Stroke(width = 4.dp.toPx())
            )

            drawArc(
                brush = Brush.sweepGradient(
                    listOf(primaryColor, primaryColor.copy(alpha = 0.4f), primaryColor)
                ),
                startAngle = -90f,
                sweepAngle = progress.coerceIn(0f, 1f) * 360f,
                useCenter = false,
                topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.03f),
                radius = radius - 20.dp.toPx(),
                blendMode = BlendMode.SrcOver
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isPreparing) {
                Text(
                    text = preparingText,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 100.sp),
                    color = primaryColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2).sp
                )
            } else {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 78.sp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2).sp,
                    textAlign = TextAlign.Center
                )

                if (showStopButton) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onStop,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    ) {
                        Text(primaryHintText, fontWeight = FontWeight.Bold)
                    }
                    secondaryHintText?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = primaryHintText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
