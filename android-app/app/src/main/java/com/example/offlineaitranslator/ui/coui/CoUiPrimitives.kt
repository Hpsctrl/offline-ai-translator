package com.example.offlineaitranslator.ui.coui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.offlineaitranslator.theme.CoUiShapes
import com.example.offlineaitranslator.theme.CoUiTheme

/**
 * Foundation-level COUI primitives.
 *
 * These deliberately do not use any `androidx.compose.material3` widget: COUI 17 has its own
 * geometry, colour usage and motion. Keeping the primitives here lets every higher-level COUI
 * component in [CoUiComponents.kt] assemble screens from the same foundation pieces the real
 * library uses, instead of re-skinning Material widgets.
 */

/** COUI text. Always takes an explicit COUI text style and token colour. */
@Composable
fun CoUiText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = CoUiTheme.colors.labelPrimary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign ?: style.textAlign),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** COUI surface: a clipped, token-coloured box with an optional hairline stroke. */
@Composable
internal fun CoUiSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    color: Color = CoUiTheme.colors.card,
    border: BorderStroke? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .then(if (border != null) Modifier.border(border, shape) else Modifier),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** COUI hairline divider. Rows in a `CardListGroup` own their own dividers; use this elsewhere. */
@Composable
fun CoUiDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 0.dp,
    color: Color = CoUiTheme.colors.divider,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .padding(start = startIndent)
            .background(color),
    )
}

/** COUI indeterminate circular progress (stroke arc, not a Material spinner). */
@Composable
fun CoUiCircularProgress(
    modifier: Modifier = Modifier,
    color: Color = CoUiTheme.colors.primaryText,
    strokeWidth: Dp = 2.5.dp,
) {
    val transition = rememberInfiniteTransition(label = "coUi-circular-progress")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "coUi-circular-progress-rotation",
    )
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = color.copy(alpha = 0.18f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
    }
}

/** COUI determinate linear progress with a rounded track and fill. */
@Composable
fun CoUiLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = CoUiTheme.colors.containerTheme,
    trackColor: Color = CoUiTheme.colors.container8,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CoUiShapes.pill)
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CoUiShapes.pill)
                .background(color)
                .align(Alignment.CenterStart),
        )
    }
}

