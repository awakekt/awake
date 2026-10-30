/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.host.readResourceBytes
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The effect field outlines and soft shadows draw from (#1061), built in and baked at load alike. */
class EffectFieldTest {
    private val regular = PackedUiFont(RobotoRegularUiFontData)

    @Test
    fun theFieldSitsUnderTheAtlasWhoseTexelsKeepTheirPlace() {
        val data = RobotoRegularUiFontData
        assertEquals(data.atlasHeight + data.effectAtlasHeight, regular.atlasHeight)
        assertEquals(maxOf(data.atlasWidth, data.effectAtlasWidth), regular.atlasWidth)
        val glyphs = data.atlasBytes()
        val texture = regular.atlasPixelsRgba
        for (row in 0 until data.atlasHeight) {
            for (column in 0 until data.atlasWidth * 4) {
                assertEquals(glyphs[row * data.atlasWidth * 4 + column], texture[row * regular.atlasWidth * 4 + column], "texel row $row")
            }
        }
    }

    @Test
    fun everyInkedGlyphHasATileReachingHalfAnEmPastItsInk() {
        assertEquals(16f, regular.effectFieldRangePx)
        for (char in "OW8gH@") {
            val tile = assertNotNull(regular.effectGlyphFor(char.code, FontWeight.Normal), "'$char'")
            val ink = assertNotNull(regular.inkFor(char))
            assertTrue(tile.offsetXEm <= ink.offsetXEm - 0.5f + 1f / 16 && tile.v0 * regular.atlasHeight >= RobotoRegularUiFontData.atlasHeight, "'$char': $tile")
            assertTrue(tile.offsetXEm + tile.widthEm >= ink.offsetXEm + ink.widthEm + 0.5f - 1f / 16, "'$char': $tile")
        }
        assertNull(regular.effectGlyphFor(' '.code, FontWeight.Normal), "a space has no ink to decorate")
    }

    /** Across 'O': outside at the tile's edge, inside on the ring, outside again in the counter. */
    @Test
    fun theFieldReadsInsideOnTheInkAndOutsideAroundIt() {
        val tile = assertNotNull(regular.effectGlyphFor('O'.code, FontWeight.Normal))
        val row = ((tile.v0 + tile.v1) / 2 * regular.atlasHeight).toInt()
        val left = (tile.u0 * regular.atlasWidth).toInt()
        val right = (tile.u1 * regular.atlasWidth).toInt()
        val values = (left until right).map { regular.atlasPixelsRgba[(row * regular.atlasWidth + it) * 4 + 3].toInt() and 0xFF }
        assertTrue(values.first() < 8 && values.last() < 8, "the reach ends at the tile's edge: $values")
        val middle = values[values.size / 2]
        assertTrue(middle < 128, "the counter is outside: $values")
        assertTrue(values.count { it > 128 } >= 2, "the ring is inside: $values")
    }

    /** [UiFonts.load] bakes what the generator's msdfgen bakes, tile for tile from each glyph's pen origin. */
    @Test
    fun aLoadedFontBakesTheGeneratorsField() = runTest {
        val loaded = OpenTypeUiFontData.load(readResourceBytes("Roboto-Regular.ttf"), "Regular", UiFonts.DEFAULT_CHARS, DistanceFieldBake.GENERATOR)
        val baked = RobotoRegularUiFontData
        val mine = assertNotNull(loaded.effectFieldBytes())
        val theirs = assertNotNull(baked.effectFieldBytes())
        assertEquals(baked.effectFieldRangePx, loaded.effectFieldRangePx)
        var compared = 0
        var close = 0
        for (glyph in loaded.glyphOrder.indices) {
            if (loaded.effectQuadMetricsEm[glyph * 4 + 2] <= 0f) continue
            val (loadedX, loadedY) = penOf(loaded, glyph)
            val (bakedX, bakedY) = penOf(baked, glyph)
            val left = maxOf(loaded.effectUvBoundsPx[glyph * 4] - loadedX, baked.effectUvBoundsPx[glyph * 4] - bakedX)
            val top = maxOf(loaded.effectUvBoundsPx[glyph * 4 + 1] - loadedY, baked.effectUvBoundsPx[glyph * 4 + 1] - bakedY)
            val right = minOf(loaded.effectUvBoundsPx[glyph * 4 + 2] - loadedX, baked.effectUvBoundsPx[glyph * 4 + 2] - bakedX)
            val bottom = minOf(loaded.effectUvBoundsPx[glyph * 4 + 3] - loadedY, baked.effectUvBoundsPx[glyph * 4 + 3] - bakedY)
            for (y in top until bottom) {
                for (x in left until right) {
                    val ours = mine[(loadedY + y) * loaded.effectAtlasWidth + loadedX + x].toInt() and 0xFF
                    val theirsHere = theirs[(bakedY + y) * baked.effectAtlasWidth + bakedX + x].toInt() and 0xFF
                    compared++
                    if (abs(ours - theirsHere) <= 3) close++
                }
            }
        }
        val share = close.toDouble() / compared
        assertTrue(compared > 100_000 && share >= 0.98, "only ${share * 100}% of $compared texels within 3/255 of msdfgen's")
    }

    /** Where [glyph]'s pen origin sits in [data]'s effect field: its tile's corner less the tile's offset from the pen. */
    private fun penOf(data: PackedUiFontData, glyph: Int): Pair<Int, Int> =
        (data.effectUvBoundsPx[glyph * 4] - (data.effectQuadMetricsEm[glyph * 4] * EffectFieldLayout.TEXELS_PER_EM).roundToInt()) to
            (data.effectUvBoundsPx[glyph * 4 + 1] - (data.effectQuadMetricsEm[glyph * 4 + 1] * EffectFieldLayout.TEXELS_PER_EM).roundToInt())
}
