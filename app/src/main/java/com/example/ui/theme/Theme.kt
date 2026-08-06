package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RadioStudioColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = StudioMutedAccent,
    onPrimaryContainer = NeonCyan,
    secondary = NeonPurple,
    onSecondary = Color.White,
    tertiary = OnAirRed,
    onTertiary = Color.White,
    background = StudioDarkBackground,
    onBackground = StudioTextPrimary,
    surface = StudioCardSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioCardBorder,
    onSurfaceVariant = StudioTextSecondary
)

@Composable
fun RadioStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RadioStudioColorScheme,
        typography = Typography,
        content = content
    )
}

