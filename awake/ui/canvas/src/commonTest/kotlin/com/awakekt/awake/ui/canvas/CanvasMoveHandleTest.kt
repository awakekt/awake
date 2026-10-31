/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.core.math2d.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CanvasInput.moveHandle] in a live overlay: a window, a 200 by 150 panel, with a 24-unit title bar
 * along its top. Dragging the bar moves the window by the pointer's travel, a click on it stays a
 * click, and the window is kept on the canvas and brought to the front.
 */
class CanvasMoveHandleTest {
    private val items = mutableListOf<CanvasItem>()
    private val host = ComposeHost()
    private var width = WIDTH
    private var height = HEIGHT

    private fun element(kind: CanvasElementKind, parent: CanvasElement? = null, configure: CanvasElement.() -> Unit): CanvasElement =
        CanvasElement().apply { this.kind = kind }.apply(configure).also { items += CanvasItem(items.size + 1, it, parent) }

    private fun window(x: Float = 100f, y: Float = 100f): Pair<CanvasElement, CanvasElement> {
        val window = element(CanvasElementKind.Panel) { offsetX = x; offsetY = y; width = 200f; height = 150f }
        val bar = element(CanvasElementKind.Panel, window) { offsetX = 0f; offsetY = 0f; width = 200f; height = 24f; moveHandle = true }
        return window to bar
    }

    private fun frame(x: Int, y: Int, pressed: Boolean = false, down: Boolean = false, released: Boolean = false) =
        host.frame(FrameInput(width, height, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
            CanvasOverlay(items)
        }

    private fun touch(vararg pointers: PointerFrame) = host.frame(FrameInput(width, height, pointers = pointers.toList())) { CanvasOverlay(items) }

    /** Presses at ([x], [y]), moves through [path] and releases at its last point. */
    private fun drag(x: Int, y: Int, vararg path: Pair<Int, Int>) {
        frame(x, y)
        frame(x, y, pressed = true, down = true)
        for ((px, py) in path) frame(px, py, down = true)
        val (lx, ly) = path.lastOrNull() ?: (x to y)
        frame(lx, ly, released = true)
        frame(lx, ly)
    }

    @Test
    fun draggingTheTitleBarMovesTheWindowByThePointersTravel() {
        val (window, _) = window()

        drag(150, 110, 170 to 120, 250 to 180)

        assertEquals(200f, window.offsetX)
        assertEquals(170f, window.offsetY)
        assertEquals(200f, window.screenBounds?.x, "and it is drawn there")
        assertEquals(Vec2(200f, 170f), window.moved, "the window reports its new offset once")
        assertNull(window.moved)
        assertFalse(window.moving)
    }

    @Test
    fun theWindowFollowsLiveWhileTheBarIsHeld() {
        val (window, _) = window()

        frame(150, 110)
        frame(150, 110, pressed = true, down = true)
        frame(190, 110, down = true)

        assertTrue(window.moving)
        assertEquals(140f, window.offsetX)
        assertNull(window.moved, "nothing to save until the move ends")
    }

    @Test
    fun aClickOnAButtonActingAsTheHandleStillClicksAndMovesNothing() {
        val window = element(CanvasElementKind.Panel) { offsetX = 100f; offsetY = 100f; width = 200f; height = 150f }
        val bar = element(CanvasElementKind.Button, window) { offsetX = 0f; offsetY = 0f; width = 200f; height = 24f; moveHandle = true }

        drag(150, 110, 153 to 112)

        assertTrue(bar.consumePress(), "under the threshold it is a click")
        assertEquals(100f, window.offsetX)
        assertNull(window.moved)
    }

    @Test
    fun aRealMoveIsNotAlsoAClick() {
        val window = element(CanvasElementKind.Panel) { offsetX = 100f; offsetY = 100f; width = 200f; height = 150f }
        val bar = element(CanvasElementKind.Button, window) { offsetX = 0f; offsetY = 0f; width = 200f; height = 24f; moveHandle = true }

        drag(150, 110, 200 to 110)

        assertFalse(bar.consumePress())
        assertEquals(150f, window.offsetX)
    }

    @Test
    fun theTitleBarStaysOnTheCanvas() {
        val (window, _) = window()

        drag(150, 110, 150 to 2000)

        assertEquals((HEIGHT - 24).toFloat(), window.screenBounds?.y, "the bar's 24 units still show at the bottom")
        drag(150, HEIGHT - 14, 150 to -500)
        assertEquals(0f, window.screenBounds?.y, "and it never goes above the top")
    }

    @Test
    fun aShrunkenCanvasPullsAWindowBackOn() {
        val (window, _) = window(x = 700f, y = 500f)
        frame(0, 0)

        width = 400
        height = 300
        frame(0, 0)
        frame(0, 0)

        assertEquals(400f - 24f, window.screenBounds?.x, "its left 24 units show")
        assertEquals(300f - 24f, window.screenBounds?.y)
    }

    @Test
    fun aWindowOnTheCanvasKeepsItsOffsetsAsWritten() {
        val (window, _) = window(x = -40f, y = 100f) // a negative edge offset draws at the edge

        frame(0, 0)
        frame(0, 0)

        assertEquals(-40f, window.offsetX, "nothing was off the canvas, so nothing was rewritten")
    }

    @Test
    fun aTouchMovesTheWindowAndASecondFingerIsIgnored() {
        val (window, _) = window()
        touch() // lays the canvas out, so the first touch lands on the bar

        touch(PointerFrame(1, 150, 110, down = true, pressed = true))
        touch(PointerFrame(1, 200, 110, down = true), PointerFrame(2, 160, 112, down = true, pressed = true))
        touch(PointerFrame(1, 250, 160, down = true), PointerFrame(2, 600, 500, down = true))
        touch(PointerFrame(1, 250, 160, down = false, released = true), PointerFrame(2, 600, 500, down = false, released = true))

        assertEquals(Vec2(200f, 150f), Vec2(window.offsetX, window.offsetY), "the first finger's travel only")
    }

    @Test
    fun pressingAWindowBringsItToTheFront() {
        val (back, _) = window(x = 100f, y = 100f)
        val (front, _) = window(x = 150f, y = 150f)
        frame(0, 0)

        frame(120, 200, pressed = true, down = true) // on the back window's body, clear of the front one
        frame(120, 200, released = true)
        frame(120, 200)

        val order = canvasDrawOrder(items).map { it.element }
        assertTrue(order.indexOf(back) > order.indexOf(front), "the pressed window draws over the other")
    }

    @Test
    fun aHostSetOffsetRestoresASavedPosition() {
        val (window, _) = window()
        frame(0, 0)

        window.offsetX = 320f
        window.offsetY = 40f
        frame(0, 0)

        assertEquals(320f, window.screenBounds?.x)
        assertEquals(40f, window.screenBounds?.y)
    }

    @Test
    fun theChildrenKeepTheirPlaceInTheWindow() {
        val (window, bar) = window()
        val slot = element(CanvasElementKind.Panel, window) { offsetX = 10f; offsetY = 40f; width = 40f; height = 40f }

        drag(150, 110, 200 to 160)

        assertEquals(window.screenBounds!!.x + 10f, slot.screenBounds?.x)
        assertEquals(window.screenBounds!!.y + 40f, slot.screenBounds?.y)
        assertEquals(0f, bar.offsetX, "only the root's offset changes")
    }

    private companion object {
        const val WIDTH = 800
        const val HEIGHT = 600
    }
}
