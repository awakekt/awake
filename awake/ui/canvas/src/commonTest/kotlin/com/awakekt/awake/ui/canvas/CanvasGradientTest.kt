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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Negative control: disable the middle-to-brush mapping. Four pixel/state tests fail: opaque
 * centres become [127, 0, 127, 255] instead of [0, 255, 0, 255], and the translucent centre
 * becomes [64, 0, 64, 255] instead of [0, 128, 0, 255]. JSON/token tests still pass.
 */
class CanvasGradientTest {
    @Test
    fun theMiddleColourReachesTheCentreOnBothAxes() {
        for (horizontal in listOf(false, true)) {
            val pixels = draw(CanvasGradient("#FF0000", "#0000FF", horizontal, middle = "#00FF00"))
            assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(20, 20), "horizontal=$horizontal")
            val start = pixels.rgbaAt(if (horizontal) 0 else 20, if (horizontal) 20 else 0)
            val end = pixels.rgbaAt(if (horizontal) 40 else 20, if (horizontal) 20 else 40)
            assertTrue(start[0] > 240 && end[2] > 240, "the endpoints remain red and blue: $start, $end")
        }
    }

    @Test
    fun aTranslucentMidpointDoesNotOverlapOrLeaveASeamOnHalfPixels() {
        for (horizontal in listOf(false, true)) {
            val pixels = draw(CanvasGradient("#FF000080", "#0000FF80", horizontal, middle = "#00FF0080"))
            val centre = pixels.rgbaAt(20, 20)
            assertEquals(listOf(0, 128, 0, 255), centre, "one half-transparent fill over black, horizontal=$horizontal")
            for (position in 18..22) {
                val rgba = pixels.rgbaAt(if (horizontal) position else 20, if (horizontal) 20 else position)
                assertTrue(rgba.take(3).sum() in 127..129, "no gap or double blend at $position: $rgba")
            }
        }
    }

    @Test
    fun theGradientKeepsItsRoundedOutline() {
        val pixels = draw(CanvasGradient("#FF0000", "#0000FF", middle = "#00FF00"), radius = 12f)
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(0, 0))
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(20, 20))
        assertTrue(pixels.rgbaAt(20, 1)[0] > 220, "the top edge between the rounded corners remains filled, one pixel inside its AA fringe")
    }

    /** Disabling the AA fringe gives zero soft boundary pixels, against 208 for the normal fill. */
    @Test
    fun aRoundedMidpointKeepsTheNormalFillsAntialiasedEdge() {
        fun softPixels(pixels: ByteArray): Int = (0 until SIZE * SIZE).count { (pixels[it * 4].toInt() and 255) in 1..249 }
        val normal = softPixels(draw(CanvasGradient("#FFFFFF", "#FFFFFF"), radius = 12f))
        val midpoint = softPixels(draw(CanvasGradient("#FFFFFF", "#FFFFFF", middle = "#FFFFFF"), radius = 12f))
        println("Rounded edge pixels: normal=$normal, midpoint=$midpoint")
        assertTrue(normal > 0, "the existing rounded fill has partial edge coverage")
        assertTrue(midpoint in normal * 3 / 4..normal * 5 / 4, "rounded edge pixels: normal=$normal, midpoint=$midpoint")
    }

    @Test
    fun theLegacyJsonKeepsItsBytesAndTheOptionalMiddleRoundTrips() {
        val legacy = """{"start":"#FF0000","end":"#0000FF"}"""
        val gradient = Json.decodeFromString<CanvasGradient>(legacy)
        assertEquals(legacy, Json.encodeToString(gradient))
        assertEquals(null, gradient.middle)
        val three = gradient.copy(middle = "#00FF0080")
        assertEquals(three, Json.decodeFromString<CanvasGradient>(Json.encodeToString(three)))
    }

    @Test
    fun aBadMiddleNamesItsFieldAndAThemeResolvesItsToken() {
        assertEquals(listOf("gradient.middle \"green\" must be #RRGGBB or #RRGGBBAA"), CanvasStyle(gradient = CanvasGradient("#FF0000", "#0000FF", middle = "green")).problems())
        val theme = CanvasControlTheme(
            preset = CanvasThemePreset.None,
            colors = mapOf("centre" to "#00FF00"),
            skin = CanvasControlSkin(button = CanvasButtonSkin(icon = CanvasSlot(style = CanvasStyle(gradient = CanvasGradient("#FF0000", "#0000FF", middle = "@centre"))))),
        )
        assertEquals("#00FF00", assertNotNull(theme.resolve(null)?.button?.icon?.style?.gradient).middle)
        assertEquals(emptyList(), theme.problems())
    }

    @Test
    fun stateOverridesAndAChangedMiddleRepaintTheSameRetainedNode() {
        val gradient = CanvasGradient("#FF0000", "#0000FF", middle = "#00FF00")
        val element = CanvasElement().apply {
            kind = CanvasElementKind.Button
            offsetX = 0f; offsetY = 0f; width = SIZE.toFloat(); height = SIZE.toFloat()
            style = CanvasStyle(
                gradient = gradient,
                hovered = CanvasStateStyle(gradient = gradient.copy(middle = "#FFFFFF")),
                pressed = CanvasStateStyle(gradient = gradient.copy(middle = "#FFFF00")),
            )
        }
        val host = ComposeHost()
        fun centre(input: FrameInput): List<Int> = host.frame(input) { CanvasOverlay(listOf(CanvasItem(1, element))) }
            .primitives.rasterize(SIZE, SIZE, Color.Black).rgbaAt(20, 20)
        assertEquals(listOf(0, 255, 0, 255), centre(FrameInput(SIZE, SIZE)))
        assertEquals(listOf(255, 255, 255, 255), centre(FrameInput(SIZE, SIZE, pointerX = 20, pointerY = 20)))
        assertEquals(listOf(255, 255, 0, 255), centre(FrameInput(SIZE, SIZE, pointerX = 20, pointerY = 20, pointerDown = true)))
        element.style = CanvasStyle(gradient = gradient.copy(middle = "#FF00FF"))
        assertEquals(listOf(255, 0, 255, 255), centre(FrameInput(SIZE, SIZE)))
    }

    private fun draw(gradient: CanvasGradient, radius: Float = 0f): ByteArray {
        val element = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            offsetX = 0f; offsetY = 0f; width = SIZE.toFloat(); height = SIZE.toFloat()
            style = CanvasStyle(gradient = gradient, cornerRadius = radius)
        }
        return ComposeHost().frame(FrameInput(SIZE, SIZE)) { CanvasOverlay(listOf(CanvasItem(1, element))) }
            .primitives.rasterize(SIZE, SIZE, Color.Black)
    }

    private fun ByteArray.rgbaAt(x: Int, y: Int): List<Int> = (0..3).map { this[(y * SIZE + x) * 4 + it].toInt() and 0xFF }

    private companion object { const val SIZE = 41 }
}
