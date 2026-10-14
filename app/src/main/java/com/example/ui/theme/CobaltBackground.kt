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
import androidx.compose.ui.unit.dp

/**
 * خلفية Stealth Silver الفاخرة:
 *  1) أساس أسود نقي #000000
 *  2) إشعاع فضي بلاتيني خافت جداً وناعم من الأعلى
 */
@Composable
fun CobaltAtmosphericBackground(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val bloomRadius = with(density) { 380.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CG.Void)
            .drawBehind {
                drawCircleRadialBloom(bloomRadius)
            }
    )
}

private fun DrawScope.drawCircleRadialBloom(radius: Float) {
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color(0x26F1F5F9), // Platinum hint
                0.45f to Color(0x0DCBD5E1),
                0.80f to Color(0x00000000)
            ),
            center = Offset(size.width * 0.5f, size.height * 0.10f),
            radius = radius
        ),
        size = size
    )
}
