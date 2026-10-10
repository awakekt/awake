/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals

/** A browser pointer lands on the buffer pixel under it, whatever the page's CSS makes the canvas. */
class CanvasBoxTest {

    /**
     * A phone with its toolbar showing: the page sizes the canvas `100vh`, 800 CSS pixels tall, while
     * the window is 700. Measured from the window, the buffer was 700 rows stretched over 800, and a
     * touch 750 pixels down landed below the buffer.
     */
    @Test
    fun theBufferIsSizedFromTheCanvasNotTheWindow() {
        val canvas = CanvasBox(left = 0.0, top = 0.0, width = 400.0, height = 800.0)

        assertEquals(800 to 1600, canvas.bufferSize(density = 2.0))
        assertEquals(400f to 1500f, canvas.bufferPoint(200.0, 750.0, bufferWidth = 800, bufferHeight = 1600))
    }

    @Test
    fun aPointIsMeasuredFromTheCanvasCorner() {
        val canvas = CanvasBox(left = 10.0, top = 20.0, width = 300.0, height = 200.0)

        assertEquals(0f to 0f, canvas.bufferPoint(10.0, 20.0, bufferWidth = 600, bufferHeight = 400))
        assertEquals(600f to 400f, canvas.bufferPoint(310.0, 220.0, bufferWidth = 600, bufferHeight = 400))
    }

    /** Between a CSS change and the next resize, the buffer still lines up with the box. */
    @Test
    fun aBufferThatNoLongerMatchesTheBoxStillLinesUp() {
        val canvas = CanvasBox(left = 0.0, top = 0.0, width = 400.0, height = 800.0)

        assertEquals(400f to 700f, canvas.bufferPoint(200.0, 400.0, bufferWidth = 800, bufferHeight = 1400))
    }

    @Test
    fun aCanvasThatIsNotDisplayedTakesAOnePixelBufferAndNoPoints() {
        val hidden = CanvasBox(left = 0.0, top = 0.0, width = 0.0, height = 0.0)

        assertEquals(1 to 1, hidden.bufferSize(density = 3.0))
        assertEquals(0f to 0f, hidden.bufferPoint(50.0, 50.0, bufferWidth = 1, bufferHeight = 1))
    }
}
