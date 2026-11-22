package com.example.offlineaitranslator.ui.coui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.offlineaitranslator.theme.CoUiTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * COUI 17 voice waveform: the ColorOS voice-input spectrum — a row of mirrored, capsule-capped
 * bars in the theme accent, tallest at the centre and fading towards the edges. Driven by two
 * phase-shifted sine waves so the motion is smooth and organic at any refresh rate.
 */
@Composable
fun CoUiWaveform17(
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = CoUiTheme.colors.primaryText,
    idleColor: Color = CoUiTheme.colors.labelQuaternary,
) {
    val transition = rememberInfiniteTransition(label = "coui-waveform-17")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "coui-waveform-17-phase",
    )
    val density = LocalDensity.current
    val barWidthPx = with(density) { 3.dp.toPx() }
    val minBarPx = with(density) { 3.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CoUiTheme.colors.container8),
    ) {
        val width = size.width
        val height = size.height
        val count = 31
        val gap = ((width - count * barWidthPx) / (count + 1)).coerceAtLeast(0f)
        val centre = (count - 1) / 2f

        for (i in 0 until count) {
            val distance = abs(i - centre) / centre
            val envelope = 1f - 0.5f * distance
            val angle = phase * 2f * PI.toFloat()
            val primary = sin(angle - distance * 5.2f)
            val secondary = sin(angle * 1.65f - distance * 8.1f + 1.1f)
            val combined = ((primary * 0.62f + secondary * 0.38f) + 1f) / 2f

            val target = if (isRecording) {
                (0.16f + 0.84f * combined) * envelope
            } else {
                0.10f
            }
            val barHeight = (target * height * 0.82f).coerceAtLeast(minBarPx)
            val x = gap + i * (barWidthPx + gap)
            val y = (height - barHeight) / 2f
            val alpha = if (isRecording) 0.35f + 0.65f * envelope else 0.55f

            drawRoundRect(
                color = (if (isRecording) barColor else idleColor).copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(barWidthPx, barHeight),
                cornerRadius = CornerRadius(barWidthPx / 2f),
            )
        }
    }
}
