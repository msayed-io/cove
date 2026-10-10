package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ============================================================================
// Cove — Liquid Glass (Obsidian Aurora) Design Tokens
// ============================================================================
object OmniGetDesignTokens {
    // 1. Backgrounds & Surfaces (Apple canonical dark scale)
    val Surface0 = Color(0xFF000000)                // OLED pure — .systemBackground (dark)
    val Surface1 = Color(0xFF1C1C1E)                // .secondarySystemBackground — cards
    val Surface2 = Color(0xFF2C2C2E)                // .tertiarySystemBackground — elevated
    val Surface3 = Color(0xFF3A3A3C)                // system gray 3 — popovers
    val Surface4 = Color(0xFF48484A)                // system gray 4 — hairlines
    val SurfaceGlass = Color(28, 28, 30).copy(alpha = 0.55f) // regular glass tint: rgba(28,28,30,0.55)

    // 2. Texts (Apple label scale in dark mode)
    val TextPrimary = Color(0xF2FFFFFF)             // .label (primary, 95% opacity white)
    val TextSecondary = Color(0x99EBEBF5)           // .secondaryLabel (60% opacity)
    val TextMuted = Color(0x4DEBEBF5)               // .tertiaryLabel (30% opacity)
    val TextQuaternary = Color(0x2EEBEBF5)          // .quaternaryLabel (18% opacity)

    // 3. Accent (SINGLE ACCENT: Apple systemTeal dark)
    val Accent = Color(0xFF64D2FF)                  // Apple systemTeal
    val AccentHover = Color(0xFF4FC1E8)
    val AccentPressed = Color(0xFF3FB0D6)
    val AccentTint = Color(0x2E64D2FF)              // 18% opacity accent (accent-soft)

    // 4. Semantic (Muted Earth Tones, Apple system colors)
    val SuccessText = Color(0xFF30D158)             // Apple systemGreen (dark)
    val SuccessBg = Color(0x2E30D158)               // 18% opacity success (success-soft)
    val DangerText = Color(0xFFFF453A)              // Apple systemRed (dark)
    val DangerBg = Color(0x2EFF453A)                // 18% opacity error (error-soft)

    // 5. Corners (Corner Radius Scale)
    val RadiusXs = 6.dp                             // chips داخلية، badges صغيرة
    val RadiusSm = 10.dp                            // small buttons، segmented control
    val RadiusMd = 14.dp                            // cards، inputs، list items (DEFAULT)
    val RadiusLg = 18.dp                            // large cards، hero items
    val RadiusXl = 22.dp                            // sheets، panels
    val Radius2Xl = 28.dp                           // modals، nav bars
    val Radius3Xl = 36.dp                           // hero featured cards

    // 6. Spacing Scale (8px base)
    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space20 = 20.dp
    val Space24 = 24.dp
    val Space32 = 32.dp
    val Space40 = 40.dp
    val Space48 = 48.dp
}

// Legacy/Compatibility Aliases mapped directly to Cove Design Tokens
val ObsidianSurface = OmniGetDesignTokens.Surface0
val ObsidianSurfaceDim = OmniGetDesignTokens.Surface0
val ObsidianSurfaceBright = OmniGetDesignTokens.Surface1
val ObsidianSurfaceContainerLowest = OmniGetDesignTokens.Surface0
val ObsidianSurfaceContainerLow = OmniGetDesignTokens.Surface0
val ObsidianSurfaceContainer = OmniGetDesignTokens.Surface1
val ObsidianSurfaceContainerHigh = OmniGetDesignTokens.Surface1
val ObsidianSurfaceContainerHighest = OmniGetDesignTokens.Surface2
val ObsidianOnSurface = OmniGetDesignTokens.TextPrimary
val ObsidianOnSurfaceVariant = OmniGetDesignTokens.TextSecondary
val ObsidianInverseSurface = OmniGetDesignTokens.TextPrimary
val ObsidianInverseOnSurface = OmniGetDesignTokens.Surface0
val ObsidianOutline = Color(0x99545458)             // .separator (Apple) — 60% opacity gray
val ObsidianOutlineVariant = Color(0xFF48484A)      // .opaqueSeparator
val ObsidianSurfaceTint = OmniGetDesignTokens.Accent

val ObsidianPrimary = OmniGetDesignTokens.Accent
val ObsidianOnPrimary = Color(0xFF000000)           // black text on light accent
val ObsidianPrimaryContainer = OmniGetDesignTokens.AccentTint
val ObsidianOnPrimaryContainer = OmniGetDesignTokens.Accent
val ObsidianInversePrimary = OmniGetDesignTokens.Accent

val ObsidianSecondary = OmniGetDesignTokens.TextSecondary
val ObsidianOnSecondary = OmniGetDesignTokens.Surface0
val ObsidianSecondaryContainer = OmniGetDesignTokens.Surface1
val ObsidianOnSecondaryContainer = OmniGetDesignTokens.TextSecondary

val ObsidianTertiary = OmniGetDesignTokens.TextMuted
val ObsidianOnTertiary = OmniGetDesignTokens.Surface0
val ObsidianTertiaryContainer = OmniGetDesignTokens.Surface2
val ObsidianOnTertiaryContainer = OmniGetDesignTokens.TextSecondary

val ObsidianError = OmniGetDesignTokens.DangerText
val ObsidianOnError = Color(0xFF000000)
val ObsidianErrorContainer = OmniGetDesignTokens.DangerBg
val ObsidianOnErrorContainer = OmniGetDesignTokens.DangerText

val ObsidianBackground = OmniGetDesignTokens.Surface0
val ObsidianOnBackground = OmniGetDesignTokens.TextPrimary
val ObsidianSurfaceVariant = OmniGetDesignTokens.Surface2

val OledBlack = OmniGetDesignTokens.Surface0
val OledSurface = OmniGetDesignTokens.Surface1
val AccentSkyBlue = OmniGetDesignTokens.Accent
val SilverZinc = OmniGetDesignTokens.TextSecondary

val TextWhite = OmniGetDesignTokens.TextPrimary
val TextGray = OmniGetDesignTokens.TextSecondary
val TextMuted = OmniGetDesignTokens.TextMuted
val SurfaceCard = OmniGetDesignTokens.Surface1
val SuccessGreen = OmniGetDesignTokens.SuccessText
val ErrorRed = OmniGetDesignTokens.DangerText
