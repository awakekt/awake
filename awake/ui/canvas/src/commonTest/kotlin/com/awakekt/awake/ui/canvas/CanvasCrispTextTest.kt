/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFontSamplingMode
import com.awakekt.awake.core.text.font.UiFonts
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Text on a scaled canvas, checked at the glyph path: whether each glyph is an exact-size coverage
 * glyph on whole device pixels, or a distance-field one.
 *
 * A canvas rounds a text element's em to whole device pixels after the scale and the density, so text
 * up to the font's pixel-glyph size draws from exact-size glyphs at any scale, 1.4125 included, and
 * [CanvasScaler.snap] only moves the scale the layout is drawn at.
 */
class CanvasCrispTextTest {
    private val font = UiFonts.default()

    /** A text element at [size] sp, one per size, each its own row, saying `HHHH`. */
    private fun rows(sizes: List<Float>): List<CanvasItem> = sizes.mapIndexed { index, size ->
        CanvasItem(
            index + 1,
            CanvasElement().apply {
                kind = CanvasElementKind.Text
                text = "HHHH"
                fontSize = size
                offsetX = 10f
                offsetY = 6f + index * 30f
                width = 120f
                height = 28f
            },
        )
    }

    private fun draw(width: Int, height: Int, scaler: CanvasScaler, sizes: List<Float>): Pair<List<UiDrawPrimitive.Glyph>, List<SemanticsNode>> {
        val out = ComposeHost().frame(FrameInput(width, height)) { CanvasOverlay(rows(sizes), scaler = scaler) }
        return out.primitives.filterIsInstance<UiDrawPrimitive.Glyph>() to out.semantics
    }

    private fun UiDrawPrimitive.Glyph.onWholePixels(): Boolean =
        listOf(x, y, w, h).all { it == floor(it) }

    @Test
    fun textAt1p4125DrawsFromWholePixelGlyphs() {
        // 1808 by 1017 dp against the default 1280 by 720 reference: 1.4125 by width and by height.
        val sizes = listOf(9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f, 17f)
        val (glyphs, _) = draw(1808, 1017, CanvasScaler(), sizes)

        assertEquals(sizes.size * 4, glyphs.size)
        for ((index, size) in sizes.withIndex()) {
            val drawn = glyphs.subList(index * 4, index * 4 + 4)
            val px = (size * 1.4125f).roundToInt()
            val pixelGlyphs = assertNotNull(font.pixelGlyphs)
            val expected = assertNotNull(pixelGlyphs.glyph('H'.code, FontWeight.Normal, px), "no $px px glyph")
            // Each H is drawn as the subpixel variant nearest its pen, and a variant can be a pixel wider.
            val variantSizes = (0 until expected.subpixelSteps).mapNotNull { step ->
                pixelGlyphs.glyph('H'.code, FontWeight.Normal, px, step)?.let { it.width.toFloat() to it.height.toFloat() }
            }.toSet()

            for (glyph in drawn) {
                assertEquals(UiFontSamplingMode.CoverageAlpha, glyph.font?.samplingMode, "${size}sp is $px px: a pixel glyph")
                assertTrue(glyph.onWholePixels(), "${size}sp glyph at ${glyph.x}, ${glyph.y} ${glyph.w}x${glyph.h}")
                assertTrue(glyph.w to glyph.h in variantSizes, "${size}sp is drawn at $px px: ${glyph.w}x${glyph.h} against $variantSizes")
            }
        }
    }

    @Test
    fun textAtOtherFractionalScalesDrawsFromWholePixelGlyphs() {
        val sizes = listOf(9f, 10f, 11f, 12f, 13f)
        // 1.25, 1.5 and 1.7778 (1.775 here: a whole 2272 by 1278 window) of the default reference.
        for ((width, height, scale) in listOf(Triple(1600, 900, 1.25f), Triple(1920, 1080, 1.5f), Triple(2272, 1278, 1.775f))) {
            val (glyphs, _) = draw(width, height, CanvasScaler(), sizes)

            assertEquals(sizes.size * 4, glyphs.size, "at $scale")
            for (glyph in glyphs) {
                assertEquals(UiFontSamplingMode.CoverageAlpha, glyph.font?.samplingMode, "at $scale")
                assertTrue(glyph.onWholePixels(), "at $scale: ${glyph.x}, ${glyph.y} ${glyph.w}x${glyph.h}")
            }
        }
    }

