package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Stealth Silver & Refined Glass — Material3 dark scheme
private val StealthColorScheme = darkColorScheme(
    primary = CG.Platinum,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1E2026),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF000000),
    secondary = CG.SilverAccent,
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF262830),
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Color(0xFFCBD5E1),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF1E2026),
    onTertiaryContainer = Color(0xFFEDF0FF),
    background = CG.Void,
    onBackground = CG.TextPrimary,
    surface = CG.Void,
    onSurface = CG.TextPrimary,
    surfaceVariant = Color(0xFF16171A),
    onSurfaceVariant = CG.TextMuted,
    surfaceTint = CG.Platinum,
    inverseSurface = CG.TextPrimary,
    inverseOnSurface = CG.Void,
    outline = Color(0x338D90A0),
    outlineVariant = Color(0xFF434655),
    error = CG.Error,
    onError = CG.OnError,
    errorContainer = CG.ErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6)
)

private val CobaltColorScheme = StealthColorScheme
private val ObsidianColorScheme = StealthColorScheme

@Composable
fun CoveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = StealthColorScheme,
        typography = Typography,
        content = content
    )
}
