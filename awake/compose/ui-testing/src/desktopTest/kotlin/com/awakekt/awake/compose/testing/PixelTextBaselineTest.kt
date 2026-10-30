/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.scale
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFontSamplingMode
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.font.withPixelGlyphMaxSize
import com.awakekt.awake.core.text.theme.TextStyle
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #993's acceptance through the whole Compose path and the CPU rasterizer: which path each size takes,
 * one-pixel stems on whole pixels at 11, 12 and 14 px in Roboto and a project TTF, distance-field text at
 * 48 and 96 px with one-pixel edges and square corners, and 12 sp at density 1.5 drawn at 18 physical px.
 *
 * The baselines pin the pictures; the coverage itself is held against a supersampled reference in
 * `awake:core:text`'s `PixelGlyphsTest`.
 */
class PixelTextBaselineTest {
    private val projectFont: UiFont by lazy {
        UiFonts.load(runBlocking { readResourceBytes("Roboto-Medium.ttf") }, "Roboto-Medium.ttf")
    }
    private val black = Color(0f, 0f, 0f, 1f)
    private val white = Color(1f, 1f, 1f, 1f)
    private val dark = Color(0.09f, 0.09f, 0.11f, 1f)
    private val light = Color(0.96f, 0.96f, 0.97f, 1f)

    private fun frame(width: Int, height: Int, density: Float = 1f, content: context(Composer) () -> Unit): List<UiDrawPrimitive> =
        ComposeHost(density = density).frame(FrameInput(width, height), content).primitives

    private fun glyphFonts(primitives: List<UiDrawPrimitive>): List<UiFont> =
        primitives.filterIsInstance<UiDrawPrimitive.Glyph>().map { requireNotNull(it.font) }

    /** Red channel, `[y][x]`, of [primitives] drawn over [ground]. */
    private fun pixels(primitives: List<UiDrawPrimitive>, width: Int, height: Int, ground: Color = white): Array<IntArray> {
        val data = primitives.rasterize(width, height, background = ground, font = UiFonts.default())
        return Array(height) { y -> IntArray(width) { x -> data[(y * width + x) * 4].toInt() and 0xFF } }
    }

    @Test
    fun smallTextOnTheGridDrawsPixelGlyphsAndEverythingElseTheDistanceField() {
        fun modes(content: context(Composer) () -> Unit) = glyphFonts(frame(200, 60, content = content)).map { it.samplingMode to it.linearCoverage }.distinct()
        val pixel = listOf(UiFontSamplingMode.CoverageAlpha to true)
        val field = listOf(UiFontSamplingMode.DistanceField to false)
        assertEquals(pixel, modes { Text("Label", style = TextStyle(size = 12.sp)) })
        assertEquals(pixel, modes { Text("Label", style = TextStyle(size = 24.sp)) })
        assertEquals(field, modes { Text("Label", style = TextStyle(size = 25.sp)) })
        assertEquals(field, modes { Text("Label", modifier = Modifier.scale(1.5f), style = TextStyle(size = 12.sp)) })
        val optedOut = UiFonts.default().withPixelGlyphMaxSize(0)
        assertEquals(field, modes { Text("Label", style = TextStyle(size = 12.sp, font = optedOut)) })
        assertEquals(listOf(UiFontSamplingMode.CoverageAlpha to false), modes { Text("Label", style = TextStyle(size = 12.sp, font = UiFonts.bitmap())) })
    }

    /** Glyphs drawn 1:1: whole-pixel quads exactly the size of their bitmaps. */
    @Test
    fun pixelGlyphsDrawOnWholePixelsAtTheirOwnSize() {
        val glyphs = frame(240, 40) { Text("Mesh Terrain Texture", style = TextStyle(size = 12.sp)) }.filterIsInstance<UiDrawPrimitive.Glyph>()
        assertTrue(glyphs.isNotEmpty())
        for (glyph in glyphs) {
            val atlas = requireNotNull(glyph.font)
            listOf(glyph.x, glyph.y, glyph.w, glyph.h).forEach { assertEquals(kotlin.math.floor(it), it, "glyph at ${glyph.x}, ${glyph.y}") }
            assertEquals(glyph.w, (glyph.u1 - glyph.u0) * atlas.atlasWidth, 1e-3f)
            assertEquals(glyph.h, (glyph.v1 - glyph.v0) * atlas.atlasHeight, 1e-3f)
        }
    }

