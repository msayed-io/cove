package com.example.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============================================================================
// Cove — Cobalt Glass shared primitives (S2)
// ============================================================================

enum class GlassMaterial { SUBTLE, REGULAR, STRONG, ACCENT_TINTED }

/**
 * سطح زجاجي Cobalt: تعبئة شبه شفافة متدرجة + حدّ شعري متدرج + لمعان علوي.
 * القيم مطابقة لـ DESIGN_SPEC (الأسطح الزجاجية فوق خلفية ملساء).
 */
@Composable
fun Modifier.glass(
    material: GlassMaterial = GlassMaterial.REGULAR,
    shape: Shape = RoundedCornerShape(CG.RadiusMd)
): Modifier {
    val glassBrush = when (material) {
        GlassMaterial.SUBTLE -> Brush.verticalGradient(listOf(Color(0x40121622), Color(0x1F0B0F19)))
        GlassMaterial.REGULAR -> Brush.verticalGradient(listOf(Color(0xBF121622), Color(0x660B0F19)))
        GlassMaterial.STRONG -> Brush.verticalGradient(listOf(Color(0xE60D111A), Color(0xB30B0F19)))
        GlassMaterial.ACCENT_TINTED -> Brush.verticalGradient(listOf(Color(0x3D3B82F6), Color(0x141A2233)))
    }
    val borderBrush = when (material) {
        GlassMaterial.ACCENT_TINTED -> Brush.verticalGradient(listOf(Color(0x6660A5FA), Color(0x1F3B82F6)))
        else -> Brush.verticalGradient(listOf(Color(0x3DFFFFFF), Color(0x0AFFFFFF)))
    }
    return this
        .clip(shape)
        .background(glassBrush)
        .border(0.5.dp, borderBrush, shape)
        .drawWithContent {
            drawContent()
            // Specular upper edge highlight (light catching the top bevel)
            val sw = 1.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = androidx.compose.ui.geometry.Offset(0f, sw / 2),
                end = androidx.compose.ui.geometry.Offset(size.width, sw / 2),
                strokeWidth = sw
            )
        }
}

/**
 * ظل/توهج ملوّن مخصص (بديل عن elevation الرمادي) مطابق لقيم CSS في DESIGN_SPEC.
 * يُرسم خلف المحتوى كطبقات متدرجة تحاكي الـ blur دون قصّ.
 */
fun Modifier.cobaltShadow(
    color: Color = Color(0x732563EB), // rgba(37,99,235,0.45)
    offsetY: Float = 12f,
    blur: Float = 30f
): Modifier = this.drawBehind {
    val layers = 4
    for (i in layers downTo 1) {
        val t = i.toFloat() / layers
        val pad = -blur * t * 0.5f
        drawRoundRect(
            color = color.copy(alpha = color.alpha * 0.22f * t),
            topLeft = androidx.compose.ui.geometry.Offset(pad, offsetY * t + pad),
            size = androidx.compose.ui.geometry.Size(size.width - pad * 2, size.height - pad * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(this.size.height / 2)
        )
    }
}

/** أثر ضغط iOS: scale 0.97 بمنحنى cubic-bezier(0.4,0,0.2,1). */
@Composable
fun Modifier.pressClickEffect(onClick: () -> Unit): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "PressScale"
    )
    return this
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .pointerInput(onClick) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    try { tryAwaitRelease() } finally { isPressed = false }
                },
                onTap = { onClick() }
            )
        }
}

@Composable
fun DoubleBezelCard(
    modifier: Modifier = Modifier,
    outerColor: Color = Color(0x0AFFFFFF),
    innerColor: Color = Color.White.copy(alpha = 0.05f),
    outerBorderColor: Color = Color(0x1AFFFFFF),
    innerBorderColor: Color = Color(0x12FFFFFF),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val outerShape = RoundedCornerShape(CG.RadiusLg)
    val innerShape = RoundedCornerShape(CG.RadiusSm)
    val outerModifier = if (onClick != null) modifier.clip(outerShape).pressClickEffect(onClick)
    else modifier.clip(outerShape)
    Box(
        modifier = outerModifier.glass(GlassMaterial.SUBTLE, outerShape).padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(innerShape)
                .background(innerColor)
                .border(0.5.dp, innerBorderColor, innerShape)
                .padding(16.dp)
        ) { content() }
    }
}

@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = CG.CobaltLight,
    contentColor: Color = Color.White,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(if (enabled) containerColor else Color.White.copy(alpha = 0.1f))
            .then(if (enabled) Modifier.pressClickEffect(onClick) else Modifier)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                color = if (enabled) contentColor else CG.TextMuted.copy(alpha = 0.5f)
            )
            if (icon != null && enabled) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun PremiumBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = CG.Cobalt.copy(alpha = 0.15f),
    contentColor: Color = CG.CobaltGlow
) {
    Box(
        modifier = modifier.clip(CircleShape).background(containerColor).padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        )
    }
}
