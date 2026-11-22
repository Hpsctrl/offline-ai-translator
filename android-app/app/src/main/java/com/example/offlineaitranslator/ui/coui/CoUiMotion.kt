package com.example.offlineaitranslator.ui.coui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * COUI 17 motion, vendored from the library's `utils/SpringUtils.kt`, `PressFeedback.kt` and
 * `COUIOverScroller.kt` (Apache-2.0). The app deliberately vendors the design system rather than
 * adding a Maven dependency, so these are the same constants and curves the real library uses.
 */
object CoUiMotion {
    /** COUI's three path interpolators, used by transitions and state changes. */
    val CouiEase = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)
    val CouiInEase = CubicBezierEasing(0f, 0f, 0.1f, 1f)
    val CouiMoveEase = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

    /** COUI `SinkFeedback`: press scale 0.94 with spring(damping 0.8, stiffness 600). */
    const val SINK_SCALE = 0.94f
    const val SINK_DAMPING = 0.8f
    const val SINK_STIFFNESS = 600f

    /** COUI `SpringEngine`: critically damped, 0.4s period; 0.55s for flings over 5000px/s. */
    const val SPRING_DAMPING = 1.0f
    const val SPRING_PERIOD_SECONDS = 0.4f
    const val HIGH_VELOCITY_THRESHOLD = 5000f

    /** COUI over-scroll travel and release spring. */
    val OVERSCROLL_RANGE: Dp = 140.dp
    const val OVERSCROLL_DAMPING = 0.6f
    const val OVERSCROLL_STIFFNESS = 260f

    /**
     * COUI `SpringMath.obtainDampingDistance`: the rubber-band curve `x - x² + x³/3`. It turns a
     * linear drag past the scroll edge into the soft, progressively stiffer ColorOS stretch.
     */
    fun dampingDistance(normalizedInput: Float, range: Float): Float {
        val x = normalizedInput.coerceIn(0f, 1f)
        return range * (x - x * x + x * x * x / 3f)
    }
}

/**
 * COUI press feedback: scales the component down while pressed and springs it back on release.
 * Share [interactionSource] with the component's own `clickable(..., indication = null, ...)`.
 */
@Composable
fun Modifier.couiSinkPress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    sinkAmount: Float = CoUiMotion.SINK_SCALE,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) sinkAmount else 1f,
        animationSpec = spring(
            dampingRatio = CoUiMotion.SINK_DAMPING,
            stiffness = CoUiMotion.SINK_STIFFNESS,
        ),
        label = "coui-sink-press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * COUI over-scroll: rubber-band the content when [scrollState] is at either edge, then spring it
 * back with [CoUiMotion.OVERSCROLL_STIFFNESS]. Observes drags in the Initial pass without consuming
 * them, so the child's `verticalScroll` still owns normal scrolling.
 */
@Composable
fun Modifier.couiOverscroll(
    scrollState: ScrollState,
    range: Dp = CoUiMotion.OVERSCROLL_RANGE,
): Modifier {
    val rangePx = with(LocalDensity.current) { range.toPx() }
    var raw by remember { mutableFloatStateOf(0f) }
    var overscroll by remember { mutableFloatStateOf(0f) }
    val animated by animateFloatAsState(
        targetValue = overscroll,
        animationSpec = spring(
            dampingRatio = CoUiMotion.OVERSCROLL_DAMPING,
            stiffness = CoUiMotion.OVERSCROLL_STIFFNESS,
            visibilityThreshold = 0.1f,
        ),
        label = "coui-overscroll",
    )
    return this
        .pointerInput(scrollState, rangePx) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var stillPressed = true
                while (stillPressed) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    var dy = 0f
                    event.changes.forEach { change ->
                        dy += change.position.y - change.previousPosition.y
                    }
                    val atTop = scrollState.value <= 0
                    val atBottom = scrollState.value >= scrollState.maxValue
                    if ((dy > 0f && atTop) || (dy < 0f && atBottom)) {
                        raw += dy
                        val normalized = (abs(raw) / rangePx).coerceIn(0f, 1f)
                        val damped = CoUiMotion.dampingDistance(normalized, rangePx)
                        overscroll = if (raw > 0f) damped else -damped
                    } else if (raw != 0f) {
                        raw = 0f
                        overscroll = 0f
                    }
                    stillPressed = event.changes.any { it.pressed }
                }
                raw = 0f
                overscroll = 0f
            }
        }
        .graphicsLayer { translationY = animated }
}

