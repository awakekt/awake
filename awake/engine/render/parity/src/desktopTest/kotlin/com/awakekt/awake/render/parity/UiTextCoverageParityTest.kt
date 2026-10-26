/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.testing.rasterizeToPixelMap
import com.awakekt.awake.compose.testing.toBufferedImage
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.font.withPixelGlyphMaxSize
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.renderer.Renderer
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The text coverage rule draws the same on the CPU raster, Vulkan and WebGPU (#1003): dark text on a light
 * ground and light text on a dark one, from exact-size pixel glyphs (12 and 24 px) and from the distance
 * field (24 px with pixel glyphs turned off, and 32 px). Each backend's frame is held to the raster's
 * pixel by pixel, and its ink, summed as `TextPolarityWeightTest` sums it, to within [INK_TOLERANCE].
 *
 * Negative control: with the raster's contrast at 0 and the shaders' at 0.2, every dark-on-light case
 * fails on both backends, 12 px pixel glyphs with 225 of 12 288 pixels more than 4 levels off (largest 9)
 * and ink 4.9% apart; light-on-dark, where the contrast fades out, still matches.
 *
 * Every frame is written to `build/reports/render-parity/text-coverage/`.
 */
class UiTextCoverageParityTest {
    @Test
    fun theRasterAndBothBackendsDrawTheSameText() {
        val cases = buildList {
            for (polarity in Polarity.entries) {
                add(Case("pixel-12", 12, null, polarity))
                add(Case("pixel-24", 24, null, polarity))
                add(Case("field-24", 24, UiFonts.default().withPixelGlyphMaxSize(0), polarity))
                add(Case("field-32", 32, null, polarity))
            }
        }
        val raster = cases.associateWith { case ->
            case.primitives.rasterizeToPixelMap(WIDTH, HEIGHT, background = case.polarity.ground, font = UiFonts.default()).pixels
        }
        // Vulkan first: wgpu's own Vulkan loader breaks a Vulkan instance created after it (UiBackendParityTest).
        val failures = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu).flatMap { backend ->
            openHeadlessUi(backend, WIDTH).use { session -> failures(backend, session.renderer, cases, raster) }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** Draws every case on [backend] and holds it to its [raster] frame; one message per case that differs. */
    private fun failures(
        backend: HeadlessUiBackend,
        renderer: Renderer,
        cases: List<Case>,
        raster: Map<Case, ByteArray>,
    ): List<String> = cases.mapNotNull { case ->
        val gpu = draw(renderer, case.primitives)
        val cpu = raster.getValue(case)
        write("${case.name}-${backend.name.lowercase()}", gpu)
        write("${case.name}-raster", cpu)
        val comparison = compare(cpu, gpu, case.polarity)
        println("$backend ${case.name}: $comparison")
        comparison.problem()?.let { "$backend ${case.name}: $it" }
    }

    private fun draw(renderer: Renderer, primitives: List<UiDrawPrimitive>): ByteArray {
        val target = renderer.createRenderTarget(WIDTH, HEIGHT)
        return try {
            renderer.drawUiToTexture(target, primitives, UiFonts.default())
            runBlocking { renderer.readPixels(target) }.data
        } finally {
            target.destroy()
        }
    }

    private data class Comparison(val differing: Int, val largest: Int, val inkRatio: Double) {
        fun problem(): String? = when {
            differing > MAX_DIFFERING -> "$differing pixels differ by more than $CHANNEL_TOLERANCE (largest $largest)"
            abs(inkRatio - 1) > INK_TOLERANCE -> "ink $inkRatio of the raster's"
            else -> null
        }

        override fun toString() = "$differing pixels over $CHANNEL_TOLERANCE (largest $largest), ink x${"%.4f".format(inkRatio)}"
    }

    private fun compare(cpu: ByteArray, gpu: ByteArray, polarity: Polarity): Comparison {
        var differing = 0
        var largest = 0
        for (offset in cpu.indices step 4) {
            var pixelLargest = 0
            for (channel in 0..2) {
                pixelLargest = maxOf(pixelLargest, abs((cpu[offset + channel].toInt() and 0xFF) - (gpu[offset + channel].toInt() and 0xFF)))
            }
            largest = maxOf(largest, pixelLargest)
            if (pixelLargest > CHANNEL_TOLERANCE) differing++
        }
        return Comparison(differing, largest, polarity.ink(gpu) / polarity.ink(cpu))
    }

    private fun write(name: String, pixels: ByteArray) {
        val directory = File(REPORT_DIR).apply { mkdirs() }
        ImageIO.write(PixelMap(WIDTH, HEIGHT, pixels).toBufferedImage(), "PNG", File(directory, "$name.png"))
    }

    private inner class Case(id: String, size: Int, font: UiFont?, val polarity: Polarity) {
        val name = "$id-${polarity.id}"
        val primitives: List<UiDrawPrimitive> = ComposeHost().frame(FrameInput(WIDTH, HEIGHT)) {
            Box(Modifier.size(WIDTH.dp, HEIGHT.dp).background(polarity.ground)) {
                Text(LABEL, Modifier.padding(4.dp), style = TextStyle(color = polarity.ink, size = size.sp, font = font, kerning = false))
            }
        }.primitives
    }

    private enum class Polarity(val id: String, val ink: Color, val ground: Color) {
        DarkOnLight("dark-on-light", DARK, LIGHT),
        LightOnDark("light-on-dark", LIGHT, DARK),
        ;

        /**
         * The summed darkness of [pixels]' ink: each pixel's distance from the frame's own ground, its first
         * pixel, towards the ink, 0 to 1, from its red channel, counting pixels from [INK] up. The frame's own
         * ground, because the raster truncates a channel to its byte and the GPU rounds it.
         */
        fun ink(pixels: ByteArray): Double {
            val ground = (pixels[0].toInt() and 0xFF).toDouble()
            val full = ground - ink.r * 255.0
            return (pixels.indices step 4).sumOf { offset ->
                val darkness = ((ground - (pixels[offset].toInt() and 0xFF)) / full).coerceIn(0.0, 1.0)
                if (darkness >= INK) darkness else 0.0
            }
        }
    }

    private companion object {
        const val LABEL = "Mesh Terrain 0123"
        const val WIDTH = 256
        const val HEIGHT = 48
        val DARK = Color(0.09f, 0.09f, 0.09f, 1f)
        val LIGHT = Color(0.96f, 0.96f, 0.96f, 1f)

        /**
         * Measured: every pixel within 4 levels of the raster on both backends, and ink within 1.1%, the
         * raster's alpha being truncated to a byte where the GPU's stays a float.
         */
        const val CHANNEL_TOLERANCE = 4
        const val MAX_DIFFERING = 0
        const val INK_TOLERANCE = 0.02

        /** Darkness from which a pixel counts as ink, as `TextPolarityWeightTest` counts it. */
        const val INK = 0.08

        const val REPORT_DIR = "build/reports/render-parity/text-coverage"
    }
}
