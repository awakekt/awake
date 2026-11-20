/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextOutline
import com.awakekt.awake.core.text.theme.TextShadow
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.render.renderer.Renderer
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A decorated run's fill is the fill of plain text of its size, on both backends (#1808).
 *
 * Each channel carries one layer: the fill is red, the outline blue, the shadow green, over a cleared target. Red is
 * then the fill's coverage alone, whatever lies under it, so a decorated run's red channel set against a plain run's
 * is its fill set against plain text's.
 *
 * Negative control, the code before this change, the same on both backends: at 11 to 14 sp at 1x the decorated fill had
 * 16% to 27% of a plain run's solid pixels and its red channel differed from plain text's by 99 to 180 levels; at 11
 * and 12 sp at 2x, 57% and 66%, by 198 and 87 levels. Every case above passes now, with a difference of 0.
 */
class UiDecoratedTextFillParityTest {
    private val backends = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
    private val font: UiFont = UiFonts.default()
    private val fill = Color(1f, 0f, 0f, 1f)
    private val outline = TextOutline(Color(0f, 0f, 1f, 1f), 1f)
    private val shadow = TextShadow(Color(0f, 1f, 0f, 1f), 1f, 1f)

    /** At or under the pixel-glyph limit the fill is plain text's to the level; above it, the field's as before. */
    @Test
    fun aDecoratedFillMatchesPlainTextAtSmallSizes() {
        val report = StringBuilder()
        val failures = mutableListOf<String>()
        for (backend in backends) {
            val rows = mutableListOf<List<Array<IntArray>>>()
            openHeadlessUi(backend, WIDTH).use { session ->
                for (density in DENSITIES) {
                    for (size in SIZES) {
                        val pixelRun = size * density <= font.pixelGlyphMaxSize
                        for ((name, decoration) in decorations()) {
                            val plain = render(session.renderer, density) { Text(LABEL, style = TextStyle(color = fill, size = size.sp)) }
                            val decorated = render(session.renderer, density) { Text(LABEL, style = decoration(fill, size)) }
                            val bare = render(session.renderer, density) { Text(LABEL, style = decoration(Color.Transparent, size)) }
                            val case = "$backend $name ${size.toInt()}sp at ${density.toInt()}x"
                            val redDiff = maxDiff(plain, decorated, RED)
                            val offFill = maxDiffOutside(plain, decorated, bare)
                            val share = solid(decorated, RED).toFloat() / solid(plain, RED)
                            report.appendLine(
                                "$case (${if (pixelRun) "pixel glyphs" else "field"}): fill max diff $redDiff, " +
                                    "decoration off the fill max diff $offFill, solid fill share ${"%.2f".format(share)}",
                            )
                            if (pixelRun && redDiff != 0) failures += "$case: the fill differs from plain text's by $redDiff"
                            if (pixelRun && offFill != 0) failures += "$case: the decoration off the fill moved by $offFill"
                            rows += listOf(plain, decorated, bare)
                        }
                    }
                }
            }
            writeComparison(backend, rows)
        }
        println(report)
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** One decorated node draws exactly what the two-node workaround drew: a fill-less decorated run, plain text on top. */
    @Test
    fun oneDecoratedNodeDrawsWhatTheTwoNodeWorkaroundDrew() {
        for (backend in backends) {
            openHeadlessUi(backend, WIDTH).use { session ->
                for (density in DENSITIES) {
                    for (size in SIZES) {
                        if (size * density > font.pixelGlyphMaxSize) continue
                        for ((name, decoration) in decorations()) {
                            val single = render(session.renderer, density) { Text(LABEL, style = decoration(fill, size)) }
                            val workaround = render(session.renderer, density) {
                                Box {
                                    Text(LABEL, style = decoration(Color.Transparent, size))
                                    Text(LABEL, style = TextStyle(color = fill, size = size.sp))
                                }
                            }
                            for (channel in 0 until CHANNELS) {
                                assertEquals(0, maxDiff(single, workaround, channel), "$backend $name ${size}sp at ${density}x: channel $channel")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun decorations(): List<Pair<String, (Color, Float) -> TextStyle>> = listOf(
        "outlined" to { color, size -> TextStyle(color = color, size = size.sp, outline = outline) },
        "shadowed" to { color, size -> TextStyle(color = color, size = size.sp, shadow = shadow) },
        "both" to { color, size -> TextStyle(color = color, size = size.sp, outline = outline, shadow = shadow) },
    )

    /** A frame of [content] at [density], as `[y][x]` packed RGBA bytes. */
    private fun render(renderer: Renderer, density: Float, content: context(Composer) () -> Unit): Array<IntArray> {
        val primitives = ComposeHost(density = density).frame(FrameInput(WIDTH, HEIGHT), content).primitives
        val target = renderer.createRenderTarget(WIDTH, HEIGHT)
        val data = try {
            renderer.drawUiToTexture(target, primitives, font = font)
            runBlocking { renderer.readPixels(target) }.data
        } finally {
            target.destroy()
        }
        return Array(HEIGHT) { y -> IntArray(WIDTH * CHANNELS) { i -> data[y * WIDTH * CHANNELS + i].toInt() and 0xFF } }
    }

    private fun maxDiff(a: Array<IntArray>, b: Array<IntArray>, channel: Int): Int =
        a.indices.maxOf { y -> (0 until WIDTH).maxOf { x -> kotlin.math.abs(a[y][x * CHANNELS + channel] - b[y][x * CHANNELS + channel]) } }

    /** Where plain text has no ink, how far [decorated] strays from [bare], the decoration drawn with no fill, in any channel. */
    private fun maxDiffOutside(plain: Array<IntArray>, decorated: Array<IntArray>, bare: Array<IntArray>): Int {
        var worst = 0
        for (y in plain.indices) {
            for (x in 0 until WIDTH) {
                if (plain[y][x * CHANNELS + RED] != 0) continue
                for (c in 0 until CHANNELS) worst = maxOf(worst, kotlin.math.abs(decorated[y][x * CHANNELS + c] - bare[y][x * CHANNELS + c]))
            }
        }
        return worst
    }

    private fun solid(image: Array<IntArray>, channel: Int): Int =
        image.sumOf { row -> (0 until WIDTH).count { x -> row[x * CHANNELS + channel] >= SOLID } }

    /** Plain | decorated | decoration alone, one row per case, magnified for a review comment. */
    private fun writeComparison(backend: HeadlessUiBackend, rows: List<List<Array<IntArray>>>) {
        val dir = File(System.getProperty("awake.decoratedText.dir") ?: "build/reports/decorated-text-fill").apply { mkdirs() }
        val image = BufferedImage(WIDTH * 3 * ZOOM, HEIGHT * rows.size * ZOOM, BufferedImage.TYPE_INT_RGB)
        for ((r, row) in rows.withIndex()) {
            for ((c, frame) in row.withIndex()) {
                for (y in 0 until HEIGHT * ZOOM) {
                    for (x in 0 until WIDTH * ZOOM) {
                        val px = frame[y / ZOOM]
                        val i = (x / ZOOM) * CHANNELS
                        image.setRGB(c * WIDTH * ZOOM + x, r * HEIGHT * ZOOM + y, (px[i] shl 16) or (px[i + 1] shl 8) or px[i + 2])
                    }
                }
            }
        }
        val file = File(dir, "decorated-fill-${backend.name.lowercase()}.png")
        ImageIO.write(image, "png", file)
        assertTrue(file.length() > 0, "wrote $file")
        println("Decorated fill comparison: ${file.absolutePath}")
    }

    private companion object {
        const val LABEL = "Harbor 42"
        const val WIDTH = 224
        const val HEIGHT = 56
        const val CHANNELS = 4
        const val RED = 0
        const val SOLID = 250
        const val ZOOM = 2
        val SIZES = listOf(11f, 12f, 13f, 14f)
        val DENSITIES = listOf(1f, 2f, 3f)
    }
}
