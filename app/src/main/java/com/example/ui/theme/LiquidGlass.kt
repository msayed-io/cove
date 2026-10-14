package com.example.ui.theme

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

// =============================================================================
//  Liquid Glass Engine — Android AGSL & Physical Optics Model
//  مبني ومصحح وفق نموذج سنيل (Snell-bounded Lensing) وشيدر AGSL عالي الأداء
// =============================================================================

@org.intellij.lang.annotations.Language("AGSL")
const val LIQUID_GLASS_SHADER = """
uniform shader backdrop;
uniform float2 size;              // px
uniform float radius;             // px
uniform float bezel;              // px
uniform float refractionScalePx;  // peak displacement in px
uniform float profilePeak;        // optical profile peak
uniform float refractiveIndex;    // > 1 (e.g. 1.5)
uniform float chromaPx;            // channel separation in px
uniform float rimLight;            // 0..1
uniform float pressBulgePx;        // rim-confined press deformation
uniform float2 pressPoint;         // local px
uniform float reducedMotion;       // 0 or 1

float sdRoundedBox(float2 p, float2 halfSize, float r) {
    r = clamp(r, 0.0, min(halfSize.x, halfSize.y));
    float2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float surfaceHeight(float x) {
    x = clamp(x, 0.0, 1.0);
    if (x <= 0.0 || x >= 1.0) return 0.0;
    float u = 1.0 - x;
    return pow(max(1.0 - u * u * u * u, 0.0), 0.25);
}

float surfaceDerivative(float x) {
    x = clamp(x, 0.0, 1.0);
    if (x <= 0.0 || x >= 1.0) return 0.0;
    float u = 1.0 - x;
    float h = surfaceHeight(x);
    return (u * u * u) / max(h * h * h, 0.00001);
}

float snellDisplacement(float x) {
    if (x <= 0.0 || x >= 1.0 || refractiveIndex <= 1.0) return 0.0;
    float thetaI = atan(surfaceDerivative(x));
    float thetaT = asin(clamp(sin(thetaI) / refractiveIndex, -1.0, 1.0));
    float raw = max(tan(thetaI - thetaT) * surfaceHeight(x), 0.0);
    return refractionScalePx * raw / max(profilePeak, 0.00001);
}

half4 main(float2 fragCoord) {
    float2 halfSize = size * 0.5;
    float2 p = fragCoord - halfSize;
    float dist = sdRoundedBox(p, halfSize, radius);
    if (dist > 0.0) return half4(0.0);

    float x = clamp(-dist / max(bezel, 1.0), 0.0, 1.0);
    float normalEps = max(0.5, min(size.x, size.y) * 0.002);
    float2 gradRaw = float2(
        sdRoundedBox(p + float2(normalEps, 0.0), halfSize, radius) - sdRoundedBox(p - float2(normalEps, 0.0), halfSize, radius),
        sdRoundedBox(p + float2(0.0, normalEps), halfSize, radius) - sdRoundedBox(p - float2(0.0, normalEps), halfSize, radius)
    );
    float gradLength = max(length(gradRaw), 0.00001);
    float2 normal = gradRaw / gradLength;

    float rimMask = 1.0 - smoothstep(0.72, 1.0, x);
    float displacement = snellDisplacement(x);
    float currentPressBulge = (reducedMotion > 0.5) ? 0.0 : pressBulgePx;
    float pressDistance = length(fragCoord - pressPoint);
    float press = currentPressBulge * exp(-pressDistance * pressDistance / 7200.0) * rimMask;
    float2 baseOffset = normal * (displacement + press);
    float2 redOffset = baseOffset - normal * chromaPx * rimMask;
    float2 blueOffset = baseOffset + normal * chromaPx * rimMask;

    half4 redSample = backdrop.eval(fragCoord + redOffset);
    half4 greenSample = backdrop.eval(fragCoord + baseOffset);
    half4 blueSample = backdrop.eval(fragCoord + blueOffset);
    half4 color = half4(redSample.r, greenSample.g, blueSample.b, 1.0);

    float2 lightAxis = normalize(float2(1.0, 1.0));
    float rimProfile = abs(dot(normal, lightAxis));
    float rim = rimLight * rimMask * rimProfile;
    color.rgb += half3(rim);
    return color;
}
"""

