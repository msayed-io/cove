package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Cobalt Glass — Material3 dark scheme مبني على توكنز CG المرجعية فقط.
private val CobaltColorScheme = darkColorScheme(
    primary = CG.CobaltLight,
    onPrimary = Color(0xFF002A78),
    primaryContainer = CG.Cobalt,
    onPrimaryContainer = Color(0xFFEEEFFF),
    inversePrimary = Color(0xFF0053DB),
    secondary = CG.CobaltGlow,
    onSecondary = Color(0xFF00315D),
    secondaryContainer = Color(0xFF0267B8),
    onSecondaryContainer = Color(0xFFD6E5FF),
    tertiary = Color(0xFFADC6FF),
    onTertiary = Color(0xFF002E6A),
    tertiaryContainer = Color(0xFF0F69DC),
    onTertiaryContainer = Color(0xFFEDF0FF),
    background = CG.Void,
    onBackground = CG.TextPrimary,
    surface = CG.Void,
    onSurface = CG.TextPrimary,
    surfaceVariant = Color(0xFF191B22),
    onSurfaceVariant = CG.TextMuted,
    surfaceTint = CG.CobaltGlow,
    inverseSurface = CG.TextPrimary,
    inverseOnSurface = CG.Void,
    outline = Color(0x338D90A0),
    outlineVariant = Color(0xFF434655),
    error = CG.Error,
    onError = CG.OnError,
    errorContainer = CG.ErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6)
)

// اسم قديم للاحتفاظ بالتوافق إن وُجد استدعاء خارجي.
private val ObsidianColorScheme = CobaltColorScheme

@Composable
fun CoveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CobaltColorScheme,
        typography = Typography,
        content = content
    )
}
