/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.spatial

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.BoundedVolume
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntIdSetTest {
    @Test
    fun addContainsClearAndGrowWithoutBoxing() {
        val set = IntIdSet(initialCapacity = 4)
        assertTrue(set.isEmpty)
        assertFalse(0 in set)
        assertFalse(-1 in set)

        assertTrue(set.add(0))
        assertFalse(set.add(0))
        assertTrue(set.add(100))
        assertEquals(2, set.size)
        assertTrue(0 in set)
        assertTrue(100 in set)
        assertFalse(50 in set)

        set.clear()
        assertTrue(set.isEmpty)
        assertFalse(0 in set)
        assertFalse(100 in set)

        assertTrue(set.add(100))
        assertEquals(1, set.size)
        assertTrue(100 in set)
        assertFailsWith<IllegalArgumentException> { set.add(-1) }
    }

    @Test
    fun spatialGridFrustumAndVolumeQueriesMatchCollectionOverloads() {
        val grid = SpatialGrid(cellSize = 16f)
        for (i in 0 until 80) {
            val x = (i % 10 - 5) * 12f
            val z = -(i / 10) * 12f
            grid.insert(i, Aabb(Vec3f(x - 1f, -1f, z - 1f), Vec3f(x + 1f, 1f, z + 1f)))
        }
        val lens = Lens.perspective(eye = Vec3f(0f, 5f, 10f), center = Vec3f(0f, 0f, -30f))
        val expectedFrustum = HashSet<Int>()
        val actualFrustum = IntIdSet()
        grid.queryFrustum(lens, 16f / 9f, expectedFrustum)
        grid.queryFrustum(lens, 16f / 9f, actualFrustum)
        assertEquals(expectedFrustum.size, actualFrustum.size)
        for (i in 0 until 80) {
            assertEquals(i in expectedFrustum, i in actualFrustum, "frustum match for id=$i")
        }

        val volume = object : BoundedVolume {
            val box = Aabb(Vec3f(-25f, -5f, -50f), Vec3f(25f, 5f, 5f))
            override fun admits(
                minX: Float,
                minY: Float,
                minZ: Float,
                maxX: Float,
                maxY: Float,
                maxZ: Float,
            ): Boolean = maxX >= box.min.x && minX <= box.max.x &&
                maxY >= box.min.y && minY <= box.max.y &&
                maxZ >= box.min.z && minZ <= box.max.z

            override fun bounds(within: Aabb, out: Aabb): Boolean {
                out.min.set(box.min)
                out.max.set(box.max)
                return true
            }
        }
        val expectedVolume = HashSet<Int>()
        val actualVolume = IntIdSet()
        grid.queryVolume(volume, expectedVolume)
        grid.queryVolume(volume, actualVolume)
        assertEquals(expectedVolume.size, actualVolume.size)
        for (i in 0 until 80) {
            assertEquals(i in expectedVolume, i in actualVolume, "volume match for id=$i")
        }
    }
}
