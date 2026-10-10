/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImageFillTest {

    @Test
    fun cornersKeepTheirPixelsAtEverySize() {
        for ((width, height) in listOf(20 to 20, 37 to 13, 64 to 40, 5 to 5)) {
            val pixels = draw(ImageFill(FRAME, sliceLeft = 2, sliceTop = 2, sliceRight = 2, sliceBottom = 2), width, height)
            for (dx in 0..1) {
                for (dy in 0..1) {
                    assertEquals(RED, pixels[dy][dx], "top-left corner at $width x $height")
                    assertEquals(GREEN, pixels[dy][width - 2 + dx], "top-right corner at $width x $height")
                    assertEquals(BLUE, pixels[height - 2 + dy][dx], "bottom-left corner at $width x $height")
                    assertEquals(YELLOW, pixels[height - 2 + dy][width - 2 + dx], "bottom-right corner at $width x $height")
                }
            }
        }
    }

    @Test
    fun cornersTooBigForTheRectangleShrinkUntilTheyMeet() {
        val pixels = draw(ImageFill(FRAME, sliceLeft = 2, sliceTop = 2, sliceRight = 2, sliceBottom = 2), 2, 2)

        assertEquals(listOf(listOf(RED, GREEN), listOf(BLUE, YELLOW)), pixels)
    }

    @Test
    fun repeatKeepsTheStripesPitchAndStretchWidensIt() {
        // 41 wide leaves 37 for the top edge: 18 whole tiles and half of one, cut at the corner.
        val repeated = draw(ImageFill(FRAME, sliceLeft = 2, sliceTop = 2, sliceRight = 2, sliceBottom = 2, repeatEdges = true), 41, 21)
        val stretched = draw(ImageFill(FRAME, sliceLeft = 2, sliceTop = 2, sliceRight = 2, sliceBottom = 2), 42, 21)

        assertEquals(List(37) { if (it % 2 == 0) BLACK else WHITE }, repeated[0].subList(2, 39), "top edge, one source pixel per dp")
        assertEquals(List(17) { if (it % 2 == 0) BLACK else WHITE }, repeated.map { it[0] }.subList(2, 19), "left edge, down")
        assertEquals(GREEN, repeated[0][39], "the cut tile stops at the corner")
        assertEquals(listOf(19, 19), runs(stretched[0].subList(2, 40)), "stretched, each stripe spans half the edge")
    }

    @Test
    fun repeatCentreTilesBothWays() {
        val pixels = draw(ImageFill(FRAME, sliceLeft = 2, sliceTop = 2, sliceRight = 2, sliceBottom = 2, repeatCenter = true), 10, 10)

        for (y in 2..7) {
            assertEquals(List(6) { x -> if ((x + y) % 2 == 0) CYAN else MAGENTA }, pixels[y].subList(2, 8), "row $y")
        }
    }

    @Test
    fun aStripIsTheSameCutWithNoTopOrBottom() {
        val strip = ImageBitmap(6, 1, rgba(listOf(listOf(RED, RED, BLACK, WHITE, GREEN, GREEN))))
        val pixels = draw(ImageFill(strip, sliceLeft = 2, sliceRight = 2), 30, 10)

        for (row in pixels) {
            assertEquals(listOf(RED, RED), row.subList(0, 2), "the left cap keeps its width all the way down")
            assertEquals(listOf(GREEN, GREEN), row.subList(28, 30), "and the right cap")
            assertEquals(listOf(13, 13), runs(row.subList(2, 28)), "the middle stretches")
        }
    }

    @Test
    fun aRegionOfASheetFillsAlone() {
        // The frame's top-right corner, stretched.
        val pixels = draw(ImageFill(FRAME, srcX = 4, srcY = 0, srcWidth = 2, srcHeight = 2), 8, 8)

        assertTrue(pixels.all { row -> row.all { it == GREEN } })
    }

    @Test
    fun tintAndFilterReachEveryPiece() {
        val tint = Color(1f, 0.5f, 0.25f, 1f)
        val frame = composeFrame(40, 40) {
            Box(Modifier.size(40.dp).background(ImageFill(FRAME, sliceLeft = 2, sliceTop = 2, sliceRight = 2, sliceBottom = 2, tint = tint, filterQuality = FilterQuality.None)))
        }
        val pieces = frame.primitives.filterIsInstance<UiDrawPrimitive.Texture>()

        assertEquals(9, pieces.size)
        assertTrue(pieces.all { it.tint == tint && it.filterQuality == FilterQuality.None })
    }

    @Test
    fun aRegionOrSliceOutsideTheImageIsRefused() {
        assertFailsWith<IllegalArgumentException> { ImageFill(FRAME, srcX = 4, srcWidth = 4) }
        assertFailsWith<IllegalArgumentException> { ImageFill(FRAME, sliceLeft = 4, sliceRight = 3) }
        assertFailsWith<IllegalArgumentException> { ImageFill(FRAME, sliceTop = -1) }
    }

    @Test
    fun framesTilesAndStripsMatchTheirBaseline() {
        val frame = { repeat: Boolean -> ImageFill(WINDOW, sliceLeft = 8, sliceTop = 8, sliceRight = 8, sliceBottom = 8, repeatEdges = repeat, repeatCenter = repeat, filterQuality = FilterQuality.None) }
        val strip = ImageFill(WINDOW, srcY = 8, srcHeight = 8, sliceLeft = 8, sliceRight = 8, filterQuality = FilterQuality.None)

        composeFrame(424, 104) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedByHorizontal(16.dp)) {
                Box(Modifier.size(120.dp, 72.dp).background(frame(false)))
                Box(Modifier.size(120.dp, 72.dp).background(frame(true)))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(120.dp, 16.dp).background(strip))
                    Box(Modifier.size(60.dp, 16.dp).background(strip))
                }
            }
        }.assertMatchesBaseline("image-fill", 424, 104)
    }

    /** [fill] drawn at [width] x [height] dp, as rows of 0xRRGGBB. */
    private fun draw(fill: ImageFill, width: Int, height: Int): List<List<Int>> {
        val frame = composeFrame(width, height) { Box(Modifier.size(width.dp, height.dp).background(fill)) }
        val pixels = frame.primitives.rasterize(width, height)
        return List(height) { y ->
            List(width) { x ->
                val offset = (y * width + x) * 4
                ((pixels[offset].toInt() and 0xFF) shl 16) or ((pixels[offset + 1].toInt() and 0xFF) shl 8) or (pixels[offset + 2].toInt() and 0xFF)
            }
        }
    }

    /** The lengths of the runs of one colour along [row]. */
    private fun runs(row: List<Int>): List<Int> = buildList {
        var length = 1
        for (i in 1 until row.size) {
            if (row[i] == row[i - 1]) {
                length++
            } else {
                add(length)
                length = 1
            }
        }
        add(length)
    }

    private companion object {
        const val RED = 0xFF0000
        const val GREEN = 0x00FF00
        const val BLUE = 0x0000FF
        const val YELLOW = 0xFFFF00
        const val BLACK = 0x000000
        const val WHITE = 0xFFFFFF
        const val CYAN = 0x00FFFF
        const val MAGENTA = 0xFF00FF

        /**
         * 6 x 6 with 2-pixel corners: red, green, blue, yellow. Every edge is striped one pixel black,
         * one white, along its length; the centre is a cyan and magenta checker.
         */
        val FRAME = ImageBitmap(
            6,
            6,
            rgba(
                listOf(
                    listOf(RED, RED, BLACK, WHITE, GREEN, GREEN),
                    listOf(RED, RED, BLACK, WHITE, GREEN, GREEN),
                    listOf(BLACK, BLACK, CYAN, MAGENTA, BLACK, BLACK),
                    listOf(WHITE, WHITE, MAGENTA, CYAN, WHITE, WHITE),
                    listOf(BLUE, BLUE, BLACK, WHITE, YELLOW, YELLOW),
                    listOf(BLUE, BLUE, BLACK, WHITE, YELLOW, YELLOW),
                ),
            ),
        )

        /**
         * 24 x 24 with 8-pixel gold corners. The edges are striped two pixels at a time along their
         * length, and the centre is a dotted blue, so stretching and tiling look different.
         */
        val WINDOW = ImageBitmap(
            24,
            24,
            rgba(
                List(24) { y ->
                    List(24) { x ->
                        val cornerColumn = x < 8 || x >= 16
                        val cornerRow = y < 8 || y >= 16
                        when {
                            cornerColumn && cornerRow -> if (minOf(x, y, 23 - x, 23 - y) == 0) 0x3A2A10 else 0xE0B040
                            cornerRow -> if (x / 2 % 2 == 0) 0x8A5A2B else 0xD9B48A
                            cornerColumn -> if (y / 2 % 2 == 0) 0x8A5A2B else 0xD9B48A
                            else -> if ((x + y) % 4 == 0) 0x6A8AC0 else 0x1E2A44
                        }
                    }
                },
            ),
        )

        fun rgba(rows: List<List<Int>>): ByteArray = rows.flatten().flatMap { rgb ->
            listOf((rgb shr 16).toByte(), (rgb shr 8).toByte(), rgb.toByte(), -1)
        }.toByteArray()
    }
}
