/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.scale
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Small UI text lands on the pixel grid on both backends, at 100%, 125% and 150% display scale.
 *
 * Each case draws `HHHH`, whose flat bottom sits on the baseline, so a baseline on a whole pixel is
 * one solid row and nothing under it, and one on a fraction is a half-covered row. The line height
 * is swept through fractions of a pixel: before the line's baseline was snapped, each value put the
 * baseline on a different phase and drew a different row of pixels.
 *
 * Negative controls, measured on the code before this change (Vulkan): at 100% the last solid row
 * peaked at 215 to 230 of 255 with 0 to 188 on the row under it, across the sweep, and 11px
 * "Mesh Terrain Texture" had 6 solid pixels against 179 now.
 */
class UiTextCrispnessParityTest {
    private val white = Color(1f, 1f, 1f, 1f)
    private val backends = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)

    /** Red channel of a frame of [content] at [density], indexed `[y][x]`. */
    private fun render(backend: HeadlessUiBackend, density: Float, content: context(Composer) () -> Unit): Array<IntArray> {
        val primitives = ComposeHost(density = density).frame(FrameInput(WIDTH, HEIGHT), content).primitives
        val data = withHeadlessUi(backend, WIDTH) { renderer ->
            val target = renderer.createRenderTarget(WIDTH, HEIGHT)
            try {
                renderer.drawUiToTexture(target, primitives, font = UiFonts.default())
                runBlocking { renderer.readPixels(target) }.data
            } finally {
                target.destroy()
            }
        }
        return Array(HEIGHT) { y -> IntArray(WIDTH) { x -> data[(y * WIDTH + x) * 4].toInt() and 0xFF } }
    }

    private fun solidPixels(image: Array<IntArray>): Int = image.sumOf { row -> row.count { it >= SOLID } }

    /** The brightest pixel of the lowest solid row, and of the row under it: a whole baseline is (255, 0). */
    private fun bottomEdge(image: Array<IntArray>): Pair<Int, Int> {
        val lastSolid = image.indices.last { y -> image[y].any { it >= SOLID } }
        val under = if (lastSolid + 1 < image.size) image[lastSolid + 1].max() else 0
        return image[lastSolid].max() to under
    }

    private fun hhhh(density: Float, size: Float, lineHeight: Float, backend: HeadlessUiBackend): Array<IntArray> =
        render(backend, density) {
            Text("HHHH", style = TextStyle(color = white, size = size.sp, lineHeight = lineHeight.sp))
        }

    /** At 100% a flat bottom is one solid row, however the line box divides its spare height. */
    @Test
    fun aFlatGlyphBottomIsOneSolidRowAtWholeScale() {
        for (backend in backends) {
            for (extra in SWEEP) {
                val (solid, under) = bottomEdge(hhhh(1f, 12f, 16.2f + extra, backend))

                assertTrue(solid >= 250, "$backend: the last solid row peaks at $solid with line height +$extra")
                assertTrue(under <= 5, "$backend: the row under the baseline reaches $under with line height +$extra")
            }
        }
    }

    /**
     * At 125% and 150% the em is a whole 16, 15 or 18 pixels and the baseline is a whole row, so
     * the picture does not depend on the fraction the line box leaves over.
     */
    @Test
    fun theBaselineDoesNotDependOnTheLineBoxAtFractionalScale() {
        for (backend in backends) {
            for ((density, size) in listOf(1.25f to 13f, 1.5f to 12f, 1.25f to 12f)) {
                val seen = SWEEP.map { extra ->
                    val image = hhhh(density, size, size * 1.35f + extra, backend)
                    val (solid, under) = bottomEdge(image)
                    listOf(solid, under, solidPixels(image))
                }

                // Within a few levels: a whole baseline can still shift the glyph's edge by a
                // sliver of a texel, which WebGPU shades two levels apart.
                for (metric in 0 until 3) {
                    val spread = seen.maxOf { it[metric] } - seen.minOf { it[metric] }
                    assertTrue(spread <= TOLERANCE, "$backend at $density: ${size}sp metric $metric moved by $spread across line heights: $seen")
                }
            }
        }
    }

    /**
     * The pixel shows it: text under a 1.5x scale keeps its fractional baseline, 14.78 here, so its
     * bottom row is partly covered and peaks well under the 255 of a whole one.
     */
    @Test
    fun scaledTextKeepsItsFractionalBaseline() {
        for (backend in backends) {
            val image = render(backend, 1f) {
                Text("HHHH", modifier = Modifier.scale(1.5f), style = TextStyle(color = white, size = 12.sp, lineHeight = 17.5f.sp))
            }
            val lowest = image.indices.last { y -> image[y].any { it > FAINT } }

            assertTrue(image[lowest].max() <= 245, "$backend: the scaled bottom row peaks at ${image[lowest].max()}, a whole-pixel baseline would be 255")
        }
    }

    /** A real 11px label through the Compose path: the atlas is sampled at its base level. */
    @Test
    fun elevenPixelTextIsSolidOnVulkan() {
        val image = render(HeadlessUiBackend.Vulkan, 1f) {
            Text("Mesh Terrain Texture", style = TextStyle(color = white, size = 11.sp))
        }

        assertTrue(solidPixels(image) >= 150, "11px text has ${solidPixels(image)} solid pixels")
    }

    private companion object {
        const val WIDTH = 160
        const val HEIGHT = 48
        const val FAINT = 30
        const val SOLID = 200
        const val TOLERANCE = 4

        /** Extra line height, in sp, that moves the unsnapped baseline through the fractions of a pixel. */
        val SWEEP = listOf(0f, 0.4f, 0.8f, 1.2f, 1.6f)
    }
}
