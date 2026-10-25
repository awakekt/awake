/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.core.graphics2d.TextureRegion
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.UiLinearGradient
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.text.font.UiFont

/**
 * Records CPU glyph placement; unexpected drawing fails the layout-only test.
 *
 * Not device-pixel aligned, so a run draws every size from the distance field, whose quads carry the
 * face's exact bearings, rather than exact-size pixel glyphs rounded to whole pixels. The test paints at
 * the origin, where alignment moves nothing, and the font keeps its production pixel-glyph limit, which
 * the line box reads.
 */
internal class LayoutRecordingScope : DrawScope {
    val glyphs = mutableListOf<UiDrawPrimitive.Glyph>()
    override val width = 100
    override val height = 40
    override val density = 1f
    override val isDevicePixelAligned = false

    override fun drawGlyph(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        color: Color,
        font: UiFont?,
        page: Int,
    ) {
        glyphs += UiDrawPrimitive.Glyph(x, y, width, height, u0, v0, u1, v1, color, font = font, page = page)
    }

    override fun drawRect(x: Float, y: Float, width: Float, height: Float, color: Color) = unused()
    override fun drawRoundedRect(x: Float, y: Float, width: Float, height: Float, color: Color, radius: Float) = unused()
    override fun drawShadow(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color,
        radius: Float,
        offsetX: Float,
        offsetY: Float,
        blurRadius: Float,
        spread: Float,
        gradient: UiLinearGradient?,
    ) = unused()
    override fun clipped(inset: Float, block: () -> Unit) = unused()
    override fun withAlpha(alpha: Float, block: () -> Unit) = unused()
    override fun withScale(scaleX: Float, scaleY: Float, block: () -> Unit) = unused()
    override fun drawPath(path: DrawPath, color: Color) = unused()
    override fun drawStrokedPath(path: DrawPath, stroke: DrawStroke, color: Color) = unused()
    override fun drawMesh(mesh: ColoredTriangleMesh) = unused()
    override fun clippedPath(path: DrawPath, safeInteriorRect: Rectangle?, block: () -> Unit) = unused()
    override fun drawTexture(
        material: Any,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        region: TextureRegion,
        tint: Color,
        filterQuality: FilterQuality,
        premultiplied: Boolean,
    ) = unused()

    @Suppress("LongParameterList")
    override fun drawBackdropBlur(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        blurRadius: Float,
        tint: Color,
        saturation: Float,
        noise: Float,
        cornerRadius: Float,
        smoothing: Float,
        path: DrawPath?,
        fallback: Color?,
    ) = unused()

    override fun emit(primitive: UiDrawPrimitive) = unused()

    private fun unused(): Nothing = error("TextRun draws glyphs only")
}
