/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.math2d.Rectangle
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Where a centred element lands in whole pixels, as [CanvasAnchor] states it: its pixel size plus
 * twice its offset, each rounded to whole pixels, is centred in its box with the spare room halved
 * and rounded down, so an odd pixel left over goes after it, on the right or at the bottom.
 */
class CanvasCentringTest {

    @Test
    fun anEvenSpareRoomCentresTheElementExactly() {
        val element = centred(width = 50f, height = 20f)

        draw(element, width = 100, height = 60)

        assertEquals(Rectangle(25f, 20f, 50f, 20f), element.screenBounds)
    }

    @Test
    fun anOddPixelOfSpareRoomGoesOnTheRightAndAtTheBottom() {
        val element = centred(width = 50f, height = 20f)

        // 51 pixels to spare across and 41 down: 25 before and 26 after, 20 above and 21 below.
        draw(element, width = 101, height = 61)

        assertEquals(Rectangle(25f, 20f, 50f, 20f), element.screenBounds, "half a pixel left of and above the exact centre")
    }

    @Test
    fun anOffsetIsAddedTwiceOverBeforeTheSpareRoomIsHalved() {
        val right = centred(width = 50f, height = 20f).apply { offsetX = 3f; offsetY = 2f }
        val left = centred(width = 50f, height = 20f).apply { offsetX = -3f; offsetY = -2f }

        // A box 56 by 24 with the element at its far end: 45 and 37 to spare, so 22 and 18 before it.
        draw(right, width = 101, height = 61)
        draw(left, width = 101, height = 61)

        assertEquals(Rectangle(28f, 22f, 50f, 20f), right.screenBounds)
        assertEquals(Rectangle(22f, 18f, 50f, 20f), left.screenBounds)
    }

    @Test
    fun atAScaleSizesAndOffsetsRoundToWholePixelsFirst() {
        val element = centred(width = 33f, height = 21f).apply { offsetX = 0.5f }

        // At 1.5 the element is 49.5, so 50, pixels wide, and its offset pads 1.5, so 2, pixels:
        // 101 - 52 = 49 to spare, 24 before, then the padding. 31.5, so 32, tall: 61 - 32 = 29, 14 above.
        draw(element, width = 101, height = 61, scale = 1.5f)

        val bounds = assertNotNull(element.screenBounds)
        // Back to whole pixels from canvas units, which dividing by 1.5 leaves a float's rounding off.
        val pixels = listOf(bounds.x, bounds.y, bounds.width, bounds.height).map { round(it * 1.5f) }
        assertEquals(listOf(26f, 14f, 50f, 32f), pixels)
    }

    @Test
    fun insideAParentItIsCentredInTheParentsWholePixels() {
        val window = CanvasElement().apply { kind = CanvasElementKind.Panel; offsetX = 10f; offsetY = 10f; width = 41f; height = 31f }
        val label = centred(width = 20f, height = 10f)

        ComposeHost().frame(FrameInput(200, 100)) { CanvasOverlay(listOf(CanvasItem(1, window), CanvasItem(2, label, window))) }

        // 21 and 21 to spare: 10 before, 11 after.
        assertEquals(Rectangle(20f, 20f, 20f, 10f), label.screenBounds)
    }

    @Test
    fun theTopCentreAndCentreLeftAnchorsSplitTheirCentredAxisTheSameWay() {
        val top = centred(width = 50f, height = 20f).apply { anchor = CanvasAnchor.TopCenter; offsetY = 0f }
        val side = centred(width = 50f, height = 20f).apply { anchor = CanvasAnchor.CenterLeft; offsetX = 0f }

        draw(top, width = 101, height = 61)
        draw(side, width = 101, height = 61)

        assertEquals(Rectangle(25f, 0f, 50f, 20f), top.screenBounds)
        assertEquals(Rectangle(0f, 20f, 50f, 20f), side.screenBounds)
    }

    private fun centred(width: Float, height: Float) = CanvasElement().apply {
        kind = CanvasElementKind.Panel
        anchor = CanvasAnchor.Center
        offsetX = 0f
        offsetY = 0f
        this.width = width
        this.height = height
    }

    private fun draw(element: CanvasElement, width: Int, height: Int, scale: Float = 1f) {
        ComposeHost().frame(FrameInput(width, height)) { CanvasOverlay(listOf(CanvasItem(1, element)), scale = scale) }
    }
}
