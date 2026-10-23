/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpatialHashGrid2DTest {

    @Test
    fun findsAllPointsWithinRadiusAndReusesStorageAcrossClears() {
        val grid = SpatialHashGrid2D(cellMetres = 16f, initialCapacity = 8)
        val rng = Random(42)
        val count = 200
        val xs = FloatArray(count) { (rng.nextFloat() - 0.5f) * 400f }
        val zs = FloatArray(count) { (rng.nextFloat() - 0.5f) * 400f }

        repeat(2) {
            grid.clear()
            assertEquals(0, grid.size)
            for (i in 0 until count) {
                grid.add(i, xs[i], zs[i])
            }
            assertEquals(count, grid.size)

            val queryX = 12f
            val queryZ = -18f
            val radius = 35f
            val radiusSq = radius * radius

            val expected = (0 until count).filter { i ->
                val dx = xs[i] - queryX
                val dz = zs[i] - queryZ
                dx * dx + dz * dz <= radiusSq
            }.toSet()

            val visited = mutableSetOf<Int>()
            grid.forEachNear(queryX, queryZ, radius) { id ->
                assertTrue(visited.add(id), "Each point in candidate cells must be visited at most once")
            }
            assertTrue(visited.containsAll(expected), "All points within radius must be visited")

            val firstExpected = expected.first()
            assertTrue(grid.anyNear(queryX, queryZ, radius) { it == firstExpected })
            assertFalse(grid.anyNear(queryX, queryZ, radius) { it == -1 })
        }
    }
}
