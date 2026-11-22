package com.example.offlineaitranslator.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Composition locals carrying the vendored COUI tokens. */
val LocalCoUiColors = staticCompositionLocalOf { coUiLightColorScheme() }
val LocalCoUiTextStyles = staticCompositionLocalOf { coUiTextStyles() }

/** Access point mirroring upstream `COUITheme.colorScheme` / `COUITheme.textStyles`. */
object CoUiTheme {
    val colors: CoUiColors
        @Composable get() = LocalCoUiColors.current
    val textStyles: CoUiTextStyles
        @Composable get() = LocalCoUiTextStyles.current
}

/**
 * COUI 17 corner radii from `theme/COUIRoundCorner.kt` (Apache-2.0).
 *
 * Card lists use Normal.XL = 24dp; buttons and chips are height-derived capsules (Full = 72dp),
 * the smooth-corner squircle module is not vendored so the capsules are drawn as circular corners.
 */
object CoUiShapes {
    val card = RoundedCornerShape(24.dp)
    val section = RoundedCornerShape(16.dp)
    val button = RoundedCornerShape(72.dp)
    val chip = RoundedCornerShape(72.dp)
    val small = RoundedCornerShape(8.dp)
    val pill = RoundedCornerShape(72.dp)
}

/**
 * Vendored COUI theme. Light/dark tokens come from [coUiLightColorScheme] / [coUiDarkColorScheme].
 * `dynamicColor` is intentionally inert: deterministic tokens keep the offline/privacy story simple and
 * the parameter is preserved for source compatibility with the existing activity and tests.
 */
@Composable
fun OfflineAITranslatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) coUiDarkColorScheme() else coUiLightColorScheme()
    val materialColors =
        if (colors.isDark) {
            darkColorScheme(
                primary = colors.primary,
                onPrimary = colors.onPrimary,
                secondary = colors.primaryText,
                onSecondary = colors.onPrimary,
                background = colors.background,
                onBackground = colors.labelPrimary,
                surface = colors.surface,
                onSurface = colors.labelPrimary,
                surfaceVariant = colors.backgroundWithCard,
                onSurfaceVariant = colors.labelSecondary,
                outline = colors.outline,
                error = colors.error,
                onError = colors.onError,
            )
        } else {
            lightColorScheme(
                primary = colors.primary,
                onPrimary = colors.onPrimary,
                secondary = colors.primaryText,
                onSecondary = colors.onPrimary,
                background = colors.background,
                onBackground = colors.labelPrimary,
                surface = colors.surface,
                onSurface = colors.labelPrimary,
                surfaceVariant = colors.backgroundWithCard,
                onSurfaceVariant = colors.labelSecondary,
                outline = colors.outline,
                error = colors.error,
                onError = colors.onError,
            )
        }

    CompositionLocalProvider(
        LocalCoUiColors provides colors,
        LocalCoUiTextStyles provides coUiTextStyles(),
    ) {
        MaterialTheme(colorScheme = materialColors, typography = Typography, content = content)
    }
}

/** Helper for tone-based accents in COUI components. */
internal fun coUiBlend(foreground: Color, background: Color, alpha: Float): Color =
    Color(
        red = foreground.red * alpha + background.red * (1f - alpha),
        green = foreground.green * alpha + background.green * (1f - alpha),
        blue = foreground.blue * alpha + background.blue * (1f - alpha),
        alpha = 1f,
    )
