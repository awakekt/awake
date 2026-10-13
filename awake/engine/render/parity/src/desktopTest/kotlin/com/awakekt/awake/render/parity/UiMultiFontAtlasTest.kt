/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.comparePixels
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Glyphs of two fonts in one UI frame, on both backends: every line from its own atlas, in paint
 * order, through the offscreen path tests and Studio read back.
 *
 * The reference for each line is that line drawn alone through the one-font path a renderer has
 * always had: glyphs with no font, and the line's font passed to `drawUiToTexture`. So a line is
 * judged against the renderer's own picture of it, not against numbers copied from a capture.
 *
 * Positive controls, run every time rather than once by hand:
 * - The same frame with every glyph forced to the first font -- what a renderer binding one atlas
 *   per frame draws, which is what `main` drew before glyphs named their font -- must be rejected.
 *   Its bitmap line samples the Roboto atlas and vanishes: 1,487 px differ on MoltenVK, 1,485 on
 *   wgpu-native.
 * - Three overlapping `O`s drawn sorted by font instead of in paint order must be rejected: the
 *   bitmap `O` then covers the last Roboto one in all 549 px where both have ink.
 *
 * Breaking the renderer instead -- binding only the first font a frame draws -- fails the frame's
 * own check and its baseline by the same 1,487 / 1,485 px.
 *
 * Both frames are also pinned to committed PNGs per backend, zero differing pixels at the default
 * two-level channel tolerance. Re-record with `-DAWAKE_RECORD_SNAPSHOTS=true`, and only after
 * stating what should change and why.
 */
class UiMultiFontAtlasTest {

    @Test
    fun eachLineDrawsFromItsOwnAtlasInPaintOrderOnBothBackends() {
        val record = System.getProperty("AWAKE_RECORD_SNAPSHOTS")?.toBoolean() == true
        val failures = mutableListOf<String>()
        // Vulkan first, for the loader reason UiBackendParityTest documents.
        listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu).forEach { backend ->
            openHeadlessUi(backend).use { session ->
                checkBackend(session.renderer, backend, record, failures)
            }
        }
        if (record) fail("Recorded the two-font baselines -- rerun without the flag.")
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun checkBackend(renderer: Renderer, backend: HeadlessUiBackend, record: Boolean, failures: MutableList<String>) {
        val references = TWO_FONT_LINES.map { renderer.renderOneFont(it) }
        val frame = renderer.render(TWO_FONTS_SCENARIO)
        ownAtlasProblem(frame, references)?.let { failures += "$backend: $it" }

        val oneAtlas = renderer.render(TWO_FONTS_SCENARIO.copy(primitives = TWO_FONT_LINES.flatten().map { it.copy(font = firstFont) }))
        if (ownAtlasProblem(oneAtlas, references) == null) {
            failures += "$backend: the check accepted a frame with every glyph drawn from the first font's atlas"
        }

        val stack = listOf(redO, greenO, blueO)
        val stackReferences = stack.map { renderer.renderOneFont(it) }
        orderProblem(renderer.renderFrame(stack.flatten()), stackReferences)?.let { failures += "$backend: $it" }
        if (orderProblem(renderer.renderFrame(listOf(redO, blueO, greenO).flatten()), stackReferences) == null) {
            failures += "$backend: the check accepted overlapping glyphs drawn sorted by font"
        }

        listOf("two-fonts" to frame, "two-fonts-one-atlas" to oneAtlas).forEach { (name, pixels) ->
            baselineProblem("ui-$name-${backend.name.lowercase()}", pixels, record)?.let { failures += it }
        }
    }

