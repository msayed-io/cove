package com.example.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawWithContent

// ============================================================================
// Cove — Liquid Glass Material Spec
// ============================================================================
enum class GlassMaterial {
    SUBTLE, REGULAR, STRONG, ACCENT_TINTED
}

@Composable
fun Modifier.glass(
    material: GlassMaterial = GlassMaterial.REGULAR,
    shape: Shape = RoundedCornerShape(OmniGetDesignTokens.RadiusMd) // default 14.dp
): Modifier {
    val glassBrush = when (material) {
        GlassMaterial.SUBTLE -> Brush.verticalGradient(
            colors = listOf(
                Color(0x40252529), // rgba(37,37,41,0.25)
                Color(0x1F0D0D0F)  // rgba(13,13,15,0.12)
            )
        )
        GlassMaterial.REGULAR -> Brush.verticalGradient(
            colors = listOf(
                Color(0x73252529), // rgba(37,37,41,0.45)
                Color(0x380D0D0F)  // rgba(13,13,15,0.22)
            )
        )
        GlassMaterial.STRONG -> Brush.verticalGradient(
            colors = listOf(
                Color(0x991E1E22), // rgba(30,30,34,0.60)
                Color(0x660B0B0D)  // rgba(11,11,13,0.40)
            )
        )
        GlassMaterial.ACCENT_TINTED -> Brush.verticalGradient(
            colors = listOf(
                Color(0x3D64D2FF), // rgba(100,210,255,0.24)
                Color(0x141A2233)  // rgba(26,34,51,0.08)
            )
        )
    }

    val borderBrush = when (material) {
        GlassMaterial.ACCENT_TINTED -> Brush.verticalGradient(
            colors = listOf(
                Color(0x6664D2FF), // 40% tint on top
                Color(0x1F64D2FF)  // 12% tint on bottom
            )
        )
        else -> Brush.verticalGradient(
            colors = listOf(
                Color(0x3DFFFFFF), // 24% white on top (catching light)
                Color(0x0AFFFFFF)  // 4% white on bottom (shadow side)
            )
        )
    }

    return this
        .clip(shape)
        .background(glassBrush)
        .border(0.5.dp, borderBrush, shape)
        .drawWithContent {
            drawContent()
            
            val width = size.width
            val height = size.height

            // 1. High-fidelity diagonal satin sheen reflection (polished glass shine)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.015f),
                        Color.Transparent
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(width, height)
                )
            )

            // 2. Stable high-performance Frosted Grain Noise (sandblasted feel)
            // Deterministic seeding to keep rendering fast and flicker-free
            val random = java.util.Random(1337L)
            val numDots = 150
            for (i in 0 until numDots) {
                val rx = random.nextFloat() * width
                val ry = random.nextFloat() * height
                val rsize = 0.8f + random.nextFloat() * 1.2f
                val ralpha = 0.02f + random.nextFloat() * 0.05f
                drawCircle(
                    color = Color.White.copy(alpha = ralpha),
                    radius = rsize,
                    center = androidx.compose.ui.geometry.Offset(rx, ry)
                )
            }

            // 3. Specular upper edge highlight (representing light catching the top bevel)
            val strokeWidth = 1.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = 0.18f),
                start = androidx.compose.ui.geometry.Offset(0f, strokeWidth / 2),
                end = androidx.compose.ui.geometry.Offset(width, strokeWidth / 2),
                strokeWidth = strokeWidth
            )
        }
}

@Composable
fun Modifier.pressClickEffect(onClick: () -> Unit): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "PressScale"
    )
    
    return this
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .pointerInput(onClick) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    try {
                        tryAwaitRelease()
                    } finally {
                        isPressed = false
                    }
                },
                onTap = { onClick() }
            )
        }
}

@Composable
fun DoubleBezelCard(
    modifier: Modifier = Modifier,
    outerColor: Color = Color(0x0AFFFFFF), // fallback color, overridden by .glass
    innerColor: Color = Color.White.copy(alpha = 0.05f),
    outerBorderColor: Color = Color(0x1AFFFFFF), // chromatic edge (Apple)
    innerBorderColor: Color = Color(0x12FFFFFF),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    // Concentric formula: inner_radius = outer_radius - padding
    // 18.dp (RadiusLg) - 8.dp (padding) = 10.dp (RadiusSm)
    val outerShape = RoundedCornerShape(OmniGetDesignTokens.RadiusLg)
    val innerShape = RoundedCornerShape(OmniGetDesignTokens.RadiusSm)

    val outerModifier = if (onClick != null) {
        modifier
            .clip(outerShape)
            .pressClickEffect(onClick)
    } else {
        modifier.clip(outerShape)
    }

    Box(
        modifier = outerModifier
            .glass(GlassMaterial.SUBTLE, outerShape)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(innerShape)
                .background(innerColor)
                .border(0.5.dp, innerBorderColor, innerShape)
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = OmniGetDesignTokens.Accent,
    contentColor: Color = Color(0xFF000000), // Clean black text on light systemTeal accent
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = if (enabled) contentColor else OmniGetDesignTokens.TextSecondary.copy(alpha = 0.5f)
            )
            
            if (icon != null && enabled) {
                Spacer(modifier = Modifier.width(10.dp))
                // Button-in-Button Trailing Icon: circular wrapper
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = OmniGetDesignTokens.Accent.copy(alpha = 0.15f),
    contentColor: Color = OmniGetDesignTokens.Accent
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        )
    }
}