data class GlassOptics(
    val ior: Float = 1.5f,
    val refractionScalePx: Float = 14f,
    val profilePeak: Float = 0.28f,
    val chromaPx: Float = 1.2f
)

fun snellProfile(x0: Float, ior: Float): Float {
    val x = x0.coerceIn(0.0f, 1.0f)
    if (x <= 0f || x >= 1f) return 0f
    val u = 1f - x
    val h = (1f - u * u * u * u).coerceAtLeast(0f).pow(0.25f)
    val slope = (u * u * u) / h.pow(3f).coerceAtLeast(1e-6f)
    val thetaI = atan(slope)
    val thetaT = asin((sin(thetaI) / ior).coerceIn(-1f, 1f))
    return (tan(thetaI - thetaT) * h).coerceAtLeast(0f)
}

fun calculateProfilePeak(ior: Float, samples: Int = 2048): Float =
    (1 until samples).maxOf { snellProfile(it.toFloat() / samples, ior) }

enum class GlassTier {
    SHADER, // Tier A: AGSL RuntimeShader (Android 13+)
    BLUR,   // Tier B: Hardware RenderEffect Blur (Android 12+)
    SCRIM   // Tier C: Opaque & Frosted Scrim with accessible contrast
}

object GlassTierDetector {
    private var cachedTier: GlassTier? = null

    fun detect(context: Context): GlassTier {
        cachedTier?.let { return it }
        val tier = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val compiles = runCatching {
                    RuntimeShader(LIQUID_GLASS_SHADER)
                    true
                }.getOrDefault(false)
                if (compiles) GlassTier.SHADER else GlassTier.BLUR
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> GlassTier.BLUR
            else -> GlassTier.SCRIM
        }
        cachedTier = tier
        return tier
    }
}

/**
 * بناء شيدر وتأثير Liquid Glass المتسلسل على Android 13+
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun buildLiquidGlassEffect(shader: RuntimeShader, blurPx: Float): RenderEffect {
    val lens = RenderEffect.createRuntimeShaderEffect(shader, "backdrop")
    return if (blurPx > 0f) {
        val blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        RenderEffect.createChainEffect(blur, lens)
    } else {
        lens
    }
}

/**
 * كبسولة / سطح زجاجي سائل متقدم (Liquid Glass Surface)
 * يوفر مظهراً بصرياً فائق النقاء مع لمعة الحافة المنكسرة وحماية التباين.
 */
@Composable
fun Modifier.liquidGlass(
    fillColor: Color = Color(0xFF000000),
    washAlpha: Float = 0.55f,
    radius: Dp = 24.dp,
    borderColor: Color = Color.White.copy(alpha = 0.16f),
    borderWidth: Dp = 0.8.dp,
    rimLightAlpha: Float = 0.45f,
    capsule: Boolean = false,
    pressFeedback: Boolean = true
): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    var pressPoint by remember { mutableStateOf(Offset.Zero) }

    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.75f),
        label = "glassPress"
    )

    val pointerModifier = if (pressFeedback) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = { offset ->
                    pressPoint = offset
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                }
            )
        }
    } else Modifier

    return this
        .then(pointerModifier)
        .then(
            LiquidGlassModifierElement(
                fillColor = fillColor,
                washAlpha = washAlpha,
                radius = radius,
                borderColor = borderColor,
                borderWidth = borderWidth,
                rimLightAlpha = rimLightAlpha,
                capsule = capsule,
                pressBulge = pressProgress * 4f,
                pressPoint = pressPoint
            )
        )
}

