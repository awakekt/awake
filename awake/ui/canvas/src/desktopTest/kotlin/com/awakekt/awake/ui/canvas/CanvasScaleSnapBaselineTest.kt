/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.testing.assertMatchesBaseline
import com.awakekt.awake.compose.testing.rasterizeToPixelMap
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFontSamplingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A small HUD drawn at fractional scales, with and without [CanvasScaler.snap]: the text is crisp at
 * every one, and the pixel art is a clean multiple of itself only when the scale is snapped.
 *
 * The HUD is designed at 160 by 90 units, so a window 226 wide is a scale of 1.4125, the one a 1017 dp
 * tall window gives a 720 dp reference. Each case checks the numbers the picture shows: its text is
 * exact-size coverage glyphs on whole pixels, the widths of the art's pixels on screen, and how far
 * the layout moved from the unsnapped one.
 */
class CanvasScaleSnapBaselineTest {

    @Test
    fun atOnePointTwoFiveTheTextIsCrispAndThisArtIsAlreadyWhole() {
        val hud = draw(200, 113, CanvasScaleSnap.None)

        hud.assertCrispText()
        assertEquals(setOf(5), hud.artPixelWidths(), "4 units of art per pixel at 1.25 is 5 screen pixels: a scale that suits this art")
        hud.out.primitives.assertMatchesBaseline("canvas-scale-1_25-none", 200, 113)
    }

    @Test
    fun atOnePointFourOneTwoFiveTheTextIsCrispAndTheArtIsUneven() {
        val hud = draw(226, 127, CanvasScaleSnap.None)

        hud.assertCrispText()
        assertTrue(hud.artPixelWidths().size > 1, "5.65 screen pixels of art per art pixel cannot be whole: ${hud.artPixelWidths()}")
        hud.out.primitives.assertMatchesBaseline("canvas-scale-1_4125-none", 226, 127)
    }

    @Test
    fun aHalfStepDrawsTheHudAtOnePointFiveWithCleanArt() {
        val hud = draw(226, 127, CanvasScaleSnap.HalfStep)

        hud.assertCrispText()
        assertEquals(setOf(6), hud.artPixelWidths(), "4 units at 1.5")
        hud.assertLayoutWithin(draw(226, 127, CanvasScaleSnap.None), 1.5f, 1.4125f, CanvasScaleSnap.HalfStep)
        hud.out.primitives.assertMatchesBaseline("canvas-scale-1_4125-half", 226, 127)
    }

    @Test
    fun aWholeStepDrawsTheHudAtOneWithCleanArt() {
        val hud = draw(226, 127, CanvasScaleSnap.WholeStep)

        hud.assertCrispText()
        assertEquals(setOf(4), hud.artPixelWidths(), "4 units at 1")
        hud.assertLayoutWithin(draw(226, 127, CanvasScaleSnap.None), 1f, 1.4125f, CanvasScaleSnap.WholeStep)
        hud.out.primitives.assertMatchesBaseline("canvas-scale-1_4125-whole", 226, 127)
    }

    @Test
    fun atOnePointSevenSevenFiveTheTextIsCrispAndAHalfStepRoundsUpToTwo() {
        val none = draw(284, 160, CanvasScaleSnap.None)
        val half = draw(284, 160, CanvasScaleSnap.HalfStep)

        none.assertCrispText()
        half.assertCrispText()
        assertTrue(none.artPixelWidths().size > 1, none.artPixelWidths().toString())
        assertEquals(setOf(8), half.artPixelWidths(), "4 units at 2")
        half.assertLayoutWithin(none, 2f, 1.775f, CanvasScaleSnap.HalfStep)
        none.out.primitives.assertMatchesBaseline("canvas-scale-1_775-none", 284, 160)
        half.out.primitives.assertMatchesBaseline("canvas-scale-1_775-half", 284, 160)
    }

    private class Hud(val out: FrameOutput, val width: Int, val height: Int) {
        val glyphs: List<UiDrawPrimitive.Glyph> get() = out.primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

        fun node(id: Int): SemanticsNode = assertNotNull(out.semantics.findTag("canvas-element-$id"))

        /** Every glyph is an exact-size coverage glyph on whole device pixels. */
        fun assertCrispText() {
            assertTrue(glyphs.isNotEmpty())
            for (glyph in glyphs) {
                assertEquals(UiFontSamplingMode.CoverageAlpha, glyph.font?.samplingMode, "glyph at ${glyph.x}, ${glyph.y}")
                assertTrue(listOf(glyph.x, glyph.y, glyph.w, glyph.h).all { it == floor(it) }, "glyph at ${glyph.x}, ${glyph.y} ${glyph.w}x${glyph.h}")
            }
        }

