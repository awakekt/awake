/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Text at the fractional densities a desktop's OS display scale gives (#955): Windows' 125% and 150%.
 *
 * The em rounds to whole physical pixels and each line snaps to the pixel grid, so text at 1.25 and 1.5
 * is drawn as sharp as at 1: glyph quads on whole pixels at their bitmaps' own size, and a one-stem
 * glyph's stem solid, with at most one anti-aliased column beside it. The baselines pin the pictures, through the CPU rasterizer with no GPU.
 */
class DisplayScaleTextTest {
    private val black = Color(0f, 0f, 0f, 1f)
    private val dark = Color(0.09f, 0.09f, 0.11f, 1f)
    private val light = Color(0.96f, 0.96f, 0.97f, 1f)

    private fun frame(width: Int, height: Int, density: Float, content: context(Composer) () -> Unit): List<UiDrawPrimitive> =
        ComposeHost(density = density).frame(FrameInput(width, height), content).primitives

    private fun glyphs(primitives: List<UiDrawPrimitive>) = primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

    /** Red channel, `[y][x]`, of [primitives] drawn over white. */
    private fun pixels(primitives: List<UiDrawPrimitive>, width: Int, height: Int): Array<IntArray> {
        val data = primitives.rasterize(width, height, background = Color(1f, 1f, 1f, 1f), font = UiFonts.default())
        return Array(height) { y -> IntArray(width) { x -> data[(y * width + x) * 4].toInt() and 0xFF } }
    }

    @Test
    fun theEmRoundsToWholePhysicalPixels() {
        for (density in DENSITIES) {
            for (size in SIZES) {
                val cells = glyphs(frame(120, 60, density) { Text("H", style = TextStyle(color = black, size = size.sp)) })
                    .map { requireNotNull(it.font).cellSize }.distinct()
                assertEquals(listOf((size * density).roundToInt()), cells, "$size sp at density $density")
            }
        }
    }

    @Test
    fun glyphsAndLinesLandOnWholePixels() {
        for (density in DENSITIES) {
            val primitives = frame(400, 120, density) {
                Column {
                    for (size in SIZES) Text("Mesh Terrain Texture", style = TextStyle(size = size.sp))
                }
            }
            val glyphs = glyphs(primitives)
            assertTrue(glyphs.isNotEmpty())
            for (glyph in glyphs) {
                val atlas = requireNotNull(glyph.font)
                listOf(glyph.x, glyph.y, glyph.w, glyph.h).forEach {
                    assertEquals(floor(it), it, "density $density: glyph at ${glyph.x}, ${glyph.y}")
                }
                assertEquals(glyph.w, (glyph.u1 - glyph.u0) * atlas.atlasWidth, 1e-3f, "density $density: drawn at its own size")
            }
        }
    }

    @Test
    fun oneStemIsOneSolidColumn() {
        for (density in DENSITIES) {
            for (size in SIZES) {
                for (label in listOf("l", "I")) {
                    val image = pixels(frame(60, 60, density) { Text(label, style = TextStyle(color = black, size = size.sp)) }, 60, 60)
                    val inked = image.indices.filter { y -> image[y].any { it < 255 - FAINT } }
                    val middle = inked[inked.size / 4]..inked[inked.size * 3 / 4]
                    val columns = (0 until 60).map { x -> middle.minOf { y -> image[y][x] } }.filter { it < 255 - FAINT }
                    val where = "'$label' at $size sp, density $density: stem columns $columns"
                    val solid = columns.count { it <= 255 - SOLID }
                    assertTrue(solid >= 1, where)
                    assertTrue(columns.size - solid <= 1, "at most one grey column beside the stem: $where")
                }
            }
        }
    }

    @Test
    fun baselineAt125() = sheet(1.25f, "display-scale-text-1.25")

    @Test
    fun baselineAt150() = sheet(1.5f, "display-scale-text-1.5")

    private fun sheet(density: Float, name: String) {
        val width = (SHEET_WIDTH_DP * density).roundToInt()
        val height = (SHEET_HEIGHT_DP * density).roundToInt()
        val primitives = frame(width, height, density) {
            Column {
                for (size in SIZES) {
                    Text("Mesh Terrain Texture Illi 0123", style = TextStyle(color = dark, size = size.sp))
                }
            }
        }
        primitives.assertMatchesBaseline(name, width, height, background = light)
    }

    private companion object {
        /** Windows' 125% and 150% display scale. */
        val DENSITIES = listOf(1.25f, 1.5f)
        val SIZES = listOf(11, 12, 14)
        const val SHEET_WIDTH_DP = 240
        const val SHEET_HEIGHT_DP = 60
        const val SOLID = 250
        const val FAINT = 12
    }
}
