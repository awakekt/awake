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
import com.awakekt.awake.compose.testing.outlineRing
import com.awakekt.awake.compose.testing.rasterizeToPixelMap
import com.awakekt.awake.compose.testing.toBufferedImage
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextOutline
import com.awakekt.awake.core.text.theme.TextShadow
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.passes2d.DrawRunCoalescer
import com.awakekt.awake.render.passes2d.StagedDrawRun
import com.awakekt.awake.render.renderer.Renderer
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Outlines and soft shadows from the effect field (#1061) draw the same on the CPU raster, Vulkan and WebGPU: a 1, 2
 * and 4 dp outline around `O`, `W` and `8` at 12, 24 and 48 px, and a soft and a hard shadow. Each backend's frame is
 * held to the raster's pixel by pixel, and its outline ring measured as `TextEffectsRasterTest` measures the raster's.
 * Outlined, shadowed text stages one decorated run of one quad a glyph.
 *
 * Every frame is written to `build/reports/render-parity/text-effects/`.
 */
class UiTextEffectsParityTest {
    @Test
    fun theRasterAndBothBackendsDrawTheSameOutlinesAndShadows() {
        val cases = buildList {
            for (size in listOf(12, 24, 48)) {
                for (width in listOf(1f, 2f, 4f)) {
                    for (glyph in "OW8") {
                        val style = TextStyle(color = Color.White, outline = TextOutline(Color.Black, width))
                        add(Case("outline-$glyph-$size-${width.toInt()}", size, style, text = glyph.toString()))
                    }
                }
            }
            add(Case("soft-shadow-32", 32, TextStyle(color = Color.White, shadow = TextShadow(Color.Black, 2f, 3f, blur = 4f))))
            add(Case("hard-shadow-32", 32, TextStyle(color = Color.White, shadow = TextShadow(Color.Black, 2f, 2f))))
            add(Case("both-24", 24, TextStyle(color = Color.White, outline = TextOutline(Color.Black, 2f), shadow = TextShadow(Color.Black, 1f, 2f, 2f))))
        }
        val raster = cases.associateWith { it.primitives.rasterizeToPixelMap(WIDTH, HEIGHT, background = GREY, font = UiFonts.default()).pixels }
        // Vulkan first: wgpu's own Vulkan loader breaks a Vulkan instance created after it (UiBackendParityTest).
        val failures = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu).flatMap { backend ->
            openHeadlessUi(backend, WIDTH).use { session -> failures(backend, session.renderer, cases, raster) }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** One decorated glyph primitive a character, staged as one run of one quad a glyph. */
    @Test
    fun outlinedShadowedTextStagesOneQuadAGlyph() {
        val style = TextStyle(color = Color.White, outline = TextOutline(Color.Black, 2f), shadow = TextShadow(Color.Black, 1f, 1f, 2f))
        val primitives = Case("count", 24, style, text = "Harbor Town").primitives
        val glyphs = primitives.filterIsInstance<UiDrawPrimitive.Glyph>()
        assertEquals("Harbor Town".length, glyphs.size)
        val runs = DrawRunCoalescer.coalesce(primitives).filterIsInstance<StagedDrawRun.GlyphRun>()
        // The space has no ink to decorate, so it is a plain glyph of its own between the two words' runs.
        assertEquals(listOf(true, false, true), runs.map { it.decorated })
        assertEquals((glyphs.size - 1) * 6, runs.filter { it.decorated }.sumOf { it.indices.size }, "one quad a decorated glyph")
    }

    private fun failures(
        backend: HeadlessUiBackend,
        renderer: Renderer,
        cases: List<Case>,
        raster: Map<Case, ByteArray>,
    ): List<String> = cases.flatMap { case ->
        val gpu = draw(renderer, case.primitives)
        val cpu = raster.getValue(case)
        write("${case.name}-${backend.name.lowercase()}", gpu)
        write("${case.name}-raster", cpu)
        val (differing, largest) = compare(cpu, gpu)
        println("$backend ${case.name}: $differing pixels over $CHANNEL_TOLERANCE (largest $largest)")
        buildList {
            if (differing > MAX_DIFFERING) add("$backend ${case.name}: $differing pixels differ by more than $CHANNEL_TOLERANCE (largest $largest)")
            case.outlineWidth?.let { width ->
                val quad = case.primitives.filterIsInstance<UiDrawPrimitive.Glyph>().first()
                val grow = (width + 2f).toInt()
                val ring = PixelMap(WIDTH, HEIGHT, gpu).outlineRing(
                    (quad.x - grow).toInt(),
                    (quad.y - grow).toInt(),
                    (quad.x + quad.w + grow).toInt(),
                    (quad.y + quad.h + grow).toInt(),
                )
                if (ring.gaps > 0 || ring.thinnest < maxOf(width - SCALLOP_PX, MIN_RING_PX)) add("$backend ${case.name}: $ring")
            }
        }
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

    /** How many pixels differ by more than [CHANNEL_TOLERANCE] in a colour channel, and the largest difference. */
    private fun compare(cpu: ByteArray, gpu: ByteArray): Pair<Int, Int> {
        var differing = 0
        var largest = 0
        for (offset in cpu.indices step 4) {
            var pixel = 0
            for (channel in 0..2) pixel = maxOf(pixel, abs((cpu[offset + channel].toInt() and 0xFF) - (gpu[offset + channel].toInt() and 0xFF)))
            largest = maxOf(largest, pixel)
            if (pixel > CHANNEL_TOLERANCE) differing++
        }
        return differing to largest
    }

    private fun write(name: String, pixels: ByteArray) {
        val directory = File(REPORT_DIR).apply { mkdirs() }
        ImageIO.write(PixelMap(WIDTH, HEIGHT, pixels).toBufferedImage(), "PNG", File(directory, "$name.png"))
    }

    /** [text] at [size] px in [style], over a mid-grey ground. */
    private inner class Case(val name: String, size: Int, style: TextStyle, text: String = "OW8") {
        val outlineWidth: Float? = style.outline?.width?.takeIf { style.shadow == null }
        val primitives: List<UiDrawPrimitive> = ComposeHost().frame(FrameInput(WIDTH, HEIGHT)) {
            Box(Modifier.size(WIDTH.dp, HEIGHT.dp).background(GREY)) {
                Text(text, Modifier.padding(PADDING.dp), style = style.copy(size = size.sp))
            }
        }.primitives
    }

    private companion object {
        const val WIDTH = 256
        const val HEIGHT = 96
        const val PADDING = 16
        val GREY = Color(0.5f, 0.5f, 0.5f, 1f)

        /**
         * Each backend's pixels against the raster's. Measured on lavapipe and wgpu-native: every pixel within 5
         * levels. Negative control: sampling the effect field through the hardware filter, which returns 8 bits,
         * put 68 to 210 pixels a case more than 6 levels off at 48 px, up to 65.
         */
        const val CHANNEL_TOLERANCE = 6
        const val MAX_DIFFERING = 0

        /** As `TextEffectsRasterTest`. */
        const val SCALLOP_PX = 0.95f
        const val MIN_RING_PX = 0.25f

        const val REPORT_DIR = "build/reports/render-parity/text-effects"
    }
}