        /** The widths, in screen pixels, of the runs of one colour along a row through the middle of the art. */
        fun artPixelWidths(): Set<Int> {
            val art = node(ART_ID)
            val map = out.primitives.rasterizeToPixelMap(width, height)
            val y = art.y + art.height / 2
            val colors = (art.x until art.x + art.width).map { x ->
                val sample = map.sample(x, y)
                Triple(sample.r, sample.g, sample.b)
            }
            val runs = mutableListOf<Int>()
            var length = 1
            for (i in 1 until colors.size) {
                if (colors[i] == colors[i - 1]) {
                    length++
                } else {
                    runs += length
                    length = 1
                }
            }
            runs += length
            return runs.toSet()
        }

        /**
         * Every element's box is [snapped] over [exact] times the unsnapped one, within a pixel of
         * rounding, and so within half of [snap]'s step in proportion of it.
         */
        fun assertLayoutWithin(unsnapped: Hud, snapped: Float, exact: Float, snap: CanvasScaleSnap) {
            for (id in listOf(TITLE_ID, BAR_ID, ART_ID)) {
                val here = node(id)
                val there = unsnapped.node(id)
                val ratio = here.width.toFloat() / there.width
                val expected = snapped / exact

                assertTrue(abs(ratio - expected) < 1.5f / there.width + 0.01f, "element $id is $ratio of the unsnapped box, expected $expected")
                assertTrue(abs(ratio - 1f) <= snap.step / 2f / exact + 1.5f / there.width, "element $id moved by $ratio, more than half of $snap")
            }
        }
    }

    private fun draw(width: Int, height: Int, snap: CanvasScaleSnap): Hud {
        // Designed at 160 by 90, matching the width: 226 / 160 is 1.4125.
        val scaler = CanvasScaler(referenceWidth = 160f, referenceHeight = 90f, match = 0f, snap = snap)
        val out = ComposeHost().frame(FrameInput(width, height)) {
            CanvasOverlay(items(), images = mapOf("art.png" to ART), scaler = scaler)
        }
        return Hud(out, width, height)
    }

    private fun items(): List<CanvasItem> {
        fun text(id: Int, line: String, size: Float, y: Float) = CanvasItem(
            id,
            CanvasElement().apply {
                kind = CanvasElementKind.Text; text = line; fontSize = size; color = "#F2F2F2"
                offsetX = 6f; offsetY = y; width = 100f; height = size * 1.4f
            },
        )
        return listOf(
            CanvasItem(
                PANEL_ID,
                CanvasElement().apply {
                    kind = CanvasElementKind.Panel; background = "#242B3A"; offsetX = 0f; offsetY = 0f; width = 160f; height = 90f
                },
            ),
            text(TITLE_ID, "HP 87/100", 12f, 6f),
            text(3, "Quest log", 9f, 22f),
            text(4, "Quick brown fox", 11f, 34f),
            CanvasItem(
                BAR_ID,
                CanvasElement().apply {
                    kind = CanvasElementKind.Bar; value = 0.6f; color = "#E5484D"; background = "#00000099"
                    offsetX = 6f; offsetY = 52f; width = 88f; height = 8f
                },
            ),
            CanvasItem(
                ART_ID,
                CanvasElement().apply {
                    kind = CanvasElementKind.Image; offsetX = 110f; offsetY = 6f; width = 32f; height = 32f
                    style = CanvasStyle(image = CanvasImage("art.png", pixelated = true))
                },
            ),
        )
    }

    private companion object {
        const val PANEL_ID = 1
        const val TITLE_ID = 2
        const val BAR_ID = 5
        const val ART_ID = 6

        /** 8 x 8 art in two colours, a checker so every art pixel is a run along any row. */
        val ART = ImageBitmap(
            8,
            8,
            ByteArray(8 * 8 * 4) { i ->
                val pixel = i / 4
                val dark = (pixel % 8 + pixel / 8) % 2 == 0
                val color = if (dark) Color(0.1f, 0.1f, 0.1f, 1f) else Color(0.9f, 0.75f, 0.35f, 1f)
                when (i % 4) {
                    0 -> (color.r * 255f).toInt().toByte()
                    1 -> (color.g * 255f).toInt().toByte()
                    2 -> (color.b * 255f).toInt().toByte()
                    else -> -1
                }
            },
        )
    }
}

private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }
