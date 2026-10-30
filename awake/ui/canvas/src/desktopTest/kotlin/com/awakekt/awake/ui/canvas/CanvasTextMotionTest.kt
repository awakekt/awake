/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.testing.rasterizeToPixelMap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.render.capture.PixelMap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the shimmer-free text motion rule (#1063) for anchored HUD elements in `:awake:ui:canvas`:
 * elements (`TopLeft`, `Center`, `BottomRight`) whose `offsetX` tweens by `0.1 px/frame` glide
 * smoothly and ease back onto exact static placement when motion stops.
 */
class CanvasTextMotionTest {
    private val ground = Color(0.08f, 0.08f, 0.10f, 1f)

    @Test
    fun anchoredElementsTweenSubpixelOffsetsAndSettleToExactStaticPixels() {
        for (anchor in listOf(CanvasAnchor.TopLeft, CanvasAnchor.Center, CanvasAnchor.BottomRight)) {
            val element = CanvasElement().apply {
                kind = CanvasElementKind.Text
                this.anchor = anchor
                offsetX = 12f
                offsetY = 10f
                width = 120f
                height = 24f
                text = "llll 1111"
                fontSize = 14f
                color = "#F5F5F7"
            }
            val items = listOf(CanvasItem(1, element))
            val host = ComposeHost(density = 1f)
            val centroids = (0 until 20).map { i ->
                element.offsetX = 12f + i * 0.1f
                val out = host.frame(FrameInput(260, 72)) { CanvasOverlay(items) }
                measureInk(out.primitives.rasterizeToPixelMap(260, 72, ground, UiFonts.default(), out.graphicsLayers)).centroidX
            }
            val expectedSign = if (anchor == CanvasAnchor.BottomRight) -1.0 else 1.0
            for (i in 1 until centroids.size) {
                val signedStep = (centroids[i] - centroids[i - 1]) * expectedSign
                assertTrue(
                    signedStep in 0.03..0.19,
                    "anchor=$anchor frame $i signedStep=$signedStep px expected in [0.03, 0.19]",
                )
            }
            val unitPeriodShift = (centroids[10] - centroids[0]) * expectedSign
            assertTrue(abs(unitPeriodShift - 1.0) < 0.01, "anchor=$anchor 10-frame signed shift=$unitPeriodShift px expected 1.0 ± 0.01")
            val totalSignedShift = (centroids.last() - centroids.first()) * expectedSign
            assertTrue(totalSignedShift in 1.80..2.00, "anchor=$anchor totalSignedShift=$totalSignedShift px expected in [1.80, 2.00]")

            // Hold stationary at the final fractional offset (13.9f) for 8 settle frames and confirm exact match against static render.
            var settled: PixelMap? = null
            var prevSettleCx = centroids.last()
            repeat(8) {
                val out = host.frame(FrameInput(260, 72)) { CanvasOverlay(items) }
                val map = out.primitives.rasterizeToPixelMap(260, 72, ground, UiFonts.default(), out.graphicsLayers)
                val cx = measureInk(map).centroidX
                assertTrue(abs(cx - prevSettleCx) <= 0.15, "anchor=$anchor settle frame popped by ${abs(cx - prevSettleCx)} px")
                prevSettleCx = cx
                settled = map
            }
            val staticOut = ComposeHost(density = 1f).frame(FrameInput(260, 72)) { CanvasOverlay(items) }
            val staticMap = staticOut.primitives.rasterizeToPixelMap(260, 72, ground, UiFonts.default(), staticOut.graphicsLayers)
            assertEquals(0, differingPixels(checkNotNull(settled), staticMap), "anchor=$anchor settled frame must match static render")
        }
    }

    private data class InkMetrics(val mass: Double, val centroidX: Double)

    private fun measureInk(map: PixelMap): InkMetrics {
        val bg = map.sample(0, 0)
        val bgLuma = srgbLuma(bg.r, bg.g, bg.b)
        var mass = 0.0
        var weightedX = 0.0
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val p = map.sample(x, y)
                val lum = srgbLuma(p.r, p.g, p.b)
                val ink = (lum - bgLuma).coerceAtLeast(0.0)
                if (ink > 1e-4) {
                    mass += ink
                    weightedX += (x + 0.5) * ink
                }
            }
        }
        return InkMetrics(mass = mass, centroidX = if (mass > 0.0) weightedX / mass else 0.0)
    }

    private fun srgbLuma(r: Int, g: Int, b: Int): Double =
        0.2126 * (r / 255.0) + 0.7152 * (g / 255.0) + 0.0722 * (b / 255.0)

    private fun differingPixels(a: PixelMap, b: PixelMap): Int {
        var diff = 0
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a.sample(x, y) != b.sample(x, y)) diff += 1
            }
        }
        return diff
    }
}
