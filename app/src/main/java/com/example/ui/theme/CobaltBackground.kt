package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity

/**
 * الخلفية الجوية Cobalt Glass — طبقة ثابتة خلف المحتوى (لا تتمرر معه):
 *  1) أساس كحلي #07090E
 *  2) إشعاع شعاعي علوي: radial 380dp عند (50% , 12%)
 *  3) بقعة ضبابية 520×340dp عند -96dp (تُقرب بتدرج شعاعي معاير، blur فعلي على API31+).
 */
@Composable
fun CobaltAtmosphericBackground(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val bloomRadius = with(density) { 380.dp.toPx() }
    val orbW = with(density) { 520.dp.toPx() }
    val orbH = with(density) { 340.dp.toPx() }
    val orbTop = with(density) { (-96).dp.toPx() }
    val orbBlur = with(density) { 110.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CG.Void)
            .drawBehind {
                // 2) Radial bloom
                drawCircleRadialBloom(bloomRadius)
                // 3) Blurred orb (approximated radial ellipse)
                drawOrb(orbW, orbH, orbTop, orbBlur)
            }
    )
}

private fun DrawScope.drawCircleRadialBloom(radius: Float) {
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color(0x7A2563EB),
                0.42f to Color(0x381D4ED8),
                0.80f to Color(0x0007090E)
            ),
            center = Offset(size.width * 0.5f, size.height * 0.12f),
            radius = radius
        ),
        size = size
    )
}

private fun DrawScope.drawOrb(w: Float, h: Float, top: Float, blur: Float) {
    val cx = size.width / 2f
    val cy = top + h / 2f
    val rx = w / 2f + blur
    val ry = h / 2f + blur
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x402563EB), Color(0x002563EB)),
            center = Offset(cx, cy),
            radius = maxOf(rx, ry)
        ),
        size = size
    )
}
