/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.spatial

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PointGrid2DTest {

    @Test
    fun forEachNearVisitsExactlyThePointsInsideTheRadius() {
        val grid = PointGrid2D(cellSize = 4f)
        grid.add(1, 0f, 0f)
        grid.add(2, 3f, 0f)
        grid.add(3, 3.1f, 0f)
        grid.add(4, -2f, -2f)

        assertEquals(setOf(1, 2, 4), near(grid, 0f, 0f, 3f))
        assertEquals(setOf(2, 3), near(grid, 3.05f, 0f, 0.1f))
        assertEquals(emptySet(), near(grid, 100f, 100f, 5f))
    }

    @Test
    fun pointsAcrossNegativeCellsAndFarAwayCellsAreFound() {
        val grid = PointGrid2D(cellSize = 1f)
        grid.add(7, -0.5f, -0.5f)
        grid.add(8, 1_000_000f, -1_000_000f)

        assertEquals(setOf(7), near(grid, 0f, 0f, 1f))
        assertEquals(setOf(8), near(grid, 1_000_000f, -1_000_000f, 0.5f))
    }

    @Test
    fun matchesABruteForceScanOverRandomPoints() {
        val random = Random(42)
        val grid = PointGrid2D(cellSize = 8f, initialCapacity = 4)
        val xs = FloatArray(2_000) { random.nextFloat() * 400f - 200f }
        val zs = FloatArray(2_000) { random.nextFloat() * 400f - 200f }
        for (id in xs.indices) grid.add(id, xs[id], zs[id])
        assertEquals(xs.size, grid.size)

        repeat(50) {
            val x = random.nextFloat() * 400f - 200f
            val z = random.nextFloat() * 400f - 200f
            val radius = random.nextFloat() * 30f
            val expected = xs.indices.filter { id ->
                val dx = xs[id] - x
                val dz = zs[id] - z
                dx * dx + dz * dz <= radius * radius
            }.toSet()
            assertEquals(expected, near(grid, x, z, radius))
        }
    }

    @Test
    fun anyNearStopsAtTheFirstMatch() {
        val grid = PointGrid2D(cellSize = 2f)
        repeat(10) { grid.add(it, 0f, 0f) }
        var visited = 0
        assertTrue(grid.anyNear(0f, 0f, 1f) { visited++; true })
        assertEquals(1, visited)
        assertFalse(grid.anyNear(0f, 0f, 1f) { it == 99 })
        assertFalse(grid.anyNear(50f, 50f, 1f) { true })
    }

    @Test
    fun clearEmptiesTheGridAndItCanBeRefilled() {
        val grid = PointGrid2D(cellSize = 2f)
        grid.add(1, 0f, 0f)
        grid.clear()
        assertEquals(0, grid.size)
        assertEquals(emptySet(), near(grid, 0f, 0f, 10f))

        grid.add(2, 0.5f, 0.5f)
        assertEquals(setOf(2), near(grid, 0f, 0f, 1f))
    }

    @Test
    fun manyClearsDoNotLeakEntriesFromEarlierFills() {
        val grid = PointGrid2D(cellSize = 1f, initialCapacity = 2)
        repeat(500) { round ->
            grid.clear()
            grid.add(round, (round % 7).toFloat(), 0f)
            grid.add(round + 10_000, 50f + round % 5, 0f)
            assertEquals(setOf(round), near(grid, (round % 7).toFloat(), 0f, 0.25f))
        }
    }

    @Test
    fun theSameIdMayBeAddedMoreThanOnce() {
        val grid = PointGrid2D(cellSize = 1f)
        grid.add(5, 0f, 0f)
        grid.add(5, 0.2f, 0f)
        var count = 0
        grid.forEachNear(0f, 0f, 1f) { count++ }
        assertEquals(2, count)
    }

    @Test
    fun rejectsANonPositiveCellSizeOrRadius() {
        assertFailsWith<IllegalArgumentException> { PointGrid2D(cellSize = 0f) }
        assertFailsWith<IllegalArgumentException> { PointGrid2D(cellSize = Float.NaN) }
        val grid = PointGrid2D(cellSize = 1f)
        assertFailsWith<IllegalArgumentException> { grid.forEachNear(0f, 0f, -1f) {} }
    }

    private fun near(grid: PointGrid2D, x: Float, z: Float, radius: Float): Set<Int> {
        val out = HashSet<Int>()
        grid.forEachNear(x, z, radius) { out += it }
        return out
    }
}
