/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.grid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The summary is what a route across unloaded ground is decided from, so its two claims have to
 * hold exactly: separate areas stay separate, and a blocked edge is never reported as a crossing.
 */
class NavCellSummaryTest {

    private fun tile(vararg rows: String): NavGridTile {
        val width = rows.first().length
        val bits = LongArray((width * rows.size + NavGridTile.LONG_MASK) ushr NavGridTile.LONG_SHIFT)
        for (z in rows.indices) {
            for (x in 0 until width) {
                if (rows[z][x] != '.') continue
                val bit = z * width + x
                bits[bit ushr NavGridTile.LONG_SHIFT] =
                    bits[bit ushr NavGridTile.LONG_SHIFT] or (1L shl (bit and NavGridTile.LONG_MASK))
            }
        }
        return NavGridTile(width, rows.size, cellSize = 1f, walkable = bits)
    }

    @Test
    fun anOpenCellIsOneRegionOpenOnEverySide() {
        val summary = tile("....", "....", "....", "....").summarize()

        assertEquals(1, summary.regionCount)
        CellSide.entries.forEach { side -> assertTrue(summary.isOpen(side), "$side should be open.") }
    }

    @Test
    fun aWallSplitsACellIntoTwoRegions() {
        val summary = tile("..#.", "..#.", "..#.", "..#.").summarize()

        assertEquals(2, summary.regionCount)
        assertNotEquals(
            summary.regionAt(CellSide.West, 0),
            summary.regionAt(CellSide.East, 0),
            "The two sides of a wall must not share a region.",
        )
    }

    @Test
    fun aBlockedEdgeReportsNoRegion() {
        val summary = tile("####", "....", "....", "....").summarize()

        assertFalse(summary.isOpen(CellSide.North))
        repeat(4) { index ->
            assertEquals(NavCellSummary.NO_REGION, summary.regionAt(CellSide.North, index))
        }
        assertTrue(summary.isOpen(CellSide.South))
    }

    /**
     * A diagonal pinch the fine search refuses to step through must not merge two regions here
     * either, or the corridor claims a crossing the local path can never make.
     */
    @Test
    fun aDiagonalPinchDoesNotMergeRegions() {
        val summary = tile(".#", "#.").summarize()

        assertEquals(2, summary.regionCount)
    }

    @Test
    fun aCellWithNoWalkableSampleHasNoRegions() {
        val summary = tile("##", "##").summarize()

        assertEquals(0, summary.regionCount)
        CellSide.entries.forEach { side -> assertFalse(summary.isOpen(side)) }
    }
}
