package com.example.offlineaitranslator.ui.coui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * COUI 17 fluid motion for the microphone control. All curves come from [CoUiMotion] so the mic
 * breathes, ripples and morphs with the same springs and easing as the rest of the app.
 */

/** Expanding concentric rings while recording, like ColorOS's voice recorder. */
@Composable
fun CoUiMicPulseRings(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "coui-mic-rings")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing)),
        label = "coui-mic-ring-phase",
    )
    Canvas(modifier = modifier) {
        val maxRadius = size.minDimension / 2f
        for (i in 0 until 3) {
            val p = (phase + i / 3f) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - p) * 0.32f),
                radius = maxRadius * (0.30f + 0.68f * p),
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = 2f),
            )
        }
    }
}

/** One-shot expanding ripple on touch, driven by the button's press state. */
@Composable
fun CoUiMicTouchRipple(
    pressed: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(460, easing = CoUiMotion.CouiEase))
        } else {
            progress.snapTo(0f)
        }
    }
    Canvas(modifier = modifier) {
        val p = progress.value
        if (p > 0f) {
            drawCircle(
                color = color.copy(alpha = (1f - p) * 0.45f),
                radius = size.minDimension / 2f * (0.40f + 0.60f * p),
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = 2.5f),
            )
        }
    }
}

/** Mic / stop glyph with a breathing idle state and a springy morph between states. */
@Composable
fun CoUiMicGlyph(
    isRecording: Boolean,
    tint: Color,
) {
    val transition = rememberInfiniteTransition(label = "coui-mic-breathe")
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.055f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = CoUiMotion.CouiEase),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "coui-mic-breathe-value",
    )
    AnimatedContent(
        targetState = isRecording,
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(190, easing = CoUiMotion.CouiEase)) +
                    scaleIn(
                        animationSpec = tween(280, easing = CoUiMotion.CouiMoveEase),
                        initialScale = 0.62f,
                    )
                ) togetherWith (
                fadeOut(animationSpec = tween(130, easing = CoUiMotion.CouiInEase)) +
                    scaleOut(
                        animationSpec = tween(180, easing = CoUiMotion.CouiInEase),
                        targetScale = 1.35f,
                    )
                )
        },
        label = "coui-mic-glyph",
    ) { recording ->
        if (recording) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
        } else {
            Box(modifier = Modifier.graphicsLayer { scaleX = breathe; scaleY = breathe }) {
                CoUiIcon(
                    kind = CoUiIconKind.MIC,
                    tint = tint,
                    iconSize = 36.dp,
                    strokeWidth = 2.4.dp,
                )
            }
        }
    }
}

/** Animated waveform bars: a travelling sine envelope replaces the static envelope while recording. */
@Composable
fun RowScope.CoUiWaveformBars(
    fractions: List<Float>,
    isRecording: Boolean,
    color: Color,
    idleColor: Color,
) {
    val transition = rememberInfiniteTransition(label = "coui-waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "coui-waveform-phase",
    )
    fractions.forEachIndexed { index, base ->
        val heightFraction = if (isRecording) {
            val wave = 0.5f + 0.5f * sin(phase.toDouble() * 2.0 * PI + index * 0.55).toFloat()
            (base * (0.45f + 0.55f * wave)).coerceIn(0.10f, 1f)
        } else {
            0.15f
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 2.dp)
                .fillMaxHeight(heightFraction)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isRecording) color else idleColor),
        )
    }
}
