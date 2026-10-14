package com.example.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

// =============================================================================
//  محرّك الزجاج الحقيقي (Frosted Glass Engine)
//  تطبيق Cove — أسطح زجاجية فائقة الدقة بتمويه فيزيائي وحساب حقيقي لموضع الشاشة
// =============================================================================

/** صورة في الذاكرة كأرقام ARGB — الشكل النقي الذي تعمل عليه كل الحسابات. */
internal data class PixelImage(val width: Int, val height: Int, val pixels: IntArray)

/** مستطيل قصّ داخل الصورة الأصلية. */
internal data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** اتجاه الكاميرا المسجَّل في الصورة: كم درجة تدوير + هل فيها انعكاس. */
internal data class ExifTransform(val rotationDegrees: Int, val flipHorizontal: Boolean) {
    val isIdentity: Boolean get() = rotationDegrees == 0 && !flipHorizontal
}

internal object GlassMath {

    /** أقصى نصف قطر تمويه على شبكة الشاشة. */
    const val MAX_BLUR_PX = 25

    /** الإشباع اللوني الافتراضي: يعيد الحيوية التي يسرقها التمويه. */
    const val DEFAULT_VIBRANCY = 1.35f

    /** درجة تغطية البطاقة الافتراضية: تعتيم كثيف يحافظ على قراءة النصوص العربية بوضوح تام. */
    const val DEFAULT_TINT_ALPHA = 0.52f

    /**
     * تعتيم ما يمرّ عبر الزجاج: صورة ساطعة جداً تجعل الحروف البيضاء باهتة،
     * فهذه الدرجة تحفظ وضوح الحروف في كل الحالات.
     */
    const val BACKDROP_BRIGHTNESS = 0.85f

    /** فوق هذه الإضاءة نضغط الإضاءة بلطف (منحنى ناعم لا قطعاً حاداً). */
    const val HIGHLIGHT_KNEE = 150f

    /** مقدار ضغط الإضاءة فوق الحدّ. */
    const val HIGHLIGHT_COMPRESSION = 0.55f

    // ------------------------------------------------------------------ الحدّ الضوئي
    /** عرض الحدّ على البطاقات: ٠٫٦ نقطة — رفيع جداً وواضح. */
    const val EDGE_RIM_WIDTH_DP = 0.6f

    /** عرض الحدّ على الشريط العلوي: ٠٫٧ نقطة. */
    const val EDGE_RIM_WIDTH_HEADER_DP = 0.7f

    /** سطوع الحدّ على الجانبين. */
    const val EDGE_RIM_ALPHA_SIDE = 0.26f

    /** سطوع الحدّ في الأعلى (لمعة الضوء). */
    const val EDGE_RIM_ALPHA_TOP = 0.55f

    /** سطوع الحدّ في الأسفل (همسة انعكاس خفيفة). */
    const val EDGE_RIM_ALPHA_BOTTOM = 0.12f

    /** طول الجزء العلوي الساطع من الارتفاع. */
    const val EDGE_RIM_TOP_SPAN = 0.16f

    /** طول الهمسة السفلية من الارتفاع. */
    const val EDGE_RIM_BOTTOM_SPAN = 0.14f

    /** كم تمتدّ خلفية الصورة تحت حواف الشاشة (لتنطبق مع ما تراه العين). */
    const val OUTER_EDGE_INSET = 0.12f

    /** تغطية البطاقات البيضاء (زر الإدخال): تبقى بارزة لكنها زجاجية أيضاً. */
    const val ACCENT_TINT_BASE = 0.35f
    const val ACCENT_TINT_SLOPE = 0.30f