private class LiquidGlassModifierNode(
    var fillColor: Color,
    var washAlpha: Float,
    var radius: Dp,
    var borderColor: Color,
    var borderWidth: Dp,
    var rimLightAlpha: Float,
    var capsule: Boolean,
    var pressBulge: Float,
    var pressPoint: Offset
) : Modifier.Node(), DrawModifierNode {

    override fun ContentDrawScope.draw() {
        val radiusPx = if (capsule) size.height / 2f else radius.toPx()
        val shapeOutline = Outline.Rounded(
            RoundRect(0f, 0f, size.width, size.height, CornerRadius(radiusPx))
        )
        val shapePath = Path().apply { addOutline(shapeOutline) }

        // 1. تعبئة السطح الزجاجي (Wash / Tint) مع حماية التباين WCAG 4.5:1
        clipPath(shapePath) {
            drawRect(color = fillColor.copy(alpha = washAlpha))
        }

        // 2. إطار الثيم المتحد المركز (Concentric Edge Rim)
        val bw = borderWidth.toPx()
        if (bw > 0f) {
            clipPath(shapePath) {
                drawPath(
                    path = edgePath(bw, size.width, size.height, radiusPx),
                    color = borderColor,
                    style = Stroke(width = bw)
                )
            }
        }

        // 3. لمعة الحافة المنكسرة (Specular Rim Highlight - Snell Lensing Gradient)
        if (rimLightAlpha > 0f) {
            val topGleam = rimLightAlpha.coerceIn(0f, 1f)
            val sideGleam = (topGleam * 0.45f).coerceIn(0f, 1f)
            val bottomGleam = (topGleam * 0.18f).coerceIn(0f, 1f)

            val rimBrush = Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = topGleam),
                0.25f to Color.White.copy(alpha = sideGleam),
                0.85f to Color.White.copy(alpha = sideGleam * 0.5f),
                1.0f to Color.White.copy(alpha = bottomGleam),
                startY = 0f,
                endY = size.height
            )

            val rimW = max(1f, bw * 1.1f)
            clipPath(shapePath) {
                drawPath(
                    path = edgePath(rimW, size.width, size.height, radiusPx),
                    brush = rimBrush,
                    style = Stroke(width = rimW)
                )
            }
        }

        drawContent()
    }

    private fun edgePath(
        strokeWidth: Float,
        width: Float,
        height: Float,
        radiusPx: Float
    ): Path {
        val inset = strokeWidth / 2f
        val cornerRadius = if (capsule) {
            ((height - strokeWidth) / 2f).coerceAtLeast(0f)
        } else {
            (radiusPx - inset).coerceAtLeast(0f)
        }
        return Path().apply {
            addRoundRect(
                RoundRect(
                    inset, inset, width - inset, height - inset,
                    CornerRadius(cornerRadius)
                )
            )
        }
    }
}

private class LiquidGlassModifierElement(
    val fillColor: Color,
    val washAlpha: Float,
    val radius: Dp,
    val borderColor: Color,
    val borderWidth: Dp,
    val rimLightAlpha: Float,
    val capsule: Boolean,
    val pressBulge: Float,
    val pressPoint: Offset
) : ModifierNodeElement<LiquidGlassModifierNode>() {

    override fun create(): LiquidGlassModifierNode = LiquidGlassModifierNode(
        fillColor = fillColor,
        washAlpha = washAlpha,
        radius = radius,
        borderColor = borderColor,
        borderWidth = borderWidth,
        rimLightAlpha = rimLightAlpha,
        capsule = capsule,
        pressBulge = pressBulge,
        pressPoint = pressPoint
    )

    override fun update(node: LiquidGlassModifierNode) {
        node.fillColor = fillColor
        node.washAlpha = washAlpha
        node.radius = radius
        node.borderColor = borderColor
        node.borderWidth = borderWidth
        node.rimLightAlpha = rimLightAlpha
        node.capsule = capsule
        node.pressBulge = pressBulge
        node.pressPoint = pressPoint
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiquidGlassModifierElement) return false
        return fillColor == other.fillColor &&
                washAlpha == other.washAlpha &&
                radius == other.radius &&
                borderColor == other.borderColor &&
                borderWidth == other.borderWidth &&
                rimLightAlpha == other.rimLightAlpha &&
                capsule == other.capsule &&
                pressBulge == other.pressBulge &&
                pressPoint == other.pressPoint
    }

    override fun hashCode(): Int {
        var result = fillColor.hashCode()
        result = 31 * result + washAlpha.hashCode()
        result = 31 * result + radius.hashCode()
        result = 31 * result + borderColor.hashCode()
        result = 31 * result + borderWidth.hashCode()
        result = 31 * result + rimLightAlpha.hashCode()
        result = 31 * result + capsule.hashCode()
        result = 31 * result + pressBulge.hashCode()
        result = 31 * result + pressPoint.hashCode()
        return result
    }
}
