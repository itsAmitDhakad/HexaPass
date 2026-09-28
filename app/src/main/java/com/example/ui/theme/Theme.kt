package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF031A21),
    primaryContainer = Color(0xFF003844),
    onPrimaryContainer = Color(0xFFA6F5FF),

    secondary = CyberEmerald,
    onSecondary = Color(0xFF002114),
    secondaryContainer = Color(0xFF003924),
    onSecondaryContainer = Color(0xFF8CF8C7),

    tertiary = CyberPurple,
    onTertiary = Color(0xFF1E0E3E),
    tertiaryContainer = Color(0xFF3B1E78),
    onTertiaryContainer = Color(0xFFE2D4FF),

    background = CyberBackground,
    onBackground = TextPrimary,

    surface = CyberSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    surfaceContainer = CyberSurface,
    surfaceContainerHigh = CyberSurfaceElevated,

    outline = CyberBorder,
    outlineVariant = Color(0xFF1E2E44),

    error = CyberRed,
    onError = Color.White
)

@Composable
fun HexaPassTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