    /** القصّ المركزي إلى نسبة الشاشة */
    fun centerCrop(sourceWidth: Int, sourceHeight: Int, targetAspect: Float): CropRect {
        if (sourceWidth <= 0 || sourceHeight <= 0) return CropRect(0, 0, 0, 0)
        if (targetAspect <= 0f) return CropRect(0, 0, sourceWidth, sourceHeight)
        val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
        return if (sourceAspect > targetAspect) {
            val width = (sourceHeight * targetAspect).roundToInt().coerceIn(1, sourceWidth)
            val left = (sourceWidth - width) / 2
            CropRect(left, 0, left + width, sourceHeight)
        } else {
            val height = (sourceWidth / targetAspect).roundToInt().coerceIn(1, sourceHeight)
            val top = (sourceHeight - height) / 2
            CropRect(0, top, sourceWidth, top + height)
        }
    }

    /** عامل التنزيل الصحيح */
    fun downsampleFactor(sourceWidth: Int, targetWidth: Int): Int {
        if (sourceWidth <= 0 || targetWidth <= 0) return 1
        return max(1, sourceWidth / targetWidth)
    }

    /** تنزيل بجودة عالية */
    fun boxDownsample(image: PixelImage, factor: Int): PixelImage {
        if (factor <= 1 || image.width <= 0 || image.height <= 0) return image
        val outWidth = max(1, image.width / factor)
        val outHeight = max(1, image.height / factor)
        val out = IntArray(outWidth * outHeight)
        val blockSize = factor * factor
        for (y in 0 until outHeight) {
            val sourceY = y * factor
            for (x in 0 until outWidth) {
                val sourceX = x * factor
                var r = 0; var g = 0; var b = 0
                for (dy in 0 until factor) {
                    val row = (sourceY + dy) * image.width
                    for (dx in 0 until factor) {
                        val p = image.pixels[row + sourceX + dx]
                        r += (p ushr 16) and 0xFF
                        g += (p ushr 8) and 0xFF
                        b += p and 0xFF
                    }
                }
                out[y * outWidth + x] =
                    OPAQUE or ((r / blockSize) shl 16) or ((g / blockSize) shl 8) or (b / blockSize)
            }
        }
        return PixelImage(outWidth, outHeight, out)
    }

    /** تمويه حقيقي ثلاثي التمرير */
    fun stackBlur(image: PixelImage, radius: Int) {
        if (radius <= 0 || image.width <= 0 || image.height <= 0) return
        val r = radius.coerceAtMost(128)
        val width = image.width
        val height = image.height
        val a = IntArray(width * height)
        val b = IntArray(width * height)
        System.arraycopy(image.pixels, 0, a, 0, a.size)
        repeat(3) {
            boxBlurHorizontal(a, b, width, height, r)
            boxBlurVertical(b, a, width, height, r)
        }
        System.arraycopy(a, 0, image.pixels, 0, a.size)
    }