    /** A one-stem glyph's stem is one solid column, with at most one anti-aliased column beside it. */
    @Test
    fun oneStemIsOneSolidColumn() {
        for ((name, font) in listOf("default" to null, "Roboto-Medium.ttf" to projectFont)) {
            for (size in listOf(11, 12, 14)) {
                for (label in listOf("l", "I")) {
                    val image = pixels(frame(40, 40) { Text(label, style = TextStyle(color = black, size = size.sp, font = font)) }, 40, 40)
                    val inked = image.indices.filter { y -> image[y].any { it < 255 - FAINT } }
                    val middle = inked[inked.size / 4]..inked[inked.size * 3 / 4]
                    val columns = (0 until 40).map { x -> middle.minOf { y -> image[y][x] } }.filter { it < 255 - FAINT }
                    assertEquals(1, columns.count { it <= 255 - SOLID }, "$name '$label' at $size px: stem columns $columns")
                    assertTrue(columns.size <= 2, "$name '$label' at $size px: stem columns $columns")
                }
            }
        }
    }

    /** 12 sp at density 1.5 rasterizes at 18 physical pixels, not a stretched 12. */
    @Test
    fun densityRasterizesAtThePhysicalSize() {
        val primitives = frame(120, 60, density = 1.5f) { Text("H", style = TextStyle(color = black, size = 12.sp)) }
        val atlases = glyphFonts(primitives).distinct()
        assertEquals(listOf(18), atlases.map { it.cellSize })
        val image = pixels(primitives, 120, 60)
        val capRows = image.indices.filter { y -> image[y].any { it <= 255 - SOLID } }
        assertEquals(13, capRows.last() - capRows.first() + 1, "the 18 px 'H' is ${capRows.size} rows tall")
    }

    /**
     * At 48 and 96 px the distance field draws, with an edge one pixel wide: across a stem's side at most
     * two pixels are neither ground nor ink (the edge lands inside one, or between two). The outer corner
     * of the 'H' stays square: the pixel one in from it is ink.
     */
    @Test
    fun largeTextHasOnePixelEdgesAndSquareCorners() {
        for ((name, font) in listOf("default" to null, "Roboto-Medium.ttf" to projectFont)) {
            for (size in listOf(48, 96)) {
                val width = size * 2
                val height = size * 2
                val primitives = frame(width, height) { Text("H", style = TextStyle(color = black, size = size.sp, font = font)) }
                assertTrue(glyphFonts(primitives).all { it.samplingMode == UiFontSamplingMode.DistanceField }, "$name at $size px")
                val image = pixels(primitives, width, height)
                val inkRows = image.indices.filter { y -> image[y].any { it < 128 } }
                val inkColumns = (0 until width).filter { x -> image.any { it[x] < 128 } }
                val row = image[(inkRows.first() + inkRows.last()) / 2]
                val edge = row.indices.filter { it < inkColumns.first() + 4 && row[it] in EDGE_LOW..EDGE_HIGH }
                assertTrue(edge.size <= 2, "$name at $size px: the stem's left edge spans ${edge.size} pixels: ${edge.map { row[it] }}")
                val corner = image[inkRows.first() + 1][inkColumns.first() + 1]
                assertTrue(corner < CORNER_INK, "$name at $size px: the pixel inside the corner is $corner")
            }
        }
    }

    @Test
    fun smallTextBaseline() {
        val primitives = frame(SHEET_WIDTH, 216) {
            Column {
                for (size in listOf(11, 12, 14)) {
                    Text("Mesh Terrain Texture Illi 0123", style = TextStyle(color = dark, size = size.sp))
                    Text("Mesh Terrain Texture Illi 0123", style = TextStyle(color = dark, size = size.sp, font = projectFont))
                }
                for (size in listOf(11, 12, 14)) {
                    Text("Light on dark Illi 0123", style = TextStyle(color = light, size = size.sp))
                }
            }
        }
        primitives.assertMatchesBaseline("pixel-text-small", SHEET_WIDTH, 216, background = Color(0.5f, 0.5f, 0.52f, 1f))
    }

    @Test
    fun largeTextBaseline() {
        val primitives = frame(SHEET_WIDTH, 300) {
            Column {
                Text("Hg4& Wave", style = TextStyle(color = dark, size = 48.sp))
                Text("Hg4&", style = TextStyle(color = dark, size = 96.sp, font = projectFont))
            }
        }
        primitives.assertMatchesBaseline("pixel-text-large", SHEET_WIDTH, 300, background = light)
    }

    @Test
    fun densityBaseline() {
        val primitives = frame(SHEET_WIDTH, 90, density = 1.5f) {
            Column {
                Text("Mesh Terrain Texture", style = TextStyle(color = dark, size = 12.sp))
                Text("Mesh Terrain Texture", style = TextStyle(color = dark, size = 12.sp, font = projectFont))
            }
        }
        primitives.assertMatchesBaseline("pixel-text-density-1.5", SHEET_WIDTH, 90, background = light)
    }

    private companion object {
        const val SHEET_WIDTH = 320
        const val SOLID = 250
        const val FAINT = 12
        const val EDGE_LOW = 26
        const val EDGE_HIGH = 229

        /** Three quarters ink: a corner rounded by a pixel or more leaves the pixel inside it lighter. */
        const val CORNER_INK = 64
    }
}
