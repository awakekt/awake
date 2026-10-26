/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.fontatlasgenerator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The packing the committed atlases are laid out by. `awake:core:text`'s `OpenTypeUiFontTest` holds its own
 * copy of the algorithm to the committed atlases' real tiles, so the two stay one algorithm.
 */
class ShelfPackingTest {
    @Test
    fun noTileOverlapsAnotherOrLeavesTheAtlas() {
        val random = java.util.Random(1058)
        val widths = IntArray(200) { 5 + random.nextInt(30) }
        val heights = IntArray(200) { 4 + random.nextInt(40) }
        val packing = ShelfPacking.pack(widths, heights)
        assertEquals(0, packing.width % ShelfPacking.WIDTH_STEP)
        for (a in widths.indices) {
            assertTrue(packing.xs[a] >= 0 && packing.ys[a] >= 0, "tile $a starts inside")
            assertTrue(packing.xs[a] + widths[a] <= packing.width && packing.ys[a] + heights[a] <= packing.height, "tile $a ends inside")
            for (b in a + 1 until widths.size) {
                val apart = packing.xs[a] + widths[a] <= packing.xs[b] || packing.xs[b] + widths[b] <= packing.xs[a] ||
                    packing.ys[a] + heights[a] <= packing.ys[b] || packing.ys[b] + heights[b] <= packing.ys[a]
                assertTrue(apart, "tiles $a and $b overlap")
            }
        }
    }

    @Test
    fun theAtlasIsNotMuchLargerThanItsTiles() {
        val random = java.util.Random(7)
        val widths = IntArray(339) { 8 + random.nextInt(24) }
        val heights = IntArray(339) { 8 + random.nextInt(24) }
        val packing = ShelfPacking.pack(widths, heights)
        val area = widths.indices.sumOf { widths[it] * heights[it] }
        assertTrue(packing.width.toLong() * packing.height <= area * 13L / 10, "${packing.width}x${packing.height} for $area texels of tiles")
    }

    @Test
    fun theSameTilesPackTheSameWay() {
        val widths = intArrayOf(10, 20, 20, 15, 7)
        val heights = intArrayOf(30, 10, 10, 25, 30)
        val first = ShelfPacking.pack(widths, heights)
        val second = ShelfPacking.pack(widths, heights)
        assertEquals(first.width to first.height, second.width to second.height)
        assertEquals(first.xs.toList(), second.xs.toList())
        assertEquals(first.ys.toList(), second.ys.toList())
    }

    @Test
    fun theTallestTilesLeadAndTiesKeepTheirOrder() {
        // Two 16 by 10 tiles and one 16 by 20: the tall one first, then the others in index order.
        val packing = ShelfPacking.pack(intArrayOf(16, 16, 16), intArrayOf(10, 10, 20))
        assertEquals(0 to 0, packing.xs[2] to packing.ys[2])
        assertTrue(packing.xs[0] < packing.xs[1] || packing.ys[0] < packing.ys[1], "equal tiles keep index order")
    }

    @Test
    fun noTilesIsAnEmptyAtlasAndABadTileIsRefused() {
        val empty = ShelfPacking.pack(IntArray(0), IntArray(0))
        assertEquals(0 to 0, empty.width to empty.height)
        assertFailsWith<IllegalArgumentException> { ShelfPacking.pack(intArrayOf(0), intArrayOf(5)) }
        assertFailsWith<IllegalArgumentException> { ShelfPacking.pack(intArrayOf(5), intArrayOf(5, 6)) }
    }
}
