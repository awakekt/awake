/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [canvasDrawOrder] lists items as [CanvasOverlay] draws them, and [canvasItemAt] finds the one
 * drawn on top at a point, so a host that picks from the canvas needs no copy of either rule.
 */
class CanvasDrawOrderTest {

    private fun item(id: Int, parent: CanvasItem? = null, configure: CanvasElement.() -> Unit = {}) =
        CanvasItem(id, CanvasElement().apply(configure), parent?.element)

    private fun ids(items: List<CanvasItem>): List<Int> = canvasDrawOrder(items).map { it.id }

    @Test
    fun anElementAndEverythingInsideItDrawBeforeTheNextRoot() {
        val window = item(1)
        val title = item(2, window)
        val icon = item(3, title)
        val close = item(4, window)
        val hud = item(5)

        assertEquals(listOf(1, 2, 3, 4, 5), ids(listOf(hud, icon, close, title, window).sortedBy { it.id }))
        assertEquals(listOf(5, 1, 4, 2, 3), ids(listOf(hud, window, close, title, icon)), "siblings as the list has them")
    }

    @Test
    fun aLowerOrderDrawsFirstAmongRootsAndAmongOneElementsChildren() {
        val window = item(1) { order = 2 }
        val back = item(2, window) { order = 1 }
        val front = item(3, window)
        val hud = item(4)

        assertEquals(listOf(4, 1, 3, 2), ids(listOf(window, back, front, hud)))
    }

    @Test
    fun aFollowerIsARootWhateverItsParent() {
        val window = item(1)
        val nameplate = item(2, window) { follow = "Hero" }
        val title = item(3, window)

        assertEquals(listOf(1, 3, 2), ids(listOf(window, nameplate, title)))
    }

    @Test
    fun anItemWhoseParentIsNotListedIsLeftOutAsItIsNotDrawn() {
        val window = item(1)
        val title = item(2, window)
        val icon = item(3, title)

        assertEquals(listOf(1), ids(listOf(window, icon)), "its parent's own parent is listed, but not its parent")
        assertEquals(emptyList(), ids(listOf(title, icon)))
    }

    @Test
    fun theItemAtAPointIsTheOneDrawnOnTopThere() {
        val back = panel(1, x = 0f, color = "#FF0000")
        val front = panel(2, x = 30f, color = "#00FF00")
        val inBack = panel(3, x = 10f, color = "#0000FF", parent = back, width = 20f)
        val items = listOf(back, front, inBack)

        val pixels = draw(items)

        // Every pixel of the frame is the colour of the item found there, or the background where none is.
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val found = canvasItemAt(items, x + 0.5f, y + 0.5f)
                assertEquals(found?.element?.background?.let(::rgb) ?: 0, pixelAt(pixels, x, y), "at ($x, $y), found ${found?.id}")
            }
        }
        assertEquals(2, canvasItemAt(items, 40f, 20f)?.id, "a later root over an earlier one")
        assertEquals(3, canvasItemAt(items, 15f, 20f)?.id, "a child over its parent")
        assertNull(canvasItemAt(items, 100f, 70f))
    }

    @Test
    fun anElementThatDoesNotTakeTapsIsStillTheOneOnTop() {
        val button = panel(1, x = 0f, color = "#FF0000").also { it.element.kind = CanvasElementKind.Button }
        val label = panel(2, x = 0f, color = "#00FF00")
        val items = listOf(button, label)
        draw(items)

        assertEquals(2, canvasItemAt(items, 10f, 10f)?.id)
    }

    @Test
    fun anElementNotDrawnIsNotFoundAndWhatWasUnderItIs() {
        val back = panel(1, x = 0f, color = "#FF0000")
        val front = panel(2, x = 0f, color = "#00FF00").also { it.element.visible = false }
        val inFront = panel(3, x = 0f, color = "#0000FF", parent = front, width = 10f)
        val items = listOf(back, front, inFront)
        draw(items)

        assertEquals(1, canvasItemAt(items, 5f, 5f)?.id, "neither the hidden element nor what is inside it")
    }

    private fun panel(id: Int, x: Float, color: String, parent: CanvasItem? = null, width: Float = 50f) = item(id, parent) {
        kind = CanvasElementKind.Panel
        offsetX = x
        offsetY = 0f
        this.width = width
        height = 40f
        background = color
    }

    private fun draw(items: List<CanvasItem>): ByteArray =
        ComposeHost().frame(FrameInput(WIDTH, HEIGHT)) { CanvasOverlay(items) }.primitives.rasterize(WIDTH, HEIGHT, Color.Black)

    private fun pixelAt(pixels: ByteArray, x: Int, y: Int): Int {
        val offset = (y * WIDTH + x) * 4
        return ((pixels[offset].toInt() and 0xFF) shl 16) or ((pixels[offset + 1].toInt() and 0xFF) shl 8) or (pixels[offset + 2].toInt() and 0xFF)
    }

    private fun rgb(hex: String): Int = hex.removePrefix("#").toInt(16)

    private companion object {
        const val WIDTH = 120
        const val HEIGHT = 80
    }
}
