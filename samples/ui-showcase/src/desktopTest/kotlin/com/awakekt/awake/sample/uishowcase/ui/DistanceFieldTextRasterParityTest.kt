/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.testing.rasterizeToPixelMap
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFontSamplingMode
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.vulkan.renderer.VulkanUiPreviewCapture
import com.awakekt.awake.vulkan.utils.VkResultException
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Distance-field text drawn by the CPU raster every UI snapshot baseline uses agrees with the same
 * primitives drawn by the headless Vulkan UI pipelines (#1057), at 28, 32, 48 and 96 px and display
 * scales 1 and 1.5: sizes above the pixel-glyph limit, where the atlas is a distance field. 28 px
 * draws the 32-texel em smaller than the atlas, where the raster used to supersample.
 *
 * Measured on the pixels either renderer inks (luminance under 250 on white), as 0-255 luminance:
 * - mean |L_cpu - L_vulkan| at most [MAX_MEAN_DIFFERENCE]
 * - total ink, CPU over Vulkan, within [INK_RATIO_TOLERANCE] of 1
 *
 * Measured on lavapipe: mean difference 0.38-0.48 and ink ratio 1.000-1.002 at every size and
 * scale. Single pixels differ by up to 18, on the steep ramp a large glyph's 6-9 px range gives,
 * where a distance one 8-bit level apart (most likely the GPU sampler's fixed-point filter weights
 * against the CPU's float bilinear) moves coverage by a few percent; they average out, as the ink
 * ratio shows.
 *
 * Negative control, the raster before #1057 (half the shader's screen-pixel range, and 2x2
 * supersampling at 28 px and scale 1): mean difference 9.7-32.8 and ink ratio 1.015-1.090, so
 * every case fails both bounds.
 *
 * Skips, as [ShadcnComposeParityPreviewTest]'s GPU previews do, when no Vulkan device exists;
 * run it under `xvfb-run -a` with lavapipe installed.
 */
class DistanceFieldTextRasterParityTest {
    @Test
    fun cpuRasterDistanceFieldTextMatchesVulkan() {
        val failures = ArrayList<String>()
        for (scale in SCALES) {
            for (size in SIZES) {
                val result = compare(size, scale) ?: return
                println("Distance-field parity ${size}px @ $scale: $result")
                if (result.meanDifference > MAX_MEAN_DIFFERENCE ||
                    abs(result.inkRatio - 1f) > INK_RATIO_TOLERANCE
                ) {
                    failures += "${size}px @ $scale: $result"
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail(
                "CPU raster distance-field text differs from Vulkan " +
                    "(limits: mean <= $MAX_MEAN_DIFFERENCE, ink ratio 1 +- $INK_RATIO_TOLERANCE):\n" +
                    failures.joinToString("\n"),
            )
        }
    }

    /** Draws [SPECIMEN] at [size] px and display [scale] on both renderers, or null without a device. */
    private fun compare(size: Int, scale: Float): ParityResult? {
        val width = ceil((size * SPECIMEN_WIDTH_EM + 2 * PADDING) * scale).toInt()
        val height = ceil((size * SPECIMEN_HEIGHT_EM + 2 * PADDING) * scale).toInt()
        val frame = ComposeHost(density = scale).frame(FrameInput(width, height)) {
            Box(Modifier.padding(PADDING.dp)) {
                Text(SPECIMEN, style = TextStyle(color = Ink, size = size.sp), softWrap = false)
            }
        }
        val font = UiFonts.default()
        val glyphs = frame.primitives.filterIsInstance<UiDrawPrimitive.Glyph>()
        assertTrue(glyphs.isNotEmpty(), "${size}px @ $scale drew no glyphs")
        assertTrue(
            glyphs.all { (it.font ?: font).pageFont(it.page).samplingMode == UiFontSamplingMode.DistanceField },
            "${size}px @ $scale is not drawn from the distance field, so it does not test #1057",
        )
        // The text is blended over an opaque page on both renderers, as an app draws it. Over the
        // capture's transparent target the GPU would store colour times alpha, which compositeOver
        // reads as straight alpha, darkening every edge pixel by up to 17 * a * (1 - a).
        val primitives = listOf(UiDrawPrimitive.Quad(0f, 0f, width.toFloat(), height.toFloat(), Paper)) + frame.primitives
        val cpu = primitives.rasterizeToPixelMap(width, height, background = Paper, font = font)
        val gpu = try {
            VulkanUiPreviewCapture(width, height)
        } catch (failure: VkResultException) {
            println("Skipped distance-field parity: no Vulkan device (${failure.message}).")
            return null
        }.use { capture -> runBlocking { capture.capture(primitives, font) } }
        val outDir = File("build/ui-previews/distance-field-parity").apply { mkdirs() }
        val label = "${size}px-s$scale"
        cpu.writePng(File(outDir, "$label-cpu.png"))
        gpu.writePng(File(outDir, "$label-vulkan.png"))
        return measure(cpu, gpu)
    }

    private fun measure(cpu: PixelMap, gpu: PixelMap): ParityResult {
        var inked = 0
        var differenceSum = 0.0
        var maxDifference = 0f
        var cpuInk = 0.0
        var gpuInk = 0.0
        for (y in 0 until cpu.height) {
            for (x in 0 until cpu.width) {
                val a = luminance(cpu, x, y)
                val b = luminance(gpu, x, y)
                cpuInk += WHITE - a
                gpuInk += WHITE - b
                if (a >= INK_THRESHOLD && b >= INK_THRESHOLD) continue
                val difference = abs(a - b)
                inked += 1
                differenceSum += difference
                if (difference > maxDifference) maxDifference = difference
            }
        }
        check(inked > 0 && gpuInk > 0.0) { "Vulkan drew no ink" }
        return ParityResult((differenceSum / inked).toFloat(), (cpuInk / gpuInk).toFloat(), maxDifference, inked)
    }

    private fun luminance(map: PixelMap, x: Int, y: Int): Float {
        val pixel = map.sample(x, y)
        return LUMA_R * pixel.r + LUMA_G * pixel.g + LUMA_B * pixel.b
    }

    private data class ParityResult(val meanDifference: Float, val inkRatio: Float, val maxDifference: Float, val pixels: Int) {
        override fun toString(): String =
            "mean |dL| %.3f, ink ratio %.4f, max |dL| %.1f over %d px".format(meanDifference, inkRatio, maxDifference, pixels)
    }

    private companion object {
        /** Straight stems, round bowls, and the joins (`6`, `9`, `e`, `8`) #1057 saw stray marks at. */
        const val SPECIMEN = "Hage 0689"
        const val SPECIMEN_WIDTH_EM = 5.2f
        const val SPECIMEN_HEIGHT_EM = 1.4f
        const val PADDING = 8
        val SIZES = intArrayOf(28, 32, 48, 96)
        val SCALES = floatArrayOf(1f, 1.5f)
        const val MAX_MEAN_DIFFERENCE = 1f
        const val INK_RATIO_TOLERANCE = 0.01f
        const val INK_THRESHOLD = 250f
        const val WHITE = 255f
        const val LUMA_R = 0.2126f
        const val LUMA_G = 0.7152f
        const val LUMA_B = 0.0722f
        val Ink = Color(17f / 255f, 17f / 255f, 17f / 255f, 1f)
        val Paper = Color(1f, 1f, 1f, 1f)
    }
}
