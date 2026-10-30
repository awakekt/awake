/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasGradient
import com.awakekt.awake.ui.canvas.CanvasItem
import com.awakekt.awake.ui.canvas.CanvasOverlay
import com.awakekt.awake.ui.canvas.CanvasStyle
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The real canvas paint path on both GPUs: midpoint colour, a translucent join and a rounded clip.
 * Negative control: disabling the middle-to-brush mapping fails on both Vulkan and WebGPU at
 * (24,24), returning [127, 0, 127, 255] where the solid reference is [0, 255, 0, 255].
 * Disabling the AA fringe fails on both backends: zero soft boundary pixels against 208 on the
 * normal rounded fill. The fixed gradient has 192; its edge count stays within 25% of the reference.
 */
class CanvasGradientParityTest {
    @Test
    fun vulkanDrawsBothAxesWithoutAnAlphaSeamAndKeepsTheRoundedClip() = verify(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuDrawsBothAxesWithoutAnAlphaSeamAndKeepsTheRoundedClip() = verify(HeadlessUiBackend.WebGpu)

    private fun verify(backend: HeadlessUiBackend) {
        val items = (0..5).map { index ->
            val alpha = if (index >= 3) "80" else ""
            CanvasItem(
                index + 1,
                CanvasElement().apply {
                    kind = CanvasElementKind.Panel
                    offsetX = 4f + (index % 3) * 51f; offsetY = 4f + (index / 3) * 51f
                    width = 41f; height = 41f
                    style = if (index % 3 == 2) {
                        CanvasStyle(background = "#00FF00$alpha", cornerRadius = 12f)
                    } else {
                        CanvasStyle(
                            gradient = CanvasGradient("#FF0000$alpha", "#0000FF$alpha", horizontal = index % 3 == 1, middle = "#00FF00$alpha"),
                            cornerRadius = if (index % 3 == 0) 12f else 0f,
                        )
                    }
                },
            )
        }
        val primitives = listOf(UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color.Black)) +
            ComposeHost().frame(FrameInput(SIZE, SIZE)) { CanvasOverlay(items) }.primitives
        val frame = withHeadlessUi(backend, SIZE) { renderer -> renderer.render(UiScenario("canvas-gradient-midpoints", null, primitives, SIZE)) }
        fun rgba(x: Int, y: Int): List<Int> = (0..3).map { frame[(y * SIZE + x) * 4 + it].toInt() and 255 }
        for (y in listOf(24, 75)) {
            val reference = rgba(126, y)
            for (x in listOf(24, 75)) {
                val midpoint = rgba(x, y)
                assertTrue(midpoint.zip(reference).all { (got, expected) -> abs(got - expected) <= 1 }, "$backend midpoint ($x,$y)=$midpoint, solid reference=$reference")
            }
        }
        assertTrue(rgba(4, 4).take(3).all { it == 0 }, "$backend: rounded corner stays clipped")
        fun softPixels(left: Int): Int = (4 until 45).sumOf { y -> (left until left + 41).count { x -> rgba(x, y).take(3).sum() in 1..249 } }
        val referenceEdge = softPixels(106)
        val midpointEdge = softPixels(4)
        println("$backend rounded edge pixels: reference=$referenceEdge, midpoint=$midpointEdge")
        assertTrue(referenceEdge > 0 && midpointEdge in referenceEdge * 3 / 4..referenceEdge * 5 / 4, "$backend rounded edge pixels: reference=$referenceEdge, midpoint=$midpointEdge")
        assertTrue(rgba(24, 5)[0] > 220, "$backend: start colour remains red")
        assertTrue(rgba(24, 43)[2] > 220, "$backend: end colour remains blue")
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val c = rgba(x, y)
                image.setRGB(x, y, (c[3] shl 24) or (c[0] shl 16) or (c[1] shl 8) or c[2])
            }
        }
        val out = File("build/reports/render-parity").apply { mkdirs() }
        ImageIO.write(image, "png", File(out, "canvas-gradient-midpoints-$backend.png"))
    }

    private companion object { const val SIZE = 160 }
}