    /** إشباع لوني حول الإضاءة */
    fun saturate(image: PixelImage, amount: Float) {
        val a = amount.coerceIn(0f, 3f)
        if (a == 1f) return
        val pixels = image.pixels
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            val luminance = 0.299f * r + 0.587f * g + 0.114f * b
            val nr = (luminance + (r - luminance) * a).roundToInt().coerceIn(0, 255)
            val ng = (luminance + (g - luminance) * a).roundToInt().coerceIn(0, 255)
            val nb = (luminance + (b - luminance) * a).roundToInt().coerceIn(0, 255)
            pixels[i] = OPAQUE or (nr shl 16) or (ng shl 8) or nb
        }
    }

    /** تعتيم الإضاءة */
    fun dim(image: PixelImage) {
        val pixels = image.pixels
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = transmittedChannel((p ushr 16) and 0xFF)
            val g = transmittedChannel((p ushr 8) and 0xFF)
            val b = transmittedChannel(p and 0xFF)
            pixels[i] = OPAQUE or (r shl 16) or (g shl 8) or b
        }
    }

    fun renderScale(blurRadiusPx: Int): Float = if (blurRadiusPx <= 3) 1f else 0.5f

    fun scaledSize(targetPx: Int, scale: Float): Int = max(1, (targetPx * scale).roundToInt())

    fun blurRadiusPx(setting: Int): Int = setting.coerceIn(0, MAX_BLUR_PX)

    fun surfaceTintAlpha(opacity: Float): Float = opacity.coerceIn(0.05f, 1f)

    fun photoVisibility(opacity: Float): Float = 1f - surfaceTintAlpha(opacity)

    fun accentTintAlpha(opacity: Float): Float =
        (ACCENT_TINT_BASE + ACCENT_TINT_SLOPE * surfaceTintAlpha(opacity)).coerceIn(0.05f, 0.9f)

    fun transmittedChannel(value: Int): Int {
        val graded = value * BACKDROP_BRIGHTNESS
        val compressed = if (graded <= HIGHLIGHT_KNEE) {
            graded
        } else {
            HIGHLIGHT_KNEE + (graded - HIGHLIGHT_KNEE) * HIGHLIGHT_COMPRESSION
        }
        return compressed.roundToInt().coerceIn(0, 255)
    }

    /** سطوع الحدّ الضوئي بحسب الموضع الرأسي */
    fun edgeRimAlpha(fractionOfHeight: Float): Float {
        val t = fractionOfHeight.coerceIn(0f, 1f)
        val top = if (t <= EDGE_RIM_TOP_SPAN) {
            (EDGE_RIM_ALPHA_TOP - EDGE_RIM_ALPHA_SIDE) * (1f - t / EDGE_RIM_TOP_SPAN)
        } else {
            0f
        }
        val bottom = if (t >= 1f - EDGE_RIM_BOTTOM_SPAN) {
            (EDGE_RIM_ALPHA_BOTTOM - EDGE_RIM_ALPHA_SIDE) *
                ((t - (1f - EDGE_RIM_BOTTOM_SPAN)) / EDGE_RIM_BOTTOM_SPAN)
        } else {
            0f
        }
        return (EDGE_RIM_ALPHA_SIDE + top + bottom).coerceIn(0f, 1f)
    }

    private const val OPAQUE = 0xFF shl 24
}

