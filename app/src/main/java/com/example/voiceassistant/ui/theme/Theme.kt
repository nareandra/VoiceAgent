package com.example.voiceassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val KalkiDarkColorScheme = darkColorScheme(
    primary = KalkiNeonCyan,
    secondary = KalkiElectricBlue,
    tertiary = KalkiNeonPurple,
    background = KalkiObsidian,
    surface = KalkiSurface,
    onPrimary = KalkiObsidian,
    onSecondary = KalkiTextPrimary,
    onTertiary = KalkiTextPrimary,
    onBackground = KalkiTextPrimary,
    onSurface = KalkiTextPrimary,
    surfaceVariant = KalkiGlassSurface,
    onSurfaceVariant = KalkiTextSecondary,
    error = KalkiAlertRed
)

@Composable
fun VoiceAgentTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KalkiDarkColorScheme,
        typography = Typography,
        content = content
    )
}