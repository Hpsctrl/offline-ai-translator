package com.example.offlineaitranslator.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

// Minimal, clean, universally responsive palette
private val MinimalLightColorScheme = lightColorScheme(
    primary = Color(0xFF1E293B),          // Slate 800
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1F5F9), // Slate 100
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFF0284C7),        // Sky 600
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = Color(0xFFD97706),         // Amber 600 accent
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92400E),
    background = Color(0xFFF8FAFC),       // Clean light gray-white
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B), // Slate 500
    outline = Color(0xFFCBD5E1),          // Slate 300
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val MinimalDarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),          // Sky 400
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E293B), // Slate 800
    onPrimaryContainer = Color(0xFFF8FAFC),
    secondary = Color(0xFF34D399),        // Emerald 400
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFFFBBF24),         // Warm Amber
    onTertiary = Color(0xFF0F172A),
    tertiaryContainer = Color(0xFF451A03),
    onTertiaryContainer = Color(0xFFFDE68A),
    background = Color(0xFF090D16),       // Deep slate night
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF111827),          // Gray 900
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1F2937),   // Gray 800
    onSurfaceVariant = Color(0xFF9CA3AF), // Gray 400
    outline = Color(0xFF374151),          // Gray 700
    error = Color(0xFFEF4444),
    onError = Color.White
)

@Composable
fun OfflineAITranslatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> MinimalDarkColorScheme
            else -> MinimalLightColorScheme
        }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