/**
 * COUI 17 contour glow (`CONTENT_CAPSULE_*` material stroke): layered edge light plus an inner
 * contour around a capsule. Drawn behind the fill, so only the outer half of each stroke shows,
 * giving the soft ColorOS 17 rim without a runtime shader.
 */
fun Modifier.couiContourGlow(
    cornerRadius: Dp,
    color: Color,
    enabled: Boolean = true,
    intensity: Float = 1f,
): Modifier = drawBehind {
    if (!enabled || intensity <= 0f) return@drawBehind
    val radius = cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
    val topLeft = Offset(0.5f, 0.5f)
    val box = Size(size.width - 1f, size.height - 1f)
    drawRoundRect(
        color = color.copy(alpha = 0.08f * intensity),
        topLeft = topLeft,
        size = box,
        cornerRadius = CornerRadius(radius + 3f),
        style = Stroke(width = 7f),
    )
    drawRoundRect(
        color = color.copy(alpha = 0.16f * intensity),
        topLeft = topLeft,
        size = box,
        cornerRadius = CornerRadius(radius + 1f),
        style = Stroke(width = 3.5f),
    )
    drawRoundRect(
        color = color.copy(alpha = 0.48f * intensity),
        topLeft = topLeft,
        size = box,
        cornerRadius = CornerRadius(radius),
        style = Stroke(width = 1f),
    )
}

/**
 * COUI 17 touch motion: press sink, rubber-band drag follow and area-conserving stretch with an
 * under-damped release spring — the behaviour of COUIButton's `COUITouchMotionAnimator`.
 * Observes drags in the Initial pass without consuming them, so [clickable] still fires.
 */
@Composable
fun Modifier.couiTouchMotion(
    enabled: Boolean = true,
    maxStretch: Dp = 20.dp,
): Modifier {
    val rangePx = with(LocalDensity.current) { maxStretch.toPx() }
    var stretchX by remember { mutableFloatStateOf(0f) }
    var stretchY by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }
    val animatedX by animateFloatAsState(
        targetValue = stretchX,
        animationSpec = spring(0.65f, 380f, visibilityThreshold = 0.1f),
        label = "coui-touch-x",
    )
    val animatedY by animateFloatAsState(
        targetValue = stretchY,
        animationSpec = spring(0.65f, 380f, visibilityThreshold = 0.1f),
        label = "coui-touch-y",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(CoUiMotion.SINK_DAMPING, CoUiMotion.SINK_STIFFNESS),
        label = "coui-touch-press",
    )
    return this
        .pointerInput(enabled, rangePx) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position
                isPressed = true
                var pressed = true
                while (pressed) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.pressed } ?: event.changes.firstOrNull()
                    if (change != null) {
                        val rawX = change.position.x - start.x
                        val rawY = change.position.y - start.y
                        val nx = (abs(rawX) / rangePx).coerceIn(0f, 1f)
                        val ny = (abs(rawY) / rangePx).coerceIn(0f, 1f)
                        val dampedX = CoUiMotion.dampingDistance(nx, rangePx)
                        val dampedY = CoUiMotion.dampingDistance(ny, rangePx)
                        stretchX = if (rawX > 0f) dampedX else -dampedX
                        stretchY = if (rawY > 0f) dampedY else -dampedY
                    }
                    pressed = event.changes.any { it.pressed }
                }
                isPressed = false
                stretchX = 0f
                stretchY = 0f
            }
        }
        .graphicsLayer {
            translationX = animatedX * 0.45f
            translationY = animatedY * 0.45f
            val ex = abs(animatedX) / rangePx
            val ey = abs(animatedY) / rangePx
            scaleX = pressScale * (1f + ex * 0.10f - ey * 0.05f)
            scaleY = pressScale * (1f + ey * 0.10f - ex * 0.05f)
        }
}