    /**
     * How the frame's lines differ from their one-font references, or null when each line matches.
     * The lines' glyph quads sit in disjoint horizontal bands, so inside a band only its own line
     * can have drawn anything.
     */
    private fun ownAtlasProblem(frame: ByteArray, references: List<ByteArray>): String? {
        val problems = TWO_FONT_LINES.indices.mapNotNull { line ->
            val (top, bottom) = bands[line]
            var differing = 0
            for (y in top until bottom) {
                for (x in 0 until TEXT_SCENARIO_SIZE) {
                    if (!samePixel(frame, references[line], y * TEXT_SCENARIO_SIZE + x)) differing++
                }
            }
            if (differing == 0) null else "line ${line + 1} differs from that line drawn alone in its own font in $differing px"
        }
        return problems.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    /**
     * Whether the last-drawn glyph covers the one under it where both have ink, and the middle one
     * shows where only it does; null when paint order held. Fails rather than passes when the
     * glyphs do not overlap at all, since order would then be unobservable.
     */
    private fun orderProblem(frame: ByteArray, references: List<ByteArray>): String? {
        val (red, green, blue) = references
        var overlap = 0
        var wrongTop = 0
        var middleOnly = 0
        var wrongMiddle = 0
        for (pixel in 0 until TEXT_SCENARIO_SIZE * TEXT_SCENARIO_SIZE) {
            if (alpha(blue, pixel) == OPAQUE && alpha(green, pixel) > 0) {
                overlap++
                if (!samePixel(frame, blue, pixel)) wrongTop++
            }
            if (alpha(green, pixel) == OPAQUE && alpha(blue, pixel) == 0 && alpha(red, pixel) == 0) {
                middleOnly++
                if (!samePixel(frame, green, pixel)) wrongMiddle++
            }
        }
        return when {
            overlap == 0 || middleOnly == 0 -> "the order glyphs do not overlap, so paint order is unobservable"
            wrongTop > 0 -> "the last glyph is not on top in $wrongTop of $overlap overlapping px"
            wrongMiddle > 0 -> "the middle glyph is missing in $wrongMiddle of $middleOnly px only it covers"
            else -> null
        }
    }

    /** Draws [glyphs] the one-font way: no glyph names a font, and the call passes theirs. */
    private fun Renderer.renderOneFont(glyphs: List<UiDrawPrimitive.Glyph>): ByteArray =
        renderFrame(glyphs.map { it.copy(font = null) }, glyphs.first().font)

    private fun Renderer.renderFrame(primitives: List<UiDrawPrimitive>, font: UiFont? = null): ByteArray {
        val target = createRenderTarget(TEXT_SCENARIO_SIZE, TEXT_SCENARIO_SIZE)
        return try {
            drawUiToTexture(target, primitives, font)
            runBlocking { readPixels(target) }.data
        } finally {
            target.destroy()
        }
    }

    /** Records or compares [pixels] against `baselines/[name].png`; a message on mismatch. */
    private fun baselineProblem(name: String, pixels: ByteArray, record: Boolean): String? {
        val file = File("src/desktopTest/resources/baselines/$name.png")
        return when {
            record -> {
                file.parentFile.mkdirs()
                ImageIO.write(pixels.toImage(), "png", file)
                null
            }
            !file.exists() -> "${file.path} does not exist -- record it with -DAWAKE_RECORD_SNAPSHOTS=true"
            else -> mismatch(name, pixels, file)
        }
    }

    private fun mismatch(name: String, pixels: ByteArray, baseline: File): String? {
        val result = comparePixels(pixels, ImageIO.read(baseline).toRgba())
        if (result.matches) return null
        val actual = File(FAILURE_DIR, "$name-actual.png").apply { parentFile.mkdirs() }
        ImageIO.write(pixels.toImage(), "png", actual)
        return "$name diverged from its baseline in ${result.diffPixelCount} px (max channel diff " +
            "${result.maxChannelDiff}) -- compare ${actual.path} against ${baseline.path}"
    }

    private fun samePixel(a: ByteArray, b: ByteArray, pixel: Int): Boolean =
        (0 until 4).all { kotlin.math.abs(a.channel(pixel, it) - b.channel(pixel, it)) <= CHANNEL_TOLERANCE }

    private fun alpha(pixels: ByteArray, pixel: Int): Int = pixels.channel(pixel, 3)

    private fun ByteArray.channel(pixel: Int, channel: Int): Int = this[pixel * 4 + channel].toInt() and 0xFF

    private fun ByteArray.toImage(): BufferedImage {
        val image = BufferedImage(TEXT_SCENARIO_SIZE, TEXT_SCENARIO_SIZE, BufferedImage.TYPE_INT_ARGB)
        for (pixel in 0 until TEXT_SCENARIO_SIZE * TEXT_SCENARIO_SIZE) {
            val argb = (channel(pixel, 3) shl 24) or (channel(pixel, 0) shl 16) or (channel(pixel, 1) shl 8) or channel(pixel, 2)
            image.setRGB(pixel % TEXT_SCENARIO_SIZE, pixel / TEXT_SCENARIO_SIZE, argb)
        }
        return image
    }

    private fun BufferedImage.toRgba(): ByteArray = ByteArray(width * height * 4).also { bytes ->
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = getRGB(x, y)
                val offset = (y * width + x) * 4
                bytes[offset] = (argb ushr 16).toByte()
                bytes[offset + 1] = (argb ushr 8).toByte()
                bytes[offset + 2] = argb.toByte()
                bytes[offset + 3] = (argb ushr 24).toByte()
            }
        }
    }

    private companion object {
        const val OPAQUE = 255
        const val CHANNEL_TOLERANCE = 2
        const val FAILURE_DIR = "build/test-failures/UiMultiFontAtlasTest"

        val firstFont: UiFont = checkNotNull(TWO_FONT_LINES.first().first().font)

        /** Each line's rows: from its highest glyph quad's top to its lowest quad's bottom. */
        val bands: List<Pair<Int, Int>> = TWO_FONT_LINES.map { line ->
            floor(line.minOf { it.y }).toInt().coerceAtLeast(0) to
                ceil(line.maxOf { it.y + it.h }).toInt().coerceAtMost(TEXT_SCENARIO_SIZE)
        }.also { bands ->
            bands.zipWithNext().forEach { (upper, lower) ->
                check(upper.second <= lower.first) { "two-font lines overlap: $bands" }
            }
        }

        /** Roboto, the bitmap font, then Roboto again, stacked on one spot. */
        val redO = textLine("O", UiFonts.default(), ORDER_GLYPH_PX, 16f, 8f, Color(1f, 0f, 0f, 1f))
        val greenO = textLine("O", UiFonts.bitmap(), ORDER_GLYPH_PX, 16f, 8f, Color(0f, 1f, 0f, 1f))
        val blueO = textLine("O", UiFonts.default(), ORDER_GLYPH_PX, 16f, 8f, Color(0f, 0f, 1f, 1f))

        const val ORDER_GLYPH_PX = 96f
    }
}
