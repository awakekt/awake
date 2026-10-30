/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextOutline
import com.awakekt.awake.core.text.theme.TextShadow
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.render.capture.PixelMap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Outlines and soft shadows from the effect field (#1061), on the CPU raster that is the GPU shader's twin: a 1, 2
 * and 4 dp outline at 12, 24 and 48 px runs unbroken around `O`, `W` and `8`, a soft shadow fades out across its
 * radius, and outlined, shadowed text is one primitive and one staged quad a glyph.
 */
class TextEffectsRasterTest {
    @Test
    fun anOutlineRunsUnbrokenAroundEveryGlyphAtEverySizeAndWidth() {
        val failures = buildList {
            for (size in SIZES) {
                for (width in WIDTHS) {
                    for (glyph in "OW8") {
                        val (primitives, map) = frame(glyph.toString(), size, TextStyle(color = Color.White, outline = TextOutline(Color.Black, width)))
                        val quad = primitives.filterIsInstance<UiDrawPrimitive.Glyph>().last()
                        val grow = (width + 2f).toInt()
                        val ring = map.outlineRing(
                            (quad.x - grow).toInt(),
                            (quad.y - grow).toInt(),
                            (quad.x + quad.w + grow).toInt(),
                            (quad.y + quad.h + grow).toInt(),
                        )
                        // The fill's own antialiased edge covers up to about two thirds of a pixel of the ring's inside.
                        val thinEnough = ring.thinnest >= maxOf(width - SCALLOP_PX, MIN_RING_PX)
                        if (ring.rays < RAYS_MET || ring.gaps > 0 || !thinEnough) add("'$glyph' at $size px, $width dp: $ring")
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /**
     * A black shadow with no offset under white text on white: outside the stem of an `I` its darkness at each pixel is
     * the effect field's smoothstep across the blur either side of the glyph's edge, half at the edge and gone a blur
     * past it, to within 0.06.
     */
    @Test
    fun aSoftShadowFadesOutAcrossItsRadius() {
        for (blur in listOf(2f, 4f, 8f)) {
            val (plainPrimitives, plain) = frame("I", 48f, TextStyle(color = Color.Black), ground = Color.White)
            val (_, shadowed) = frame("I", 48f, TextStyle(color = Color.White, shadow = TextShadow(Color.Black, 0f, 0f, blur)), ground = Color.White)
            val quad = plainPrimitives.filterIsInstance<UiDrawPrimitive.Glyph>().single()
            val row = (quad.y + quad.h / 2f).toInt()
            // The stem's right edge: where the plain glyph's darkness falls through one half, between pixel centres.
            val column = (quad.x.toInt()..(quad.x + quad.w).toInt()).first { x ->
                darkness(plain, x, row) >= 0.5f && darkness(plain, x + 1, row) < 0.5f
            }
            val inside = darkness(plain, column, row)
            val edge = column + 0.5f + (inside - 0.5f) / (inside - darkness(plain, column + 1, row))
            // Pixel centres clear of the fill's own antialiased edge, out to past the radius.
            for (column in (edge + 1f).toInt()..(edge + blur + 2f).toInt()) {
                val distance = column + 0.5f - edge
                val expected = smoothstep(-blur, blur, -distance)
                val measured = darkness(shadowed, column, row)
                assertTrue(abs(measured - expected) < 0.06f, "blur $blur, $distance px out: $measured, not $expected")
            }
        }
    }

    /**
     * A decorated glyph moving 0.1 px a frame moves its ink by about as much each frame, never stalling or jumping as a
     * column at its rect's edge comes or goes. Measured on "Harbor" at 14 px: steps 0.039 to 0.159 px. Before the fill
     * handed over to the effect field across its rect's last half texel, the clamped atlas sample repeated one coverage
     * there and the rect then cut it off: steps fell to 0.004 and jumped to 0.331 px.
     */
    @Test
    fun aMovingGlyphLosesNoColumnAtItsRectsEdge() {
        val style = TextStyle(
            color = INK,
            size = 14.sp,
            outline = TextOutline(Color(0.32f, 0.40f, 0.58f, 1f), 1f),
            shadow = TextShadow(Color(0.20f, 0.24f, 0.36f, 0.9f), 1f, 1f),
        )
        val primitives = ComposeHost(density = 1f).frame(FrameInput(FRAME, FRAME)) { Text("Harbor", style = style) }.primitives
        val glyphs = primitives.filterIsInstance<UiDrawPrimitive.Glyph>().filter { it.decoration != null && it.w > 0f }
        assertTrue(glyphs.isNotEmpty(), "decorated glyphs drawn")
        for (glyph in glyphs) {
            val centroids = (0..10).map { frame ->
                val moved = glyph.copy(x = glyph.x + frame * 0.1f)
                inkCentroidX(listOf(moved).rasterizeToPixelMap(FRAME, FRAME, background = DARK, font = UiFonts.default()))
            }
            for (i in 1 until centroids.size) {
                val step = centroids[i] - centroids[i - 1]
                assertTrue(step in 0.03..0.19, "glyph at x=${glyph.x}, frame $i: centroid step $step px (${centroids.map { "%.3f".format(it) }})")
            }
        }
    }

    /** Outlined, shadowed text is one glyph primitive a character; `UiTextEffectsParityTest` counts its staged quads. */
    @Test
    fun outlinedShadowedTextDrawsOnceAGlyph() {
        val label = "Harbor Town"
        val style = TextStyle(color = Color.White, outline = TextOutline(Color.Black, 2f), shadow = TextShadow(Color.Black, 1f, 1f, 2f))
        val (primitives, _) = frame(label, 24f, style, width = 320)
        val glyphs = primitives.filterIsInstance<UiDrawPrimitive.Glyph>()
        assertEquals(label.length, glyphs.size, "one primitive a glyph, its outline and shadow included")
        assertTrue(glyphs.all { it.decoration != null || it.w == 0f }, "every inked glyph decorated")
    }

    private fun frame(
        text: String,
        size: Float,
        style: TextStyle,
        ground: Color = GREY,
        width: Int = FRAME,
    ): Pair<List<UiDrawPrimitive>, PixelMap> {
        val primitives = ComposeHost().frame(FrameInput(width, FRAME)) {
            Box(Modifier.size(width.dp, FRAME.dp).background(ground)) {
                Text(text, Modifier.padding(PADDING.dp), style = style.copy(size = size.sp))
            }
        }.primitives
        return primitives to primitives.rasterizeToPixelMap(width, FRAME, background = ground, font = UiFonts.default())
    }

    private fun smoothstep(low: Float, high: Float, x: Float): Float {
        val t = ((x - low) / (high - low)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** The x of the luma-weighted centroid of everything lighter than [DARK]. */
    private fun inkCentroidX(map: PixelMap): Double {
        var mass = 0.0
        var weighted = 0.0
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val p = map.sample(x, y)
                val ink = 0.2126 * p.r / 255.0 + 0.7152 * p.g / 255.0 + 0.0722 * p.b / 255.0 - DARK_LUMA
                if (ink > 1e-4) {
                    mass += ink
                    weighted += (x + 0.5) * ink
                }
            }
        }
        return weighted / mass
    }

    /** How dark ([x], [y]) is on a white ground, 0 to 1, from its red channel. */
    private fun darkness(map: PixelMap, x: Int, y: Int): Float = 1f - map.sample(x, y).r / 255f

    private companion object {
        val SIZES = listOf(12f, 24f, 48f)
        val WIDTHS = listOf(1f, 2f, 4f)
        val GREY = Color(0.5f, 0.5f, 0.5f, 1f)
        val DARK = Color(0.08f, 0.08f, 0.10f, 1f)
        val INK = Color(0.96f, 0.96f, 0.97f, 1f)
        const val DARK_LUMA = 0.0807
        const val FRAME = 96
        const val PADDING = 16

        /** Of the 180 rays cast, how many must reach a glyph's fill for its ring to count as measured. */
        const val RAYS_MET = 120

        /**
         * How much thinner than its width an outline may read on any ray before it counts as scalloped. Measured: the
         * thinnest ray reads 0.4 to 0.9 px under the width, the fill's antialiased edge over the ring's inside, and a
         * ring stamped in eight steps reads more than a pixel under at 4 dp.
         */
        const val SCALLOP_PX = 0.95f

        /** The least a 1 dp outline may read on any ray: about a third of a pixel shows past the fill's edge. */
        const val MIN_RING_PX = 0.25f
    }
}
