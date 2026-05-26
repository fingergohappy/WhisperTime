package com.example.whispertime.ui.timer.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.padding
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(340.dp)
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
            val radius = size.minDimension / 2f - 30.dp.toPx()
            val strokeWidth = 10.dp.toPx()

            // Outer multi-layered glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = breathingAlpha), Color.Transparent),
                    center = center,
                    radius = radius + 80.dp.toPx()
                ),
                radius = radius + 80.dp.toPx()
            )

            // Decorative background rings
            drawCircle(
                color = Color.White.copy(alpha = 0.03f),
                radius = radius + 20.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = radius,
                style = Stroke(width = 1.dp.toPx())
            )

            // Progress Arc
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to primaryColor.copy(alpha = 0.2f),
                    0.5f to primaryColor,
                    1.0f to primaryColor.copy(alpha = 0.2f)
                ),
                startAngle = -90f,
                sweepAngle = progress.coerceIn(0.001f, 1f) * 360f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Glow point at the end of progress
            val angleRad = Math.toRadians((progress * 360f - 90f).toDouble())
            val dotX = center.x + radius * Math.cos(angleRad).toFloat()
            val dotY = center.y + radius * Math.sin(angleRad).toFloat()

            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = Offset(dotX, dotY)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.8f), Color.Transparent),
                    center = Offset(dotX, dotY),
                    radius = 15.dp.toPx()
                ),
                radius = 15.dp.toPx(),
                center = Offset(dotX, dotY)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            if (isPreparing) {
                Text(
                    text = preparingText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Light,
                        color = primaryColor,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-4).sp
                    )
                )
            } else {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 110.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-4).sp,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                )

                if (showStopButton) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onStop,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                        ),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = primaryHintText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    secondaryHintText?.let {
                        Spacer(modifier = Modifier.height(8.dp))
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
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
