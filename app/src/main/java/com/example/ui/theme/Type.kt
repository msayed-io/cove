package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// ============================================================================
// Cove — Cobalt Glass Typography (S1)
// الخطوط مضمّنة (Bundled) بكل الأوزان الحقيقية:
//   العربية: IBM Plex Sans Arabic بأوزان 300/400/500/600/700
//             (المرجع يطلب 800 للعناوين لكنه غير متوفر上游؛ الأقصى الحقيقي 700 —
//             مسجّل في CONFLICTS.md، فيُستخدم 700 كأثقل وزن عربي)
//   اللاتينية (العلامة): Plus Jakarta Sans بأوزان 500/600/700/800
// ============================================================================
val CoveArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_300, FontWeight.Light),
    Font(R.font.ibm_plex_sans_arabic_400, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_500, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_600, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_700, FontWeight.Bold),
)

val CoveBrand = FontFamily(
    Font(R.font.plus_jakarta_sans_500, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_600, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_700, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_800, FontWeight.ExtraBold),
)

/** أثقل وزن عربي حقيقي متاح (المرجع 800 غير موجود上游 → 700). */
val ArabicHeaviest = FontWeight.Bold

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = CoveArabic, fontWeight = ArabicHeaviest,
        fontSize = 30.sp, lineHeight = 37.5.sp, letterSpacing = (-0.025).sp
    ),
    displayMedium = TextStyle(
        fontFamily = CoveArabic, fontWeight = ArabicHeaviest,
        fontSize = 28.sp, lineHeight = 35.sp, letterSpacing = (-0.02).sp
    ),
    displaySmall = TextStyle(
        fontFamily = CoveArabic, fontWeight = ArabicHeaviest,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.01).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = CoveArabic, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.015).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = CoveArabic, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.01).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = CoveArabic, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.01).sp
    ),
    titleLarge = TextStyle(
        fontFamily = CoveArabic, fontWeight = FontWeight.Normal,
        fontSize = 18.sp, lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = CoveArabic, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = CoveArabic, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = CoveArabic, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = CoveArabic, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = CoveArabic, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = CoveArabic, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.sp
    ),
    labelMedium = TextStyle(
        fontFamily = CoveArabic, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.04.sp
    ),
    labelSmall = TextStyle(
        fontFamily = CoveArabic, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.04.sp
    )
)
