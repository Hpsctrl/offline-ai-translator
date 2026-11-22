package com.example.offlineaitranslator.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.offlineaitranslator.R

/**
 * Vendored COUI (ColorOS 17) type scale.
 *
 * Source: https://github.com/suqi8/coui — `theme/TextStyles.kt` (Apache-2.0). Every step uses the
 * real COUI text size, weight and `android:lineSpacingMultiplier`; Compose's `lineHeight` folds in
 * Roboto's 1.1719 metrics height so a line box measures the same as ColorOS on every platform.
 * `LineHeightStyle(Proportional, None)` keeps the leading that Android's TextView leaves on
 * single-line text instead of Compose's default trim.
 */
@Immutable
data class CoUiTextStyles(
    val displayL: TextStyle,
    val display: TextStyle,
    val displayM: TextStyle,
    val displayS: TextStyle,
    val displayXS: TextStyle,
    val headlineL: TextStyle,
    val headlineM: TextStyle,
    val headlineS: TextStyle,
    val headlineXS: TextStyle,
    val bodyXL: TextStyle,
    val bodyL: TextStyle,
    val bodyM: TextStyle,
    val bodyS: TextStyle,
    val bodyXS: TextStyle,
    val buttonL: TextStyle,
    val buttonM: TextStyle,
    val buttonS: TextStyle,
    val description: TextStyle,
    val caption: TextStyle,
    val tag: TextStyle,
)

/** Roboto hhea (1900 + 500) / 2048: Android scales this metrics height, not the font size. */
private const val COUI_FONT_METRICS_HEIGHT = 1.1719f

private val COUI_LINE_HEIGHT_STYLE = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Proportional,
    trim = LineHeightStyle.Trim.None,
)

/**
 * OPPO Sans (bundled under `res/font/`), the system UI typeface on ColorOS / OPPO devices.
 * Sourced from OPPO's own Mente Lab repository. All weights map to the bundled file; Compose
 * selects the nearest available weight for the COUI steps.
 */
val OppoSans = FontFamily(
    Font(R.font.oppo_sans, FontWeight.Normal),
    Font(R.font.oppo_sans, FontWeight.Medium),
    Font(R.font.oppo_sans, FontWeight.SemiBold),
    Font(R.font.oppo_sans, FontWeight.Bold),
)

private fun step(
    size: Float,
    multiplier: Float,
    weight: FontWeight? = null,
    family: FontFamily = OppoSans,
): TextStyle = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (multiplier * COUI_FONT_METRICS_HEIGHT).em,
    lineHeightStyle = COUI_LINE_HEIGHT_STYLE,
)

fun coUiTextStyles(): CoUiTextStyles = CoUiTextStyles(
    displayL = step(72f, 1.1384f, FontWeight.Medium),
    display = step(48f, 1f, FontWeight.Medium),
    displayM = step(36f, 1.191f, FontWeight.SemiBold),
    displayS = step(32f, 1.2322f, FontWeight.SemiBold),
    displayXS = step(24f, 1.2f, FontWeight.SemiBold),
    headlineL = step(28f, 1.2245f, FontWeight.Medium),
    headlineM = step(20f, 1.1831f, FontWeight.Medium),
    headlineS = step(18f, 1.1429f, FontWeight(550)),
    headlineXS = step(16f, 1.158f, FontWeight.Medium),
    bodyXL = step(16f, 1.158f),
    bodyL = step(15f, 1.1321f),
    bodyM = step(14f, 1.2245f),
    bodyS = step(13f, 1.1739f),
    bodyXS = step(12f, 1.143f),
    buttonL = step(16f, 1.1579f, FontWeight.Medium),
    buttonM = step(14f, 1.2245f, FontWeight.Medium),
    buttonS = step(12f, 1.143f, FontWeight.Medium),
    description = step(12f, 1.143f, FontWeight.Medium),
    caption = step(10f, 1.2f),
    tag = step(10f, 1.2f, FontWeight.Medium),
)

/** Material3 typography derived from the COUI scale, kept for any legacy Material widget. */
val Typography: Typography =
    coUiTextStyles().let { t ->
        Typography(
            displayLarge = t.displayL,
            displayMedium = t.display,
            displaySmall = t.displayM,
            headlineLarge = t.headlineL,
            headlineMedium = t.headlineM,
            headlineSmall = t.headlineS,
            titleLarge = t.headlineXS,
            titleMedium = t.bodyXL,
            titleSmall = t.buttonL,
            bodyLarge = t.bodyXL,
            bodyMedium = t.bodyM,
            bodySmall = t.bodyS,
            labelLarge = t.buttonL,
            labelMedium = t.buttonM,
            labelSmall = t.bodyXS,
        )
    }
