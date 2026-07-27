package com.conreo.couchytv.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath

/**
 * iOS-style continuous ("squircle") corners: the curvature eases in from the
 * straight edge instead of meeting a quarter-circle arc abruptly, which is how
 * a rounded corner reads as smooth to the eye rather than "mathematically"
 * round. `smoothing` 0f = a plain arc (same as RoundedCornerShape), ~0.6f ≈ iOS.
 */
class SmoothCornerShape(
    private val radius: Dp,
    private val smoothing: Float = 0.6f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val minDim = size.minDimension
        val r = with(density) { radius.toPx() }.coerceIn(0f, minDim / 2f)
        // Degenerate size (measured 0×0 mid-transition) or no radius: a plain
        // rect. Building a RoundedPolygon there can emit a NaN path and crash
        // rendering. Any failure in the squircle math falls back the same way.
        val path = if (minDim < 1f || r < 0.5f) null else runCatching {
            RoundedPolygon.rectangle(
                width = size.width,
                height = size.height,
                rounding = CornerRounding(r, smoothing),
                centerX = size.width / 2f,
                centerY = size.height / 2f,
            ).toPath().asComposePath()
        }.getOrNull()
        return if (path != null) Outline.Generic(path)
        else Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
    }
}
