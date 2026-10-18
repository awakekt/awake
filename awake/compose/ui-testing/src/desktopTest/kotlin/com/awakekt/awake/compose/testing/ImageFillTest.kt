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
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.FillDirection
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.compose.ui.graphics.drawImageFillBar
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
    fun artAtFewerPixelsPerDpDrawsLargerAndKeepsItsPitch() {
        // Half a pixel per dp: each source pixel is two on screen, the corners 4 and the stripes 2 wide.
        val fill = ImageFill(FRAME, sliceLeft = 2, sliceTop = 2, sliceRight = 2, sliceBottom = 2, repeatEdges = true, pixelsPerDp = 0.5f, filterQuality = FilterQuality.None)
        val pixels = draw(fill, 24, 24)

        for (i in 0..3) assertEquals(RED, pixels[i][i], "the corner is 4 wide, at ($i, $i)")
        assertEquals(List(16) { if (it / 2 % 2 == 0) BLACK else WHITE }, pixels[0].subList(4, 20), "the top edge repeats every 2")
        assertEquals(GREEN, pixels[0][20])
    }

    @Test
    fun highDensityArtKeepsItsCornersAtTheirSizeInDp() {
        // Two pixels per dp: the 2-pixel corners are 1 dp, so the edge starts at the second pixel.
        val fill = ImageFill(FRAME, sliceLeft = 2, sliceTop = 2, sliceRight = 2, sliceBottom = 2, pixelsPerDp = 2f, filterQuality = FilterQuality.None)
        val pixels = draw(fill, 20, 20)

        assertEquals(RED, pixels[0][0])
        assertTrue(pixels[0][1] == BLACK || pixels[0][1] == WHITE, "the edge starts after a 1-dp corner")
        assertEquals(GREEN, pixels[0][19])
        assertEquals(YELLOW, pixels[19][19])
        assertFailsWith<IllegalArgumentException> { ImageFill(FRAME, pixelsPerDp = 0f) }
        assertFailsWith<IllegalArgumentException> { ImageFill(FRAME, pixelsPerDp = Float.NaN) }
    }

    @Test
    fun aHollowFrameLeavesItsCentreOut() {
        val hollow = ImageFill(FRAME, sliceLeft = 2, sliceTop = 2, sliceRight = 2, sliceBottom = 2, hollow = true)
        val frame = composeFrame(20, 20) { Box(Modifier.size(20.dp).background(hollow)) }
        val pixels = draw(hollow, 20, 20)

        assertEquals(8, frame.primitives.filterIsInstance<UiDrawPrimitive.Texture>().size, "four corners and four edges")
        assertEquals(RED, pixels[0][0])
        assertTrue(pixels.subList(2, 18).all { row -> row.subList(2, 18).none { it == CYAN || it == MAGENTA } }, "no centre")
    }

    @Test
    fun aBarKeepsBothCapsWholeAtEveryValue() {
        for (percent in listOf(1, 5, 25, 50, 99, 100)) {
            val value = percent / 100f
            val row = drawBar(value, FillDirection.LeftToRight, 30, 4)[2]
            val length = 4 + kotlin.math.round(value * 26).toInt()

            assertEquals(listOf(RED, RED), row.subList(0, 2), "$percent%: the left cap")
            assertEquals(listOf(GREEN, GREEN), row.subList(length - 2, length), "$percent%: the right cap ends the fill")
            assertTrue(row.subList(length, 30).all { it == TRACK }, "$percent%: the rest shows the track")
        }
        assertTrue(drawBar(0f, FillDirection.LeftToRight, 30, 4).all { row -> row.all { it == TRACK } }, "0 draws nothing")
    }

    @Test
    fun aBarFillsFromTheEdgeItsDirectionNames() {
        val leftward = drawBar(0.5f, FillDirection.RightToLeft, 30, 4)[2]
        assertEquals(listOf(GREEN, GREEN), leftward.subList(28, 30), "the right cap sits at the right edge")
        assertEquals(listOf(RED, RED), leftward.subList(13, 15), "and the left one at the value")
        assertEquals(TRACK, leftward[12])

        val upward = drawBar(0.5f, FillDirection.BottomToTop, 4, 30, vertical = true).map { it[2] }
        assertEquals(listOf(GREEN, GREEN), upward.subList(28, 30), "the bottom cap sits at the bottom")
        assertEquals(listOf(RED, RED), upward.subList(13, 15), "the top cap at the value")
        assertEquals(TRACK, upward[12])
    }

    @Test
    fun aBarShorterThanItsCapsSqueezesThem() {
        val row = drawBar(1f, FillDirection.LeftToRight, 2, 4)[2]

        assertEquals(listOf(RED, GREEN), row)
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

    @Test
    fun hollowHighDensityFramesAndCappedBarsMatchTheirBaseline() {
        val hollow = ImageFill(WINDOW, sliceLeft = 8, sliceTop = 8, sliceRight = 8, sliceBottom = 8, hollow = true, filterQuality = FilterQuality.None)
        val sharp = ImageFill(WINDOW, sliceLeft = 8, sliceTop = 8, sliceRight = 8, sliceBottom = 8, repeatEdges = true, pixelsPerDp = 2f, filterQuality = FilterQuality.None)
        val strip = ImageFill(WINDOW, srcY = 8, srcHeight = 8, sliceLeft = 8, sliceRight = 8, filterQuality = FilterQuality.None)
        val column = ImageFill(WINDOW, srcX = 8, srcWidth = 8, sliceTop = 8, sliceBottom = 8, filterQuality = FilterQuality.None)
        val track = Color(0.05f, 0.05f, 0.08f, 1f)

        composeFrame(424, 104) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedByHorizontal(16.dp)) {
                Box(Modifier.size(96.dp, 72.dp).background(Color(0.2f, 0.5f, 0.3f, 1f)).background(hollow))
                Box(Modifier.size(96.dp, 72.dp).background(sharp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (value in listOf(0.02f, 0.5f, 1f)) {
                        Box(Modifier.size(120.dp, 16.dp).background(track).drawBehind { drawImageFillBar(strip, value) })
                    }
                }
                Box(Modifier.size(16.dp, 72.dp).background(track).drawBehind { drawImageFillBar(column, 0.3f, FillDirection.BottomToTop) })
            }
        }.assertMatchesBaseline("image-fill-hollow-dpi-bars", 424, 104)
    }

    /** A [width] x [height] bar of [BAR] filled to [value] from [direction]'s edge over [TRACK], as rows of 0xRRGGBB. */
    private fun drawBar(value: Float, direction: FillDirection, width: Int, height: Int, vertical: Boolean = false): List<List<Int>> {
        val fill = if (vertical) {
            ImageFill(BAR_DOWN, sliceTop = 2, sliceBottom = 2, filterQuality = FilterQuality.None)
        } else {
            ImageFill(BAR, sliceLeft = 2, sliceRight = 2, filterQuality = FilterQuality.None)
        }
        val frame = composeFrame(width, height) {
            Box(Modifier.size(width.dp, height.dp).drawBehind { drawImageFillBar(fill, value, direction) })
        }
        return rows(frame.primitives.rasterize(width, height, Color.Black), width, height)
    }

    /** [fill] drawn at [width] x [height] dp, as rows of 0xRRGGBB. */
    private fun draw(fill: ImageFill, width: Int, height: Int): List<List<Int>> {
        val frame = composeFrame(width, height) { Box(Modifier.size(width.dp, height.dp).background(fill)) }
        return rows(frame.primitives.rasterize(width, height), width, height)
    }

    /** Tightly packed RGBA [pixels], [width] x [height], as rows of 0xRRGGBB. */
    private fun rows(pixels: ByteArray, width: Int, height: Int): List<List<Int>> = List(height) { y ->
        List(width) { x ->
            val offset = (y * width + x) * 4
            ((pixels[offset].toInt() and 0xFF) shl 16) or ((pixels[offset + 1].toInt() and 0xFF) shl 8) or (pixels[offset + 2].toInt() and 0xFF)
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
        const val TRACK = 0x000000

        /** 6 x 1: red caps of 2 on the left, green of 2 on the right, cyan between. */
        val BAR = ImageBitmap(6, 1, rgba(listOf(listOf(RED, RED, CYAN, CYAN, GREEN, GREEN))))

        /** [BAR] stood on end: red at the top, green at the bottom. */
        val BAR_DOWN = ImageBitmap(1, 6, rgba(listOf(RED, RED, CYAN, CYAN, GREEN, GREEN).map { listOf(it) }))

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
