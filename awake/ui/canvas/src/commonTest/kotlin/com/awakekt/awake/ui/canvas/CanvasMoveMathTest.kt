/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The arithmetic behind a [CanvasInput.moveHandle]: the threshold, the anchor offsets and the clamp. */
class CanvasMoveMathTest {
    @Test
    fun aMoveStartsOnlyOnceThePointerHasTravelledTheDragThreshold() {
        val move = CanvasMove()
        move.begin(0L, CanvasElement(), Rectangle(0f, 0f, 100f, 100f))

        assertFalse(move.travel(CANVAS_DRAG_THRESHOLD_PX - 1, 0), "under the threshold it is still a click")
        assertTrue(move.travel(1, 0), "at the threshold it is a move")
        assertTrue(move.travel(-CANVAS_DRAG_THRESHOLD_PX, 0), "and stays one, wherever the pointer goes")
        assertEquals(0f, move.travelX(1f))
    }

    @Test
    fun theThresholdCountsEitherAxisAlone() {
        val move = CanvasMove()
        move.begin(0L, CanvasElement(), Rectangle(0f, 0f, 1f, 1f))

        assertTrue(move.travel(0, -CANVAS_DRAG_THRESHOLD_PX))
    }

    @Test
    fun onlyThePointerThatPressedHoldsTheMove() {
        val move = CanvasMove()
        move.begin(1L, CanvasElement(), Rectangle(0f, 0f, 1f, 1f))

        assertTrue(move.holds(1L))
        assertFalse(move.holds(2L), "a second finger is left alone")
        assertFalse(move.accepts(2L), "and cannot take the window over")
        move.end()
        assertTrue(move.accepts(2L))
    }

    @Test
    fun anEdgeOffsetMovesInwardAndAFarEdgeOneTheOtherWay() {
        assertEquals(30f, movedOffset(edge = 0, offset = 10f, start = 10f, target = 30f, ratio = 1f))
        assertEquals(0f, movedOffset(edge = 2, offset = 20f, start = 80f, target = 100f, ratio = 1f), "right 20 from a right edge is 20 less")
        assertEquals(-5f, movedOffset(edge = 1, offset = 5f, start = 50f, target = 40f, ratio = 1f), "a centred offset goes either way")
    }

    @Test
    fun anEdgeOffsetHeldAtTheEdgeMovesFromTheEdgeAndNeverPastIt() {
        // A negative edge offset draws at the edge, so the move starts there, not from -40.
        assertEquals(10f, movedOffset(edge = 0, offset = -40f, start = 0f, target = 10f, ratio = 1f))
        assertEquals(0f, movedOffset(edge = 0, offset = 10f, start = 10f, target = -50f, ratio = 1f))
    }

    @Test
    fun theOffsetIsInTheWindowsOwnDp() {
        // Two canvas units per window dp: 40 units is 20 dp.
        assertEquals(20f, movedOffset(edge = 0, offset = 0f, start = 0f, target = 40f, ratio = 2f))
    }

    @Test
    fun theStripStaysOnTheCanvasOnEverySide() {
        // A 200 by 150 window on an 800 by 600 canvas, keeping 30 units.
        assertEquals(800f - 30f, keepOnCanvasX(900f, 200f, 800f, 30f), "its left 30 stays at the right")
        assertEquals(30f - 200f, keepOnCanvasX(-500f, 200f, 800f, 30f), "its right 30 stays at the left")
        assertEquals(0f, keepOnCanvasY(-20f, 150f, 600f, 30f), "its title bar never goes above the top")
        assertEquals(600f - 30f, keepOnCanvasY(700f, 150f, 600f, 30f), "its top 30 stays at the bottom")
        assertEquals(123f, keepOnCanvasX(123f, 200f, 800f, 30f), "inside, nothing changes")
    }

    @Test
    fun aWindowLargerThanTheCanvasKeepsItsTopLeft() {
        assertEquals(0f, keepOnCanvasY(50f, 900f, 600f, 900f))
        assertEquals(0f, keepOnCanvasX(-900f, 1000f, 800f, 2000f), "wider than the canvas, its left edge shows")
    }

    @Test
    fun placingAWindowMovesItsAnchorOffsetByTheClampedDelta() {
        val window = CanvasElement().apply { anchor = CanvasAnchor.TopRight; offsetX = 16f; offsetY = 16f; width = 200f; height = 100f }
        val box = Rectangle(584f, 16f, 200f, 100f)

        placeWindow(window, 16f, 16f, box, dx = -100f, dy = 50f, canvasWidth = 800f, canvasHeight = 600f, strip = 24f, ratio = 1f)
        assertEquals(116f, window.offsetX, "100 left of a right anchor is 100 more inward")
        assertEquals(66f, window.offsetY)

        placeWindow(window, 16f, 16f, box, dx = 0f, dy = -500f, canvasWidth = 800f, canvasHeight = 600f, strip = 24f, ratio = 1f)
        assertEquals(0f, window.offsetY, "held at the top")
    }

    @Test
    fun aRootBroughtToTheFrontDrawsLastOfItsOrderButUnderAHigherOrder() {
        val first = CanvasElement()
        val second = CanvasElement()
        val hud = CanvasElement().apply { order = 5 }
        val items = listOf(CanvasItem(1, first), CanvasItem(2, second), CanvasItem(3, hud))

        first.bringToFront()

        assertEquals(listOf(second, first, hud), canvasDrawOrder(items).map { it.element })
        second.bringToFront()
        assertEquals(listOf(first, second, hud), canvasDrawOrder(items).map { it.element })
    }
}