/** COUI gesture dot used by the language picker and tab rows. */
@Composable
internal fun CoUiStateDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(6.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

/** The small set of monochrome icons the app needs, drawn with COUI stroke geometry. */
enum class CoUiIconKind {
    MIC, SWAP, PLAY, STOP, COPY, REFRESH, FOLDER, CHEVRON_DOWN, CHECK, CLOSE, NEURAL,
}

/** COUI 17 icon. Monochrome, 24dp viewport, token-tinted — never an emoji. */
@Composable
fun CoUiIcon(
    kind: CoUiIconKind,
    modifier: Modifier = Modifier,
    tint: Color = CoUiTheme.colors.primaryText,
    iconSize: Dp = 24.dp,
    strokeWidth: Dp = 2.dp,
) {
    Canvas(modifier = modifier.size(iconSize)) {
        val s = iconSize.toPx()
        val sw = strokeWidth.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), sw)
        fun outline(path: Path) = drawPath(path, tint, style = Stroke(sw))
        fun filled(path: Path) = drawPath(path, tint)

        when (kind) {
            CoUiIconKind.MIC -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(0.375f * s, 0.14f * s),
                    size = Size(0.25f * s, 0.42f * s),
                    cornerRadius = CornerRadius(0.125f * s),
                    style = Stroke(sw),
                )
                drawArc(
                    color = tint,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(0.26f * s, 0.38f * s),
                    size = Size(0.48f * s, 0.36f * s),
                    style = Stroke(sw),
                )
                line(0.5f, 0.74f, 0.5f, 0.88f)
                line(0.32f, 0.88f, 0.68f, 0.88f)
            }

            CoUiIconKind.SWAP -> {
                line(0.16f, 0.37f, 0.84f, 0.37f)
                filled(
                    Path().apply {
                        moveTo(0.68f * s, 0.25f * s)
                        lineTo(0.86f * s, 0.37f * s)
                        lineTo(0.68f * s, 0.49f * s)
                        close()
                    },
                )
                line(0.84f, 0.65f, 0.16f, 0.65f)
                filled(
                    Path().apply {
                        moveTo(0.32f * s, 0.53f * s)
                        lineTo(0.14f * s, 0.65f * s)
                        lineTo(0.32f * s, 0.77f * s)
                        close()
                    },
                )
            }

            CoUiIconKind.PLAY -> filled(
                Path().apply {
                    moveTo(0.34f * s, 0.22f * s)
                    lineTo(0.80f * s, 0.50f * s)
                    lineTo(0.34f * s, 0.78f * s)
                    close()
                },
            )

            CoUiIconKind.STOP -> drawRoundRect(
                color = tint,
                topLeft = Offset(0.30f * s, 0.30f * s),
                size = Size(0.40f * s, 0.40f * s),
                cornerRadius = CornerRadius(0.06f * s),
            )

            CoUiIconKind.COPY -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(0.30f * s, 0.30f * s),
                    size = Size(0.52f * s, 0.52f * s),
                    cornerRadius = CornerRadius(0.10f * s),
                    style = Stroke(sw),
                )
                outline(
                    Path().apply {
                        moveTo(0.66f * s, 0.22f * s)
                        lineTo(0.20f * s, 0.22f * s)
                        lineTo(0.20f * s, 0.68f * s)
                    },
                )
            }

            CoUiIconKind.REFRESH -> {
                drawArc(
                    color = tint,
                    startAngle = -80f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = Offset(0.18f * s, 0.18f * s),
                    size = Size(0.64f * s, 0.64f * s),
                    style = Stroke(sw),
                )
                filled(
                    Path().apply {
                        moveTo(0.82f * s, 0.28f * s)
                        lineTo(0.60f * s, 0.30f * s)
                        lineTo(0.74f * s, 0.46f * s)
                        close()
                    },
                )
            }

            CoUiIconKind.FOLDER -> {
                outline(
                    Path().apply {
                        moveTo(0.14f * s, 0.30f * s)
                        lineTo(0.40f * s, 0.30f * s)
                        lineTo(0.48f * s, 0.40f * s)
                        lineTo(0.86f * s, 0.40f * s)
                        lineTo(0.86f * s, 0.76f * s)
                        lineTo(0.14f * s, 0.76f * s)
                        close()
                    },
                )
            }

            CoUiIconKind.CHEVRON_DOWN -> outline(
                Path().apply {
                    moveTo(0.30f * s, 0.42f * s)
                    lineTo(0.50f * s, 0.62f * s)
                    lineTo(0.70f * s, 0.42f * s)
                },
            )

            CoUiIconKind.CHECK -> {
                line(0.20f, 0.52f, 0.42f, 0.74f)
                line(0.42f, 0.74f, 0.82f, 0.28f)
            }

            CoUiIconKind.CLOSE -> {
                line(0.28f, 0.28f, 0.72f, 0.72f)
                line(0.72f, 0.28f, 0.28f, 0.72f)
            }

            CoUiIconKind.NEURAL -> {
                val inputs = listOf(0.28f, 0.50f, 0.72f)
                val hidden = listOf(0.36f, 0.64f)
                val linkWidth = sw * 0.6f
                inputs.forEach { y ->
                    hidden.forEach { hy ->
                        drawLine(
                            color = tint,
                            start = Offset(0.24f * s, y * s),
                            end = Offset(0.50f * s, hy * s),
                            strokeWidth = linkWidth,
                        )
                    }
                }
                hidden.forEach { hy ->
                    drawLine(
                        color = tint,
                        start = Offset(0.54f * s, hy * s),
                        end = Offset(0.78f * s, 0.50f * s),
                        strokeWidth = linkWidth,
                    )
                }
                inputs.forEach { y ->
                    drawCircle(color = tint, radius = 0.045f * s, center = Offset(0.20f * s, y * s))
                }
                hidden.forEach { y ->
                    drawCircle(color = tint, radius = 0.055f * s, center = Offset(0.50f * s, y * s))
                }
                drawCircle(color = tint, radius = 0.070f * s, center = Offset(0.80f * s, 0.50f * s))
            }
        }
    }
}