    @Test
    fun oddSizesAtOnePointFiveRoundToAWholePixelSize() {
        // 13 * 1.5 is 19.5: the em is rounded up to 20 device pixels rather than drawn blurred at 19.5.
        val (glyphs, _) = draw(1920, 1080, CanvasScaler(), listOf(13f))
        val expected = assertNotNull(font.pixelGlyphs?.glyph('H'.code, FontWeight.Normal, 20))

        assertTrue(glyphs.all { it.font?.samplingMode == UiFontSamplingMode.CoverageAlpha })
        assertEquals(expected.height.toFloat(), glyphs.first().h)
    }

    @Test
    fun textPastThePixelGlyphSizeDrawsFromTheDistanceFieldAtAnyScale() {
        // 18sp at 1.4125 is 25 px, past the 24 px the default font draws as pixel glyphs: a size limit,
        // not a fractional scale. At 1 it is 18 px and a pixel glyph.
        val (scaled, _) = draw(1808, 1017, CanvasScaler(), listOf(18f))
        val (plain, _) = draw(1280, 720, CanvasScaler(), listOf(18f))

        assertTrue(scaled.all { it.font?.samplingMode == UiFontSamplingMode.DistanceField })
        assertTrue(plain.all { it.font?.samplingMode == UiFontSamplingMode.CoverageAlpha })
    }

    @Test
    fun snappingMovesTheLayoutToTheSnappedScale() {
        val sizes = listOf(12f)
        val (offGlyphs, off) = draw(1808, 1017, CanvasScaler(), sizes)
        val (halfGlyphs, half) = draw(1808, 1017, CanvasScaler(snap = CanvasScaleSnap.HalfStep), sizes)
        val (wholeGlyphs, whole) = draw(1808, 1017, CanvasScaler(snap = CanvasScaleSnap.WholeStep), sizes)

        // The element is 120 units wide: 169.5 px at 1.4125, 180 at 1.5 and 120 at 1.
        assertEquals(170, assertNotNull(off.findTag("canvas-element-1")).width)
        assertEquals(180, assertNotNull(half.findTag("canvas-element-1")).width)
        assertEquals(120, assertNotNull(whole.findTag("canvas-element-1")).width)
        // 12sp: 16.95 px rounds to 17, and snapped it is exactly 18 at 1.5 and 12 at 1.
        for ((glyphs, px) in listOf(offGlyphs to 17, halfGlyphs to 18, wholeGlyphs to 12)) {
            val expected = assertNotNull(font.pixelGlyphs?.glyph('H'.code, FontWeight.Normal, px))
            assertTrue(glyphs.all { it.font?.samplingMode == UiFontSamplingMode.CoverageAlpha && it.h == expected.height.toFloat() }, "$px px")
        }
    }

    @Test
    fun snappedLayoutStaysWithinHalfAStepOfTheExactOne() {
        // The unsnapped box is 120 * 1.4125 = 169.5 px; HalfStep may move it by 0.25 / 1.4125 = 17.7%
        // at most, and at this scale it moves it by 6.2%, plus a pixel for rounding to whole pixels.
        val exact = 120f * 1.4125f
        for ((snap, scale) in listOf(CanvasScaleSnap.HalfStep to 1.5f, CanvasScaleSnap.WholeStep to 1f)) {
            val (_, nodes) = draw(1808, 1017, CanvasScaler(snap = snap), listOf(12f))
            val width = assertNotNull(nodes.findTag("canvas-element-1")).width

            assertEquals((120f * scale).roundToInt(), width)
            assertTrue(abs(width - exact) / exact <= snap.step / 2f / 1.4125f + 1f / exact, "$snap: $width px against $exact")
        }
    }

    @Test
    fun withTheOptionOffTheScaleAndTheTextAreAsBefore() {
        val (_, plain) = draw(1808, 1017, CanvasScaler(snap = CanvasScaleSnap.None), listOf(12f))
        val (_, defaults) = draw(1808, 1017, CanvasScaler(), listOf(12f))

        assertEquals(170, assertNotNull(plain.findTag("canvas-element-1")).width)
        assertEquals(plain.findTag("canvas-element-1")?.width, defaults.findTag("canvas-element-1")?.width)
    }
}

private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }
