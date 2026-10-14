package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ============================================================================
// Cove — Stealth Silver & Refined Glass Design Tokens (Stealth Luxury)
// الفلسفة: أسود نقي تام OLED Black #000000 + فحم هادئ + فضة بلاتينية ناصعة
// ============================================================================
object CG {
    // --- Surfaces (Matte Stepped Charcoal & True Black) ---
    val Void = Color(0xFF000000)                 // Canvas OLED black #000000
    val Elevated = Color(0xFF0B0C0E)             // Matte Charcoal Surface 1
    val Card = Color(0xFF111215)                 // Card Base Surface 2
    val CardAlpha = Color(0xD9111215)            // rgba(17,18,21,0.85)
    val Dock = Color(0xE60C0D0F)                 // Dock background rgba(12,13,15,0.90)
    val SearchBar = Color(0xD90C0D0F)            // Search bar rgba(12,13,15,0.85)
    val SegTrack = Color(0xE6090A0C)             // Segmented track

    // --- Accent / Platinum & Aliases ---
    val Cobalt = Color(0xFFF1F5F9)               // Platinum
    val CobaltLight = Color(0xFFFFFFFF)          // Stark White
    val CobaltGlow = Color(0xFF94A3B8)           // Muted Silver
    val Indigo = Color(0xFFCBD5E1)               // Titanium
    val BloomMid = Color(0xFF334155)

    val Platinum = Color(0xFFF1F5F9)
    val SilverPrimary = Color(0xFFFFFFFF)
    val SilverAccent = Color(0xFFCBD5E1)
    val SilverGlow = Color(0xFF94A3B8)

    // --- Text ---
    val TextPrimary = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFF94A3B8)            // Slate 400
    val TextDim = Color(0xFF64748B)              // Slate 500
    val Slate300 = Color(0xFFCBD5E1)

    // --- Borders / Dividers (Platinum Hairlines) ---
    val Border10 = Color(0x1AFFFFFF)             // white/10
    val Border09 = Color(0x17FFFFFF)
    val Border12 = Color(0x1FFFFFFF)
    val Border08 = Color(0x14FFFFFF)
    val Divider05 = Color(0x0DFFFFFF)

    // --- Badges ---
    val BadgeLockBg = Color(0x0DFFFFFF)
    val BadgeCheckBg = Color(0x26F1F5F9)         // Platinum 15%
    val BadgeCheckBorder = Color(0x4DF1F5F9)     // Platinum 30%
    val TabActiveBg = Color(0x26FFFFFF)          // White 15%

    // --- Semantic ---
    val Error = Color(0xFFFFB4AB)
    val ErrorContainer = Color(0xFF93000A)
    val OnError = Color(0xFF690005)
    val Success = Color(0xFF34D399)              // Emerald 400

    // --- Corners ---
    val RadiusCard = 30.dp
    val RadiusXs = 6.dp
    val RadiusSm = 10.dp
    val RadiusMd = 14.dp
    val RadiusLg = 18.dp
    val RadiusXl = 22.dp
    val Radius2Xl = 28.dp
    val Radius3Xl = 36.dp

    // --- Spacing ---
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

// ============================================================================
// Compatibility aliases — Stealth Silver tokens
// ============================================================================
object OmniGetDesignTokens {
    val Surface0 = CG.Void
    val Surface1 = CG.Card
    val Surface2 = CG.Elevated
    val Surface3 = Color(0xFF16171A)
    val Surface4 = Color(0xFF1C1D21)
    val SurfaceGlass = CG.CardAlpha

    val TextPrimary = CG.TextPrimary
    val TextSecondary = CG.TextMuted
    val TextMuted = CG.TextDim
    val TextQuaternary = Color(0x4D94A3B8)

    val Accent = CG.Platinum
    val AccentHover = CG.SilverAccent
    val AccentPressed = CG.SilverPrimary
    val AccentTint = CG.TabActiveBg

    val SuccessText = CG.Success
    val SuccessBg = CG.BadgeCheckBg
    val DangerText = CG.Error
    val DangerBg = CG.ErrorContainer

    val RadiusXs = CG.RadiusXs
    val RadiusSm = CG.RadiusSm
    val RadiusMd = CG.RadiusMd
    val RadiusLg = CG.RadiusLg
    val RadiusXl = CG.RadiusXl
    val Radius2Xl = CG.Radius2Xl
    val Radius3Xl = CG.Radius3Xl

    val Space4 = CG.Space4
    val Space8 = CG.Space8
    val Space12 = CG.Space12
    val Space16 = CG.Space16
    val Space20 = CG.Space20
    val Space24 = CG.Space24
    val Space32 = CG.Space32
    val Space40 = CG.Space40
    val Space48 = CG.Space48
}

// Top-level legacy aliases — Stealth Silver
val ObsidianSurface = CG.Void
val ObsidianSurfaceDim = CG.Void
val ObsidianSurfaceBright = CG.Card
val ObsidianSurfaceContainerLowest = CG.Void
val ObsidianSurfaceContainerLow = CG.Elevated
val ObsidianSurfaceContainer = CG.Card
val ObsidianSurfaceContainerHigh = CG.Card
val ObsidianSurfaceContainerHighest = Color(0xFF16171A)
val ObsidianOnSurface = CG.TextPrimary
val ObsidianOnSurfaceVariant = CG.TextMuted
val ObsidianInverseSurface = CG.TextPrimary
val ObsidianInverseOnSurface = CG.Void
val ObsidianOutline = Color(0x338D90A0)
val ObsidianOutlineVariant = Color(0xFF434655)
val ObsidianSurfaceTint = CG.Platinum

val ObsidianPrimary = CG.Platinum
val ObsidianOnPrimary = Color(0xFF000000)
val ObsidianPrimaryContainer = Color(0xFF1E2026)
val ObsidianOnPrimaryContainer = Color(0xFFFFFFFF)
val ObsidianInversePrimary = Color(0xFF000000)

val ObsidianSecondary = CG.SilverAccent
val ObsidianOnSecondary = Color(0xFF000000)
val ObsidianSecondaryContainer = Color(0xFF262830)
val ObsidianOnSecondaryContainer = Color(0xFFF1F5F9)

val ObsidianTertiary = Color(0xFFCBD5E1)
val ObsidianOnTertiary = Color(0xFF000000)
val ObsidianTertiaryContainer = Color(0xFF1E2026)
val ObsidianOnTertiaryContainer = Color(0xFFEDF0FF)

val ObsidianError = CG.Error
val ObsidianOnError = CG.OnError
val ObsidianErrorContainer = CG.ErrorContainer
val ObsidianOnErrorContainer = Color(0xFFFFDAD6)

val ObsidianBackground = CG.Void
val ObsidianOnBackground = CG.TextPrimary
val ObsidianSurfaceVariant = Color(0xFF16171A)

val OledBlack = CG.Void
val OledSurface = CG.Card
val AccentSkyBlue = CG.Platinum
val SilverZinc = CG.TextMuted

val TextWhite = CG.TextPrimary
val TextGray = CG.TextMuted
val TextMuted = CG.TextDim
val SurfaceCard = CG.Card
val SuccessGreen = CG.Success
val ErrorRed = CG.Error
