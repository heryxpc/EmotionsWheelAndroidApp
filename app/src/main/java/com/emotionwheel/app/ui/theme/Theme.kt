package com.emotionwheel.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The wheel already carries six strong colors, so the app chrome stays quiet: a warm
// off-white paper and a muted indigo for controls, close to the printed original.
private val Indigo = Color(0xFF6B6BB0)
private val IndigoLight = Color(0xFF9C9CD4)
private val Paper = Color(0xFFFBF9F6)
private val Ink = Color(0xFF1F1D24)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E2F5),
    onPrimaryContainer = Color(0xFF23225A),
    secondary = Color(0xFF6E6A7B),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0EDE8),
    onSurfaceVariant = Color(0xFF4A4750),
    outlineVariant = Color(0xFFDCD8D2),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF23225A),
    primaryContainer = Color(0xFF3B3A72),
    onPrimaryContainer = Color(0xFFE3E2F5),
    secondary = Color(0xFFC8C4D4),
    background = Color(0xFF141317),
    onBackground = Color(0xFFEDEAF0),
    surface = Color(0xFF1D1B21),
    onSurface = Color(0xFFEDEAF0),
    surfaceVariant = Color(0xFF2A282F),
    onSurfaceVariant = Color(0xFFC9C5CE),
    outlineVariant = Color(0xFF3A3841),
)

@Composable
fun EmotionWheelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
