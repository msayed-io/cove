package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ObsidianColorScheme = darkColorScheme(
    primary = OmniGetDesignTokens.Accent,
    onPrimary = Color(0xFF000000), // Clean black text for systemTeal action buttons
    primaryContainer = OmniGetDesignTokens.AccentTint,
    onPrimaryContainer = OmniGetDesignTokens.Accent,
    inversePrimary = OmniGetDesignTokens.Accent,
    secondary = OmniGetDesignTokens.TextSecondary,
    onSecondary = OmniGetDesignTokens.Surface0,
    secondaryContainer = OmniGetDesignTokens.Surface1,
    onSecondaryContainer = OmniGetDesignTokens.TextSecondary,
    tertiary = OmniGetDesignTokens.TextMuted,
    onTertiary = OmniGetDesignTokens.Surface0,
    tertiaryContainer = OmniGetDesignTokens.Surface2,
    onTertiaryContainer = OmniGetDesignTokens.TextSecondary,
    background = OmniGetDesignTokens.Surface0, // #000000 OLED pure background
    onBackground = OmniGetDesignTokens.TextPrimary, // rgba(255,255,255,0.95)
    surface = OmniGetDesignTokens.Surface1, // #1C1C1E cards
    onSurface = OmniGetDesignTokens.TextPrimary,
    surfaceVariant = OmniGetDesignTokens.Surface2, // #2C2C2E elevated
    onSurfaceVariant = OmniGetDesignTokens.TextSecondary, // rgba(235,235,245,0.60)
    inverseSurface = OmniGetDesignTokens.TextPrimary,
    inverseOnSurface = OmniGetDesignTokens.Surface0,
    outline = Color(0x99545458), // .separator (Apple) — 60% opacity gray
    outlineVariant = Color(0xFF48484A), // .opaqueSeparator
    error = OmniGetDesignTokens.DangerText, // #FF453A
    onError = Color(0xFF000000),
    errorContainer = OmniGetDesignTokens.DangerBg, // 18% opacity error
    onErrorContainer = OmniGetDesignTokens.DangerText
)

@Composable
fun CoveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ObsidianColorScheme,
        typography = Typography,
        content = content
    )
}