private fun boxBlurHorizontal(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int
) {
    val divisor = radius * 2 + 1
    val half = divisor / 2
    for (y in 0 until height) {
        val row = y * width
        var sumR = 0; var sumG = 0; var sumB = 0
        for (i in -radius..radius) {
            val p = source[row + i.coerceIn(0, width - 1)]
            sumR += (p ushr 16) and 0xFF
            sumG += (p ushr 8) and 0xFF
            sumB += p and 0xFF
        }
        for (x in 0 until width) {
            target[row + x] = (0xFF shl 24) or
                (((sumR + half) / divisor) shl 16) or
                (((sumG + half) / divisor) shl 8) or ((sumB + half) / divisor)
            val leaving = source[row + (x - radius).coerceIn(0, width - 1)]
            val entering = source[row + (x + radius + 1).coerceIn(0, width - 1)]
            sumR += ((entering ushr 16) and 0xFF) - ((leaving ushr 16) and 0xFF)
            sumG += ((entering ushr 8) and 0xFF) - ((leaving ushr 8) and 0xFF)
            sumB += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}

private fun boxBlurVertical(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int
) {
    val divisor = radius * 2 + 1
    val half = divisor / 2
    for (x in 0 until width) {
        var sumR = 0; var sumG = 0; var sumB = 0
        for (i in -radius..radius) {
            val p = source[i.coerceIn(0, height - 1) * width + x]
            sumR += (p ushr 16) and 0xFF
            sumG += (p ushr 8) and 0xFF
            sumB += p and 0xFF
        }
        for (y in 0 until height) {
            target[y * width + x] = (0xFF shl 24) or
                (((sumR + half) / divisor) shl 16) or
                (((sumG + half) / divisor) shl 8) or ((sumB + half) / divisor)
            val leaving = source[(y - radius).coerceIn(0, height - 1) * width + x]
            val entering = source[(y + radius + 1).coerceIn(0, height - 1) * width + x]
            sumR += ((entering ushr 16) and 0xFF) - ((leaving ushr 16) and 0xFF)
            sumG += ((entering ushr 8) and 0xFF) - ((leaving ushr 8) and 0xFF)
            sumB += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}

// ---------------------------------------------------------------- خط أنابيب العرض

internal fun renderGlassBitmap(
    source: Bitmap,
    targetWidthPx: Int,
    targetHeightPx: Int,
    blurRadiusPx: Int,
    saturation: Float = GlassMath.DEFAULT_VIBRANCY
): Bitmap? {
    if (targetWidthPx <= 0 || targetHeightPx <= 0 || source.width <= 0 || source.height <= 0) {
        return null
    }

    val renderScale = GlassMath.renderScale(blurRadiusPx)
    val renderWidth = GlassMath.scaledSize(targetWidthPx, renderScale)
    val renderHeight = GlassMath.scaledSize(targetHeightPx, renderScale)
    val renderRadius = max(0, (blurRadiusPx * renderScale).roundToInt())

    val crop = GlassMath.centerCrop(
        source.width,
        source.height,
        renderWidth.toFloat() / renderHeight.toFloat()
    )
    if (crop.width <= 0 || crop.height <= 0) return null

    val pixels = IntArray(crop.width * crop.height)
    source.getPixels(pixels, 0, crop.width, crop.left, crop.top, crop.width, crop.height)

    var image = PixelImage(crop.width, crop.height, pixels)
    image = GlassMath.boxDownsample(image, GlassMath.downsampleFactor(crop.width, renderWidth))

    val scaled = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
    scaled.setPixels(image.pixels, 0, image.width, 0, 0, image.width, image.height)

    val exact = if (image.width != renderWidth || image.height != renderHeight) {
        Bitmap.createScaledBitmap(scaled, renderWidth, renderHeight, true).also { scaled.recycle() }
    } else {
        scaled
    }

    val working = PixelImage(
        renderWidth,
        renderHeight,
        IntArray(renderWidth * renderHeight).also {
            exact.getPixels(it, 0, renderWidth, 0, 0, renderWidth, renderHeight)
        }
    )
    exact.recycle()
    GlassMath.stackBlur(working, GlassMath.blurRadiusPx(renderRadius))
    GlassMath.saturate(working, saturation)
    GlassMath.dim(working)

    val output = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
    output.setPixels(working.pixels, 0, renderWidth, 0, 0, renderWidth, renderHeight)
    return output
}

/** ذاكرة لبصمة واحدة */
private object GlassCache {
    private var key: String? = null
    private var bitmap: Bitmap? = null

    @Synchronized
    fun get(sourceKey: String, width: Int, height: Int, blur: Int, saturation: Float): Bitmap? {
        return if (key == "$sourceKey|$width|$height|$blur|$saturation") bitmap else null
    }

    @Synchronized
    fun put(
        sourceKey: String,
        width: Int,
        height: Int,
        blur: Int,
        saturation: Float,
        value: Bitmap?
    ) {
        key = "$sourceKey|$width|$height|$blur|$saturation"
        bitmap = value
    }
}

/**
 * يبني صورة «ما وراء الزجاج» ويُحدّثها خارج الخيط الرئيسي (Dispatchers.Default)
 */
@Composable
internal fun rememberGlassImage(
    sourceBitmap: Bitmap?,
    sourceKey: String,
    targetWidthPx: Int,
    targetHeightPx: Int,
    blurRadiusPx: Int,
    saturation: Float = GlassMath.DEFAULT_VIBRANCY
): ImageBitmap? {
    val state = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(sourceKey, sourceBitmap, targetWidthPx, targetHeightPx, blurRadiusPx, saturation) {
        val bitmap = sourceBitmap ?: return@LaunchedEffect
        val cached = GlassCache.get(sourceKey, targetWidthPx, targetHeightPx, blurRadiusPx, saturation)
        if (cached != null) {
            state.value = cached.asImageBitmap()
            return@LaunchedEffect
        }
        delay(60)
        try {
            val rendered = withContext(Dispatchers.Default) {
                renderGlassBitmap(bitmap, targetWidthPx, targetHeightPx, blurRadiusPx, saturation)
            }
            if (rendered != null) {
                GlassCache.put(sourceKey, targetWidthPx, targetHeightPx, blurRadiusPx, saturation, rendered)
                state.value = rendered.asImageBitmap()
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
    return state.value
}

// ---------------------------------------------------------------- توزيع الزجاج

class GlassSampler(
    val image: ImageBitmap,
    val containerWidthPx: Int,
    val containerHeightPx: Int,
    val originInWindow: Offset = Offset.Zero,
    val opacity: Float = GlassMath.DEFAULT_TINT_ALPHA
) {
    val pxPerNodePx: Float get() = image.width.toFloat() / containerWidthPx.coerceAtLeast(1)

    fun tintAlpha(): Float = GlassMath.surfaceTintAlpha(opacity)

    fun accentTintAlpha(): Float = GlassMath.accentTintAlpha(opacity)
}

val LocalGlassSampler = compositionLocalOf<GlassSampler?> { null }

@Composable
fun rememberScreenSizePx(): Pair<Int, Int> {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val width = remember(configuration.screenWidthDp, density.density) {
        with(density) { configuration.screenWidthDp.dp.roundToPx() }
    }
    val height = remember(configuration.screenHeightDp, density.density) {
        with(density) { configuration.screenHeightDp.dp.roundToPx() }
    }
    return width to height
}

// ---------------------------------------------------------------- توليد خلفية Cove برمجياً

/**
 * التقاط تدرجات تطبيق Cove الحالية برمجياً كعينة للمحرك (Cobalt Atmospheric Gradient)
 */
fun createCobaltAtmosphereBitmap(width: Int = 360, height: Int = 800): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1) Void base (#000000 Pure Black)
    canvas.drawColor(android.graphics.Color.BLACK)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 2) Radial bloom at (50%, 12%)
    val bloomRadius = width * 1.05f
    paint.shader = RadialGradient(
        width * 0.5f,
        height * 0.12f,
        bloomRadius,
        intArrayOf(
            android.graphics.Color.argb(0x8C, 0x25, 0x63, 0xEB),
            android.graphics.Color.argb(0x42, 0x1D, 0x4E, 0xD8),
            android.graphics.Color.argb(0x00, 0x00, 0x00, 0x00)
        ),
        floatArrayOf(0f, 0.45f, 0.85f),
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    // 3) Blurred orb at top
    val orbRadius = width * 0.80f
    paint.shader = RadialGradient(
        width * 0.5f,
        height * 0.04f,
        orbRadius,
        intArrayOf(
            android.graphics.Color.argb(0x55, 0x38, 0x82, 0xF6),
            android.graphics.Color.argb(0x00, 0x25, 0x63, 0xEB)
        ),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    // 4) Bottom ambient subtle cobalt glow (enhances dock/footer glass reflection)
    paint.shader = RadialGradient(
        width * 0.5f,
        height * 0.92f,
        width * 0.70f,
        intArrayOf(
            android.graphics.Color.argb(0x25, 0x1E, 0x40, 0xAF),
            android.graphics.Color.argb(0x00, 0x00, 0x00, 0x00)
        ),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    return bitmap
}

/**
 * يوفر عينة زجاج حية مبنية على تدرجات تطبيق Cove الحالية مع تمويه كثيف وتعتيم ناعم.
 */
@Composable
fun rememberCobaltGlassSampler(
    blurRadius: Int = 25,
    opacity: Float = GlassMath.DEFAULT_TINT_ALPHA
): GlassSampler? {
    val (screenWidth, screenHeight) = rememberScreenSizePx()
    val atmosphereBitmap = remember {
        createCobaltAtmosphereBitmap(width = 360, height = 800)
    }
    val glassImage = rememberGlassImage(
        sourceBitmap = atmosphereBitmap,
        sourceKey = "cove_atmospheric_backdrop",
        targetWidthPx = screenWidth,
        targetHeightPx = screenHeight,
        blurRadiusPx = blurRadius,
        saturation = GlassMath.DEFAULT_VIBRANCY
    )
    return remember(glassImage, screenWidth, screenHeight, opacity) {
        if (glassImage != null && screenWidth > 0 && screenHeight > 0) {
            GlassSampler(
                image = glassImage,
                containerWidthPx = screenWidth,
                containerHeightPx = screenHeight,
                originInWindow = Offset.Zero,
                opacity = opacity
            )
        } else null
    }
}

// ---------------------------------------------------------------- سطح الزجاج (GlassSurfaceNode)

private class GlassSurfaceNode(
    var sampler: GlassSampler?,
    var fillColor: Color,
    var tintAlpha: Float,
    var borderColor: Color,
    var borderWidth: Dp,
    var rimWidth: Dp,
    var radius: Dp,
    var capsule: Boolean
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    var positionInWindow: Offset = Offset.Zero

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        positionInWindow = coordinates.positionInWindow()
    }

    override fun ContentDrawScope.draw() {
        val active = sampler
        val radiusPx = if (capsule) size.height / 2f else radius.toPx()
        val shapePath = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radiusPx)))
        }

        clipPath(shapePath) {
            if (active != null && active.containerWidthPx > 0 && active.containerHeightPx > 0) {
                // ١) ما خلف البطاقة: الجزء المقابل من الخلفية المموّهة بحساب دقيق لموقع العنصر في النافذة
                val imageWidth = active.image.width
                val imageHeight = active.image.height
                val relX = positionInWindow.x - active.originInWindow.x
                val relY = positionInWindow.y - active.originInWindow.y
                val scaleX = imageWidth.toFloat() / active.containerWidthPx
                val scaleY = imageHeight.toFloat() / active.containerHeightPx

                val srcX = (relX * scaleX).roundToInt().coerceIn(0, max(0, imageWidth - 1))
                val srcY = (relY * scaleY).roundToInt().coerceIn(0, max(0, imageHeight - 1))
                val srcW = (size.width * scaleX).roundToInt().coerceIn(1, max(1, imageWidth - srcX))
                val srcH = (size.height * scaleY).roundToInt().coerceIn(1, max(1, imageHeight - srcY))

                drawImage(
                    image = active.image,
                    srcOffset = IntOffset(srcX, srcY),
                    srcSize = IntSize(srcW, srcH),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(
                        size.width.roundToInt().coerceAtLeast(1),
                        size.height.roundToInt().coerceAtLeast(1)
                    ),
                    alpha = 1f,
                    style = Fill,
                    filterQuality = FilterQuality.Low
                )
                // ٢) طبقة الثيم والتعتيم لحفظ وضوح الخط
                drawRect(color = fillColor.copy(alpha = tintAlpha))
            } else {
                // في غياب العينة: التعبئة الصلبة القياسية
                drawRect(color = fillColor)
            }
        }

        // ٣) الحافة واللمعة الضوئية العلوية
        if (active == null) {
            val width = borderWidth.toPx()
            if (width > 0f) {
                drawPath(
                    path = edgePath(width, size.width, size.height, radiusPx),
                    color = borderColor,
                    style = Stroke(width = width)
                )
            }
        } else {
            val themeWidth = borderWidth.toPx()
            if (themeWidth > 0f) {
                clipPath(shapePath) {
                    drawPath(
                        path = edgePath(themeWidth, size.width, size.height, radiusPx),
                        color = borderColor,
                        style = Stroke(width = themeWidth)
                    )
                }
            }
            // لمعة الحافة العلوية والجانبية
            val rimW = rimWidth.toPx()
            if (rimW > 0f) {
                val brush = Brush.verticalGradient(
                    colorStops = rimColorStops(),
                    startY = 0f,
                    endY = size.height
                )
                clipPath(shapePath) {
                    drawPath(path = edgePath(rimW, size.width, size.height, radiusPx), brush = brush, style = Stroke(width = rimW))
                }
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

private fun rimColorStops(): Array<Pair<Float, Color>> {
    val steps = 12
    val stops = ArrayList<Pair<Float, Color>>(steps + 1)
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        stops.add(t to Color.White.copy(alpha = GlassMath.edgeRimAlpha(t)))
    }
    return stops.toTypedArray()
}

private class GlassSurfaceElement(
    val sampler: GlassSampler?,
    val fillColor: Color,
    val tintAlpha: Float,
    val borderColor: Color,
    val borderWidth: Dp,
    val rimWidth: Dp,
    val radius: Dp,
    val capsule: Boolean
) : ModifierNodeElement<GlassSurfaceNode>() {

    override fun create(): GlassSurfaceNode = GlassSurfaceNode(
        sampler = sampler,
        fillColor = fillColor,
        tintAlpha = tintAlpha,
        borderColor = borderColor,
        borderWidth = borderWidth,
        rimWidth = rimWidth,
        radius = radius,
        capsule = capsule
    )

    override fun update(node: GlassSurfaceNode) {
        node.sampler = sampler
        node.fillColor = fillColor
        node.tintAlpha = tintAlpha
        node.borderColor = borderColor
        node.borderWidth = borderWidth
        node.rimWidth = rimWidth
        node.radius = radius
        node.capsule = capsule
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlassSurfaceElement) return false
        return sampler === other.sampler &&
            fillColor == other.fillColor &&
            tintAlpha == other.tintAlpha &&
            borderColor == other.borderColor &&
            borderWidth == other.borderWidth &&
            rimWidth == other.rimWidth &&
            radius == other.radius &&
            capsule == other.capsule
    }

    override fun hashCode(): Int {
        var result = sampler?.hashCode() ?: 0
        result = 31 * result + fillColor.hashCode()
        result = 31 * result + tintAlpha.hashCode()
        result = 31 * result + borderColor.hashCode()
        result = 31 * result + borderWidth.hashCode()
        result = 31 * result + rimWidth.hashCode()
        result = 31 * result + radius.hashCode()
        result = 31 * result + capsule.hashCode()
        return result
    }
}

/**
 * سطح زجاجي كامل: ما خلفه مموّهاً + طبقة الثيم + حدّ ضوئي رفيع على الحافة.
 */
fun Modifier.glassSurface(
    sampler: GlassSampler?,
    fillColor: Color,
    tintAlpha: Float = GlassMath.DEFAULT_TINT_ALPHA,
    radius: Dp = 20.dp,
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    borderWidth: Dp = 0.6.dp,
    rimWidth: Dp = GlassMath.EDGE_RIM_WIDTH_DP.dp,
    capsule: Boolean = false
): Modifier = this.then(
    GlassSurfaceElement(
        sampler = sampler,
        fillColor = fillColor,
        tintAlpha = tintAlpha,
        borderColor = borderColor,
        borderWidth = borderWidth,
        rimWidth = rimWidth,
        radius = radius,
        capsule = capsule
    )
)

/**
 * ملحق مريح يستخدم تلقائياً [LocalGlassSampler.current] في حال لم يُمرّر sampler مخصص.
 */
@Composable
fun Modifier.glassSurface(
    fillColor: Color = CG.SearchBar,
    tintAlpha: Float = GlassMath.DEFAULT_TINT_ALPHA,
    radius: Dp = 20.dp,
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    borderWidth: Dp = 0.6.dp,
    rimWidth: Dp = GlassMath.EDGE_RIM_WIDTH_DP.dp,
    capsule: Boolean = false,
    sampler: GlassSampler? = LocalGlassSampler.current
): Modifier = this.then(
    GlassSurfaceElement(
        sampler = sampler,
        fillColor = fillColor,
        tintAlpha = tintAlpha,
        borderColor = borderColor,
        borderWidth = borderWidth,
        rimWidth = rimWidth,
        radius = radius,
        capsule = capsule
    )
)
