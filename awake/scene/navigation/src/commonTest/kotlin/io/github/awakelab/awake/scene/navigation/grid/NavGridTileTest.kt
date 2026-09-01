/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.navigation.grid

import io.github.awakelab.awake.asset.terrain.Heightmap
import io.github.awakelab.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavGridTileTest {
    private val unitScale = Vec3f(1f, 1f, 1f)

    private fun flat(width: Int, depth: Int) =
        Heightmap(FloatArray(width * depth), width, depth, unitScale)

    /** A map whose every row is [row], so walkability varies only along X. */
    private fun rows(row: FloatArray, depth: Int = 2): Heightmap {
        val samples = FloatArray(row.size * depth)
        for (z in 0 until depth) row.copyInto(samples, z * row.size)
        return Heightmap(samples, row.size, depth, unitScale)
    }

    @Test
    fun flatTerrainIsEntirelyWalkable() {
        val tile = flat(3, 3).bakeNavGrid(cellSize = 1f, maxSlopeDegrees = 45f)

        assertEquals(3, tile.width)
        assertEquals(3, tile.depth)
        assertEquals(9, tile.walkableCount)
    }

    @Test
    fun rejectsSamplesStraddlingACliff() {
        // A 10m wall between x=1 and x=2. Both sides of the step fail; the flats do not.
        val tile = rows(floatArrayOf(0f, 0f, 10f, 10f, 10f)).bakeNavGrid(cellSize = 1f, maxSlopeDegrees = 45f)

        assertTrue(tile.isWalkable(0, 0), "Flat ground before the cliff.")
        assertFalse(tile.isWalkable(1, 0), "The lip of the drop.")
        assertFalse(tile.isWalkable(2, 0), "The top of the wall.")
        assertTrue(tile.isWalkable(3, 0), "Flat ground after the cliff.")
        assertTrue(tile.isWalkable(4, 0), "Flat ground after the cliff.")
    }

    /**
     * The discriminating case for using forward and backward differences rather than a central
     * one. A 1.5m step over a 1m cell is a gradient of 1.5, above 45 degrees. A central difference
     * would spread it across 2m, read 0.75, and bake the lip of the drop as walkable.
     */
    @Test
    fun doesNotAverageAStepAcrossBothNeighbours() {
        val tile = rows(floatArrayOf(0f, 0f, 1.5f, 1.5f)).bakeNavGrid(cellSize = 1f, maxSlopeDegrees = 45f)

        assertFalse(tile.isWalkable(1, 0), "Below the step; a central difference would allow this.")
        assertFalse(tile.isWalkable(2, 0), "Above the step.")
        assertTrue(tile.isWalkable(0, 0))
        assertTrue(tile.isWalkable(3, 0))
    }

    @Test
    fun acceptsSlopesUnderTheThresholdAndRejectsThoseOver() {
        // 0.9m and 1.1m rises per 1m cell straddle tan(45deg) = 1.0, without testing the exact
        // boundary, where the float value of tan is not worth asserting against.
        val gentle = rows(floatArrayOf(0f, 0.9f, 1.8f)).bakeNavGrid(cellSize = 1f, maxSlopeDegrees = 45f)
        val steep = rows(floatArrayOf(0f, 1.1f, 2.2f)).bakeNavGrid(cellSize = 1f, maxSlopeDegrees = 45f)

        assertTrue(gentle.isWalkable(1, 0), "A 0.9 gradient is under 45 degrees.")
        assertFalse(steep.isWalkable(1, 0), "A 1.1 gradient is over 45 degrees.")
    }

    @Test
    fun navigationResolutionIsIndependentOfHeightmapSpacing() {
        // 5x5 samples at 1m spacing is a 4x4 metre extent; at 2m navigation cells that is 3x3.
        val tile = flat(5, 5).bakeNavGrid(cellSize = 2f, maxSlopeDegrees = 45f)

        assertEquals(3, tile.width)
        assertEquals(3, tile.depth)
        assertEquals(2f, tile.cellSize)
        // Centred: a 4m map spans -2..2, so its last navigation column is at +2, not at +4.
        assertEquals(2f, tile.worldX(2))
        assertEquals(2f, tile.worldZ(2))
    }

    @Test
    fun accountsForHeightmapScaleWhenSizingTheGrid() {
        // 3x3 samples at 4m spacing is an 8x8 metre extent; at 1m cells that is 9x9.
        val tile = Heightmap(FloatArray(9), 3, 3, Vec3f(4f, 1f, 4f))
            .bakeNavGrid(cellSize = 1f, maxSlopeDegrees = 45f)

        assertEquals(9, tile.width)
        assertEquals(9, tile.depth)
    }

    /** 81 samples spans two bitset words, so this fails if packing wraps or truncates past bit 63. */
    @Test
    fun packsSamplesBeyondTheFirstBitsetWord() {
        val tile = flat(9, 9).bakeNavGrid(cellSize = 1f, maxSlopeDegrees = 45f)

        assertEquals(81, tile.walkableCount)
        assertTrue(tile.isWalkable(8, 8), "The last sample lives in the second word.")
        assertTrue(tile.isWalkable(0, 7), "Bit 63, the last of the first word.")
        assertTrue(tile.isWalkable(1, 7), "Bit 64, the first of the second word.")
    }

    @Test
    fun rejectsOutOfRangeSamples() {
        val tile = flat(3, 3).bakeNavGrid(cellSize = 1f, maxSlopeDegrees = 45f)

        assertFailsWith<IllegalArgumentException> { tile.isWalkable(3, 0) }
        assertFailsWith<IllegalArgumentException> { tile.isWalkable(0, -1) }
    }

    @Test
    fun rejectsInvalidBakeParameters() {
        val heightmap = flat(3, 3)

        assertFailsWith<IllegalArgumentException> { heightmap.bakeNavGrid(cellSize = 0f) }
        assertFailsWith<IllegalArgumentException> { heightmap.bakeNavGrid(cellSize = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { heightmap.bakeNavGrid(maxSlopeDegrees = 0f) }
        assertFailsWith<IllegalArgumentException> { heightmap.bakeNavGrid(maxSlopeDegrees = 90f) }
    }
}
