/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.testing

import io.github.awakelab.awake.compose.foundation.text.Text
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.math2d.sp
import io.github.awakelab.awake.core.text.font.FontWeight
import io.github.awakelab.awake.core.text.font.UiFonts
import io.github.awakelab.awake.core.text.theme.TextStyle
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/** Compares rendered ink geometry with the pinned Chromium Roboto captures. */
class FontReferenceFidelityTest {

    private val repoRoot: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "docs/reference/font-previews").isDirectory }

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
    }

    private fun reference(id: String): Bounds {
        val image = ImageIO.read(File(repoRoot, "docs/reference/font-previews/$id.png"))
        return bounds(image.width, image.height) { x, y ->
            val pixel = image.getRGB(x, y)
            ((pixel ushr 16) and 0xFF) > 20 || ((pixel ushr 8) and 0xFF) > 20 || (pixel and 0xFF) > 20
        }
    }

    private fun awake(text: String, size: Float): Bounds {
        val width = 640
        val height = 96
        val frame = composeFrame(width, height) {
            Text(text, style = TextStyle(size = size.sp, weight = FontWeight.Normal))
        }
        val pixels = frame.primitives.rasterize(
            width,
            height,
            background = Color.Black,
            font = UiFonts.default(),
        )
        return bounds(width, height) { x, y ->
            (pixels[(y * width + x) * 4].toUByte().toInt() > 20) ||
                (pixels[(y * width + x) * 4 + 1].toUByte().toInt() > 20) ||
                (pixels[(y * width + x) * 4 + 2].toUByte().toInt() > 20)
        }
    }

    private fun bounds(width: Int, height: Int, isInk: (x: Int, y: Int) -> Boolean): Bounds {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!isInk(x, y)) continue
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        require(right >= left && bottom >= top) { "font capture contains no ink" }
        return Bounds(left, top, right, bottom)
    }

    @Test
    fun roundVsFlatInkBoundsStayWithinOnePixelOfChromium() {
        val samples = listOf(
            "roundvsflat-12" to 12f,
            "roundvsflat-14" to 14f,
            "roundvsflat-16" to 16f,
        )
        samples.forEach { (id, size) ->
            val expected = reference(id)
            val actual = awake("iliaeco", size)
            assertTrue(
                kotlin.math.abs(actual.width - expected.width) <= 1 &&
                    kotlin.math.abs(actual.height - expected.height) <= 1,
                "$id ink bounds differ: expected ${expected.width}x${expected.height}, " +
                    "actual ${actual.width}x${actual.height}",
            )
        }
    }
}
