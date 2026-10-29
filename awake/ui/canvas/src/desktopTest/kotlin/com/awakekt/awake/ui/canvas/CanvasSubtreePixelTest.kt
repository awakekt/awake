/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.testing.rasterizeToPixelMap
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFontSamplingMode
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pixel-level verification for subtree-scoped [CanvasScaler] combined with [CanvasScaleSnap] and
 * rounded/cut clipping alongside an unscaled `1:1` sibling.
 */
class CanvasSubtreePixelTest {

    @Test
    fun snappedPixelArtSubtreeDrawsCleanMultiplesBesideAOneToOneSibling() {
        // 226 wide against a 160 reference is 1.4125; HalfStep rounds the subtree to 1.5 while the sibling stays 1:1.
        val unsnapped = renderMixedArt(CanvasScaleSnap.None)
        val snapped = renderMixedArt(CanvasScaleSnap.HalfStep)

        assertTrue(unsnapped.pixelRunsOf(SUBTREE_ART_ID).size > 1, "at 1.4125 without snap, art pixel widths are uneven")
        assertEquals(setOf(4), unsnapped.pixelRunsOf(SIBLING_ART_ID), "the 1:1 sibling stays 4 px per art pixel")

        assertEquals(setOf(6), snapped.pixelRunsOf(SUBTREE_ART_ID), "HalfStep snaps the subtree to 1.5x: 6 px per art pixel")
        assertEquals(setOf(4), snapped.pixelRunsOf(SIBLING_ART_ID), "the 1:1 sibling is still 4 px per art pixel")

        val glyphs = snapped.out.primitives.filterIsInstance<UiDrawPrimitive.Glyph>()
        assertTrue(glyphs.isNotEmpty())
        for (glyph in glyphs) {
            assertEquals(UiFontSamplingMode.CoverageAlpha, glyph.font?.samplingMode)
            assertTrue(listOf(glyph.x, glyph.y, glyph.w, glyph.h).all { it == floor(it) })
        }
    }

    @Test
    fun clippingInsideAScaledSubtreeUsesItsScaledBox() {
        val scaler = CanvasScaler(referenceWidth = 160f, referenceHeight = 90f, match = 0f, scope = CanvasScaleScope.Subtree) // 1.5x in 240x135
        val panel = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            offsetX = 10f; offsetY = 10f; width = 80f; height = 40f // 120x60 at (15, 15) px
        }
        // Cut-mode fill image at 50% clips at half of the 1.5x scaled bar width (60 px of 120 px, not 40 px).
        val bar = CanvasElement().apply {
            kind = CanvasElementKind.Bar
            offsetX = 0f; offsetY = 0f; width = 80f; height = 20f
            value = 0.5f
            style = CanvasStyle(fillImage = CanvasImage("solid.png", pixelated = true), fillImageMode = CanvasFillImageMode.Cut)
        }
        val items = listOf(
            CanvasItem(1, panel, scaler = scaler),
            CanvasItem(2, bar, parent = panel),
        )

        val out = ComposeHost().frame(FrameInput(240, 135)) {
            CanvasOverlay(items, images = mapOf("solid.png" to SOLID_RED))
        }
        val map = out.primitives.rasterizeToPixelMap(240, 135, background = Color.Transparent)

        // Bar sits at [15..135, 15..45); 50% fill covers [15..75) — pixel 74 (well past unscaled 15+40=55) is filled, pixel 75 is clipped out.
        assertTrue(map.sample(74, 25).r > 200, "scaled 50% fill reaches x=74 (past the unscaled x=55 boundary)")
        assertEquals(0, map.sample(75, 25).a, "clipped cleanly at the scaled 50% mark (x=75)")
    }

    private class Rendered(val out: FrameOutput, val width: Int, val height: Int) {
        fun pixelRunsOf(id: Int): Set<Int> {
            val art = assertNotNull(out.semantics.findPixelTag("canvas-element-$id"))
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
    }

    private fun renderMixedArt(snap: CanvasScaleSnap): Rendered {
        val subtreeScaler = CanvasScaler(
            referenceWidth = 160f,
            referenceHeight = 90f,
            match = 0f,
            snap = snap,
            scope = CanvasScaleScope.Subtree,
        )
        val hud = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            offsetX = 4f; offsetY = 4f; width = 80f; height = 64f
        }
        val subtreeArt = CanvasElement().apply {
            kind = CanvasElementKind.Image
            offsetX = 0f; offsetY = 0f; width = 32f; height = 32f
            style = CanvasStyle(image = CanvasImage("checker.png", pixelated = true))
        }
        val subtreeLabel = CanvasElement().apply {
            kind = CanvasElementKind.Text
            text = "HP 87"; fontSize = 10f
            offsetX = 0f; offsetY = 36f; width = 72f; height = 16f
        }
        val siblingArt = CanvasElement().apply {
            kind = CanvasElementKind.Image
            anchor = CanvasAnchor.TopRight
            offsetX = 6f; offsetY = 6f; width = 32f; height = 32f
            style = CanvasStyle(image = CanvasImage("checker.png", pixelated = true))
        }
        val items = listOf(
            CanvasItem(1, hud, scaler = subtreeScaler),
            CanvasItem(SUBTREE_ART_ID, subtreeArt, parent = hud),
            CanvasItem(3, subtreeLabel, parent = hud),
            CanvasItem(SIBLING_ART_ID, siblingArt),
        )
        val out = ComposeHost().frame(FrameInput(226, 127)) {
            CanvasOverlay(items, images = mapOf("checker.png" to CHECKER))
        }
        return Rendered(out, 226, 127)
    }

    private companion object {
        const val SUBTREE_ART_ID = 2
        const val SIBLING_ART_ID = 4

        val CHECKER = ImageBitmap(
            8,
            8,
            ByteArray(8 * 8 * 4) { i ->
                val pixel = i / 4
                val dark = (pixel % 8 + pixel / 8) % 2 == 0
                val color = if (dark) Color(0.12f, 0.14f, 0.2f, 1f) else Color(0.92f, 0.78f, 0.36f, 1f)
                when (i % 4) {
                    0 -> (color.r * 255f).toInt().toByte()
                    1 -> (color.g * 255f).toInt().toByte()
                    2 -> (color.b * 255f).toInt().toByte()
                    else -> -1
                }
            },
        )

        val SOLID_RED = ImageBitmap(
            4,
            4,
            ByteArray(4 * 4 * 4) { i ->
                when (i % 4) {
                    0 -> 240.toByte()
                    1 -> 40.toByte()
                    2 -> 40.toByte()
                    else -> -1
                }
            },
        )
    }
}

private fun List<SemanticsNode>.findPixelTag(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findPixelTag(tag) }
