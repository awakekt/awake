/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.LinearGradient
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.renderer.Renderer
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real backend probes; a bounding-box fallback adds about 27% extra ink.
 * Negative control on the unmodified implementation: Vulkan's 32 dp circle at 0.75 scale
 * painted the outside corner alpha=255 instead of 0. The old CPU shadow rectangle likewise
 * painted its outside corner alpha=255 instead of 0 (see the CPU RoundedShadowRasterTest).
 * Interior opacity and analytic area bounds prevent an empty/undersized clip from passing.
 */
class RoundClipParityTest {
    @Test
    fun circlesAndShadowsKeepTheirShapeOnBothBackends() {
        // Vulkan must finish before WebGPU loads its own Vulkan loader into the process.
        for (backend in listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)) {
            withHeadlessUi(backend) { renderer ->
                verifyCircles(renderer, backend)
                for (gradient in listOf(false, true)) verifyShadow(renderer, backend, gradient)
                verifyGradientFills(renderer, backend)
            }
        }
    }

    private fun verifyCircles(renderer: Renderer, backend: HeadlessUiBackend) {
        val ratios = mutableListOf<Double>()
        for (dp in listOf(32f, 64f, 128f, 137f, 192f, 256f, 512f)) {
            for (scale in listOf(0.75f, 0.83f, 1f, 1.25f, 1.5f, 1.75f, 2f)) {
                val diameter = dp * scale
                val scenario = circleClipScenario(diameter)
                val pixels = renderer.render(scenario)
                write(scenario, backend.name, pixels)
                val corner = (13.25f + diameter * 0.08f).toInt()
                val center = (13.25f + diameter / 2f).toInt()
                val label = "$backend size=$dp scale=$scale"
                assertEquals(0, alpha(pixels, scenario.size, corner, corner), "$label outside circle")
                assertEquals(255, alpha(pixels, scenario.size, center, center), "$label center")
                val area = pixels.indices.step(4).sumOf { channel(pixels, it + 3) / 255.0 }
                val ratio = area / (PI * diameter * diameter / 4.0)
                assertTrue(ratio in 0.94..1.03, "$label measured area/analytic circle=$ratio")
                ratios += ratio
            }
        }
        println("$backend: ${ratios.size} circle cases passed; area ratios ${ratios.min()}..${ratios.max()}")
    }

    private fun verifyShadow(renderer: Renderer, backend: HeadlessUiBackend, gradient: Boolean) {
        val scenario = roundShadowScenario(gradient)
        val gpu = renderer.render(scenario)
        val cpu = scenario.primitives.rasterize(96, 96, Color.Transparent)
        write(scenario, backend.name, gpu)
        write(scenario, "CPU", cpu)
        assertEquals(0, alpha(cpu, 96, 18, 18), "CPU round shadow corner")
        assertEquals(0, alpha(gpu, 96, 18, 18), "$backend round shadow corner")
        assertEquals(255, alpha(gpu, 96, 48, 48), "$backend round shadow center")
        compareCoverageAndColor(scenario.name, gpu, cpu, backend)
    }

    private fun verifyGradientFills(renderer: Renderer, backend: HeadlessUiBackend) {
        // DrawScope also emits ShadowQuad for gradient fills, including radius=0 and blur=0.
        for (radius in listOf(0f, 10f)) {
            val color = Color(0.9f, 0.28f, 0.3f, 0.6f)
            val end = Color(1f, 0.7f, 0.14f, 0.6f)
            val scenario = UiScenario(
                name = "gradient-fill-$radius",
                font = null,
                size = 160,
                primitives = listOf(
                    UiDrawPrimitive.ShadowQuad(
                        16f, 16f, 120f, 40f, radius,
                        0f, 0f, 0f, 0f, color, gradient = LinearGradient(color, end, end, color),
                    ),
                ),
            )
            val gpu = renderer.render(scenario)
            val cpu = scenario.primitives.rasterize(scenario.size, scenario.size, Color.Transparent)
            write(scenario, backend.name, gpu)
            write(scenario, "CPU", cpu)
            assertEquals(153, alpha(gpu, scenario.size, 76, 36), "$backend gradient fill interior opacity")
            compareCoverageAndColor(scenario.name, gpu, cpu, backend)
        }
    }

    private fun compareCoverageAndColor(name: String, gpu: ByteArray, cpu: ByteArray, backend: HeadlessUiBackend) {
        val maxAlphaDiff = gpu.indices.step(4).maxOf { abs(channel(gpu, it + 3) - channel(cpu, it + 3)) }
        assertTrue(maxAlphaDiff <= 1, "$backend versus CPU $name alpha: $maxAlphaDiff")
        // GPU readback is premultiplied; PixelMap stores straight colour. Compare in one space.
        val maxColorDiff = gpu.indices.step(4).maxOf { offset ->
            (0..2).maxOf { c ->
                abs(channel(gpu, offset + c) - (channel(cpu, offset + c) * channel(cpu, offset + 3) / 255f).roundToInt())
            }
        }
        assertTrue(maxColorDiff <= 2, "$backend versus CPU $name colour: $maxColorDiff")
        println("$backend $name: max alpha difference=$maxAlphaDiff; premultiplied colour=$maxColorDiff")
    }

    private fun channel(pixels: ByteArray, offset: Int): Int = pixels[offset].toInt() and 255

    private fun alpha(pixels: ByteArray, size: Int, x: Int, y: Int): Int = pixels[(y * size + x) * 4 + 3].toInt() and 255

    private fun write(scenario: UiScenario, backend: String, pixels: ByteArray) {
        val image = BufferedImage(scenario.size, scenario.size, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until scenario.size) {
            for (x in 0 until scenario.size) {
                val offset = (y * scenario.size + x) * 4
                fun channel(i: Int): Int = pixels[offset + i].toInt() and 255
                image.setRGB(x, y, (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
            }
        }
        val directory = File("build/reports/round-ui/$backend").apply { mkdirs() }
        ImageIO.write(image, "png", File(directory, "${scenario.name}.png"))
    }
}
