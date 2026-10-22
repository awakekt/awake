/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.scale
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.core.graphics2d.TextureRegion
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.UiLinearGradient
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where text lands on the device pixel grid.
 *
 * A distance-field glyph is antialiased by the coverage at the pixel it falls on, so a stem or a
 * baseline that straddles two pixels is two grey ones. Each line's start and baseline therefore
 * snap to whole device pixels, and the em size to a whole number of them, except under a scale
 * transform, where snapping would make animated text move in steps.
 */
class TextPixelSnapTest {
    private val white = Color(1f, 1f, 1f, 1f)

    /** The baseline a `H` quad was placed on, recovered from the quad and the font's own metrics. */
    private fun baselineOf(glyph: UiDrawPrimitive.Glyph, glyphPx: Float, scale: Float = 1f): Float {
        val font = checkNotNull(glyph.font)
        val rect = checkNotNull(font.glyphFor('H'.code, FontWeight.Normal))
        return glyph.y - rect.offsetYEm * glyphPx * scale + font.ascentEm * glyphPx * scale
    }

    private fun glyphs(density: Float = 1f, content: context(com.awakekt.awake.compose.runtime.Composer) () -> Unit): List<UiDrawPrimitive.Glyph> =
        ComposeHost(density = density).frame(FrameInput(400, 200), content).primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

    /**
     * A 17.5sp line box around 12sp text puts the unsnapped baseline at 12.85 and the second line's
     * at 30.35: both quads sat on a fraction of a pixel. Measured on the unsnapped code before this
     * change: baselines 12.8516 and 30.3516.
     */
    @Test
    fun everyLineSitsOnAWholeBaseline() {
        val quads = glyphs {
            Text("H\nH", style = TextStyle(color = white, size = 12.sp, lineHeight = 17.5f.sp))
        }

        assertEquals(2, quads.size)
        quads.forEach { assertEquals(floor(baselineOf(it, 12f) + 0.5f), baselineOf(it, 12f), 1e-3f, "baseline ${baselineOf(it, 12f)}") }
        assertEquals(13f, baselineOf(quads[0], 12f), 1e-3f)
        assertEquals(30f, baselineOf(quads[1], 12f), 1e-3f)
    }

    /**
     * Under a 1.5x scale the same text keeps its fractional position: 1.5 * 12.8516 - 4.5 = 14.78,
     * so the baseline moves smoothly while the scale animates instead of in whole-pixel steps.
     */
    @Test
    fun scaledTextKeepsItsSubpixelPosition() {
        val quads = glyphs {
            Text("H", modifier = Modifier.scale(1.5f), style = TextStyle(color = white, size = 12.sp, lineHeight = 17.5f.sp))
        }

        val baseline = baselineOf(quads.single(), 12f, scale = 1.5f)
        assertEquals(14.777f, baseline, 5e-3f)
        assertTrue(abs(baseline - baseline.let { floor(it + 0.5f) }) > 0.1f, "a scaled baseline must stay fractional, was $baseline")
    }

    /** `13.sp` is 16.25 and 19.5 device pixels at 125% and 150%; the em becomes 16 and 20. */
    @Test
    fun theEmIsAWholeNumberOfDevicePixels() {
        val style = TextStyle(size = 13.sp)
        val policy = TextMeasurePolicy("Hello", style, UiFonts.trueSans())

        assertEquals(13f, policy.glyphPixels(1f, 1f))
        assertEquals(16f, policy.glyphPixels(1.25f, 1f))
        assertEquals(20f, policy.glyphPixels(1.5f, 1f))
        assertEquals(15f, TextMeasurePolicy("Hello", TextStyle(size = 12.sp), UiFonts.trueSans()).glyphPixels(1.25f, 1f))
        assertEquals(1f, TextMeasurePolicy("Hello", TextStyle(size = 0.2f.sp), UiFonts.trueSans()).glyphPixels(1f, 1f))
    }

    /** Measuring and painting both read the rounded em: a quad is as wide as the em it was measured at. */
    @Test
    fun paintedGlyphsUseTheSameRoundedEmAsMeasurement() {
        val quads = glyphs(density = 1.25f) {
            Text("H", style = TextStyle(color = white, size = 13.sp))
        }

        val font = checkNotNull(quads.single().font)
        val rect = checkNotNull(font.glyphFor('H'.code, FontWeight.Normal))
        assertEquals(rect.widthEm * 16f, quads.single().w, 1e-3f)
    }

    /**
     * A field scrolls its text by a fraction of a pixel: the first glyph's pen starts on the whole
     * pixel nearest 4.5, and a scope that scales keeps 4.5.
     */
    @Test
    fun aRunStartsOnAWholePixelUnlessTheScopeScales() {
        val font = UiFonts.trueSans()
        val run = TextRun("l", TextStyle.Default, font, glyphPx = 12f, density = 1f)
        val rect = checkNotNull(font.glyphFor('l'.code, FontWeight.Normal))
        val bearing = rect.offsetXEm * 12f

        val aligned = RecordingScope(aligned = true).also { run.paint(it, white, offsetX = 4.5f) }
        val scaled = RecordingScope(aligned = false).also { run.paint(it, white, offsetX = 4.5f) }

        assertEquals(5f + bearing, aligned.glyphs.single().x, 1e-4f)
        assertEquals(4.5f + bearing, scaled.glyphs.single().x, 1e-4f)
    }

    /** Records the glyph quads a run draws; nothing else is used. */
    private class RecordingScope(aligned: Boolean) : DrawScope {
        val glyphs = mutableListOf<UiDrawPrimitive.Glyph>()
        override val width = 100
        override val height = 40
        override val density = 1f
        override val isDevicePixelAligned = aligned

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
}
