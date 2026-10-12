package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ============================================================================
// Cove — Cobalt Glass Design Tokens (S1)
// القيم كلها مستخرجة حرفيًا من الملفات المرجعية (Image_2_(3).html + الصورة
// + Image_3.markdown) ومسجّلة في DESIGN_SPEC.md / CONFLICTS.md.
// ----------------------------------------------------------------------------
// CG = المرجع الرسمي الجديد. الأسماء القديمة (OmniGetDesignTokens و Obsidian
// و AccentSkyBlue ...) أُبقيت كـ aliases تُشير إلى قيم Cobalt الجديدة حتى يرحَل
// كل الكود الموجود بصريًا دون كسر الاستدعاءات، ثم تُزال في S6.
// ============================================================================
object CG {
    // --- Surfaces (من HTML: midnight/void/surfaceCard/dock/search/track) ---
    val Void = Color(0xFF07090E)                 // body bg / base canvas (HTML 22,60)
    val Elevated = Color(0xFF0B0F19)             // void (HTML 23)
    val Card = Color(0xFF121622)                 // card base (HTML 128)
    val CardAlpha = Color(0xBF121622)            // rgba(18,22,34,0.75)
    val Dock = Color(0xE60D111A)                 // rgba(13,17,26,0.90) (HTML 215)
    val SearchBar = Color(0xCC10141F)            // rgba(16,20,31,0.80) (HTML 105)
    val SegTrack = Color(0xE60A0D15)             // rgba(10,13,21,0.90) (HTML 131)

    // --- Accent / Cobalt ---
    val Cobalt = Color(0xFF2563EB)               // blue-600 (HTML 25)
    val CobaltLight = Color(0xFF3B82F6)          // blue-500 (HTML 26)
    val CobaltGlow = Color(0xFF60A5FA)           // blue-400 (HTML 27)
    val Indigo = Color(0xFF818CF8)               // indigo-400 (HTML 96)
    val BloomMid = Color(0xFF1D4ED8)             // rgba(29,78,216) (HTML 45)

    // --- Text ---
    val TextPrimary = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFF94A3B8)            // slate-400 (HTML 28)
    val TextDim = Color(0xFF64748B)              // slate-500 (HTML 195)
    val Slate300 = Color(0xFFCBD5E1)             // slate-300 (HTML 84,149)

    // --- Borders / dividers (alpha white) ---
    val Border10 = Color(0x1AFFFFFF)             // white/10
    val Border09 = Color(0x17FFFFFF)             // white/9
    val Border12 = Color(0x1FFFFFFF)             // white/12
    val Border08 = Color(0x14FFFFFF)             // white/8
    val Divider05 = Color(0x0DFFFFFF)            // white/5

    // --- Badges ---
    val BadgeLockBg = Color(0x0DFFFFFF)          // white/5
    val BadgeCheckBg = Color(0x333B82F6)         // blue-500/20
    val BadgeCheckBorder = Color(0x4D3B82F6)     // blue-500/30
    val TabActiveBg = Color(0x332563EB)          // blue-600/20

    // --- Semantic (من Markdown YAML: error/error-container) ---
    val Error = Color(0xFFFFB4AB)
    val ErrorContainer = Color(0xFF93000A)
    val OnError = Color(0xFF690005)
    val Success = Color(0xFF60A5FA)

    // --- Corners (من HTML: rounded-[30px] للبطاقة، full للأزرار) ---
    val RadiusCard = 30.dp
    val RadiusXs = 6.dp
    val RadiusSm = 10.dp
    val RadiusMd = 14.dp
    val RadiusLg = 18.dp
    val RadiusXl = 22.dp
    val Radius2Xl = 28.dp
    val Radius3Xl = 36.dp

    // --- Spacing (8px base، من HTML px-5=20, p-5=20, space-y-4=16 ...) ---
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
// Compatibility aliases — نفس الأسماء القديمة بقيم Cobalt الجديدة.
// ============================================================================
object OmniGetDesignTokens {
    val Surface0 = CG.Void
    val Surface1 = CG.Card
    val Surface2 = CG.Elevated
    val Surface3 = Color(0xFF191B22)
    val Surface4 = Color(0xFF1D1F26)
    val SurfaceGlass = CG.CardAlpha

    val TextPrimary = CG.TextPrimary
    val TextSecondary = CG.TextMuted
    val TextMuted = CG.TextDim
    val TextQuaternary = Color(0x4D94A3B8)

    val Accent = CG.CobaltLight
    val AccentHover = CG.CobaltGlow
    val AccentPressed = CG.Cobalt
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

// Top-level legacy aliases (مستخدمة في الشاشات) — بقيم Cobalt.
val ObsidianSurface = CG.Void
val ObsidianSurfaceDim = CG.Void
val ObsidianSurfaceBright = CG.Card
val ObsidianSurfaceContainerLowest = CG.Void
val ObsidianSurfaceContainerLow = CG.Elevated
val ObsidianSurfaceContainer = CG.Card
val ObsidianSurfaceContainerHigh = CG.Card
val ObsidianSurfaceContainerHighest = Color(0xFF191B22)
val ObsidianOnSurface = CG.TextPrimary
val ObsidianOnSurfaceVariant = CG.TextMuted
val ObsidianInverseSurface = CG.TextPrimary
val ObsidianInverseOnSurface = CG.Void
val ObsidianOutline = Color(0x338D90A0)
val ObsidianOutlineVariant = Color(0xFF434655)
val ObsidianSurfaceTint = CG.CobaltGlow

val ObsidianPrimary = CG.CobaltLight
val ObsidianOnPrimary = Color(0xFF002A78)
val ObsidianPrimaryContainer = CG.Cobalt
val ObsidianOnPrimaryContainer = Color(0xFFEEEFFf)
val ObsidianInversePrimary = Color(0xFF0053DB)

val ObsidianSecondary = CG.CobaltGlow
val ObsidianOnSecondary = Color(0xFF00315D)
val ObsidianSecondaryContainer = Color(0xFF0267B8)
val ObsidianOnSecondaryContainer = Color(0xFFD6E5FF)

val ObsidianTertiary = Color(0xFFADC6FF)
val ObsidianOnTertiary = Color(0xFF002E6A)
val ObsidianTertiaryContainer = Color(0xFF0F69DC)
val ObsidianOnTertiaryContainer = Color(0xFFEDF0FF)

val ObsidianError = CG.Error
val ObsidianOnError = CG.OnError
val ObsidianErrorContainer = CG.ErrorContainer
val ObsidianOnErrorContainer = Color(0xFFFFDAD6)

val ObsidianBackground = CG.Void
val ObsidianOnBackground = CG.TextPrimary
val ObsidianSurfaceVariant = Color(0xFF33343C)

val OledBlack = CG.Void
val OledSurface = CG.Card
val AccentSkyBlue = CG.CobaltGlow
val SilverZinc = CG.TextMuted

val TextWhite = CG.TextPrimary
val TextGray = CG.TextMuted
val TextMuted = CG.TextDim
val SurfaceCard = CG.Card
val SuccessGreen = CG.Success
val ErrorRed = CG.Error
