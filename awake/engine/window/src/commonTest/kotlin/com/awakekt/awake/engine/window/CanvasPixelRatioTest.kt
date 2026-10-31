/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals

/** A host can cap the browser canvas's pixel ratio; the buffer, the UI density and pointers follow. */
class CanvasPixelRatioTest {

    private val box = CanvasBox(left = 0.0, top = 0.0, width = 1920.0, height = 1080.0)

    @Test
    fun withNoCapTheBufferIsExactlyWhatItWas() {
        for (device in listOf(1.0, 1.25, 1.5, 2.0, 3.0)) {
            assertEquals(device, canvasPixelRatio(device, maxPixelRatio = null))
            assertEquals(box.bufferSize(device), box.bufferSize(canvasPixelRatio(device, null)))
        }
    }

    @Test
    fun aCapLowersTheRatioButNeverRaisesIt() {
        assertEquals(1.5, canvasPixelRatio(2.0, maxPixelRatio = 1.5f))
        assertEquals(1.0, canvasPixelRatio(3.0, maxPixelRatio = 1f))
        assertEquals(1.25, canvasPixelRatio(1.25, maxPixelRatio = 1.5f), "a cap above the device ratio")
        assertEquals(0.5, canvasPixelRatio(1.0, maxPixelRatio = 0.5f), "a cap below 1 is honoured")
    }

    /** A 1920x1080 CSS-pixel canvas on a 2x screen capped at 1: a quarter of the pixels. */
    @Test
    fun aCappedBufferShadesFewerPixels() {
        assertEquals(3840 to 2160, box.bufferSize(canvasPixelRatio(2.0, null)))
        assertEquals(1920 to 1080, box.bufferSize(canvasPixelRatio(2.0, 1f)))
        assertEquals(2880 to 1620, box.bufferSize(canvasPixelRatio(3.0, 1.5f)))
    }

    @Test
    fun anUnusableRatioOrCapIsIgnored() {
        assertEquals(1.0, canvasPixelRatio(Double.NaN, null))
        assertEquals(1.0, canvasPixelRatio(0.0, null))
        assertEquals(2.0, canvasPixelRatio(2.0, 0f))
        assertEquals(2.0, canvasPixelRatio(2.0, -1f))
        assertEquals(2.0, canvasPixelRatio(2.0, Float.NaN))
        assertEquals(2.0, canvasPixelRatio(2.0, Float.POSITIVE_INFINITY))
    }

    /**
     * The UI density is the capped ratio, so layout keeps its size in CSS pixels: a panel of 400 dp
     * covers 400 CSS pixels of the page at any cap.
     */
    @Test
    fun theUiKeepsItsSizeOnThePage() {
        val panelDp = 400.0
        for (cap in listOf(null, 1.5f, 1f, 0.75f)) {
            val density = canvasPixelRatio(2.0, cap)
            val (bufferWidth, _) = box.bufferSize(density)
            val panelCssPixels = panelDp * density * box.width / bufferWidth
            assertEquals(panelDp, panelCssPixels, 1e-9, "cap $cap")
        }
    }

    /** A pointer lands on the same buffer pixel the UI drew under it, at a capped ratio. */
    @Test
    fun aPointerLandsOnTheCappedBuffer() {
        val density = canvasPixelRatio(2.0, maxPixelRatio = 1.5f)
        val (width, height) = box.bufferSize(density)

        assertEquals(2880 to 1620, width to height)
        assertEquals(1500f to 750f, box.bufferPoint(1000.0, 500.0, width, height))
        // The same point in dp, as the UI lays out, is the CSS point.
        val (x, y) = box.bufferPoint(1000.0, 500.0, width, height)
        assertEquals(1000.0, x / density, 1e-6)
        assertEquals(500.0, y / density, 1e-6)
    }
}
