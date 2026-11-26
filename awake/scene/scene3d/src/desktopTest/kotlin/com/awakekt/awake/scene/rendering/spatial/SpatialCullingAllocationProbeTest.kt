/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.spatial

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.BoundedVolume
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.spatial.IntIdSet
import com.awakekt.awake.core.spatial.SpatialGrid
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that stamp-based [IntIdSet] frustum and shadow-volume queries over a crowded 3D scene
 * allocate strictly 0 B/frame under `-XX:-DoEscapeAnalysis` (matching Kotlin/Wasm allocation
 * semantics) and benchmarks bytes + CPU time against the boxing `HashSet<Int>` overload.
 */
class SpatialCullingAllocationProbeTest {
    @Test
    fun spatialGridIntIdSetQueriesAllocateNothingAndBeatHashSetBoxing() {
        val grid = SpatialGrid(cellSize = 16f)
        for (i in 0 until ENTITY_COUNT) {
            val x = (i % GRID_SIDE - HALF_SIDE) * SPACING
            val z = -(i / GRID_SIDE) * SPACING
            grid.insert(i, Aabb(Vec3f(x - 1f, -1f, z - 1f), Vec3f(x + 1f, 1f, z + 1f)))
        }
        val lens = Lens.perspective(eye = Vec3f(0f, 8f, 12f), center = Vec3f(0f, 0f, -60f))
        val volume = BoxVolume(Aabb(Vec3f(-60f, -10f, -120f), Vec3f(60f, 10f, 10f)))

        val intVisible = IntIdSet(initialCapacity = ENTITY_COUNT)
        val intCasters = IntIdSet(initialCapacity = ENTITY_COUNT)
        val boxedVisible = HashSet<Int>(ENTITY_COUNT)
        val boxedCasters = HashSet<Int>(ENTITY_COUNT)

        // Warm up JIT and ensure IntIdSet/HashSet backing tables are at steady capacity.
        repeat(WARMUP) {
            intVisible.clear()
            intCasters.clear()
            boxedVisible.clear()
            boxedCasters.clear()
            grid.queryFrustum(lens, ASPECT, intVisible)
            grid.queryVolume(volume, intCasters)
            grid.queryFrustum(lens, ASPECT, boxedVisible)
            grid.queryVolume(volume, boxedCasters)
        }
        assertTrue(intVisible.size > 0 && intCasters.size > 0)
        assertEquals(boxedVisible.size, intVisible.size)
        assertEquals(boxedCasters.size, intCasters.size)

        val tIntStart = System.nanoTime()
        val intAlloc = readAllocation(FRAMES) {
            repeat(FRAMES) {
                intVisible.clear()
                intCasters.clear()
                grid.queryFrustum(lens, ASPECT, intVisible)
                grid.queryVolume(volume, intCasters)
            }
        }
        val intUs = (System.nanoTime() - tIntStart) / (FRAMES * READ_ALLOC_WINDOWS) / NANOS_PER_MICRO

        val tBoxStart = System.nanoTime()
        val boxedAlloc = readAllocation(FRAMES) {
            repeat(FRAMES) {
                boxedVisible.clear()
                boxedCasters.clear()
                grid.queryFrustum(lens, ASPECT, boxedVisible)
                grid.queryVolume(volume, boxedCasters)
            }
        }
        val boxedUs = (System.nanoTime() - tBoxStart) / (FRAMES * READ_ALLOC_WINDOWS) / NANOS_PER_MICRO

        println(
            "SPATIAL-CULL-BENCH ($ENTITY_COUNT entities, -XX:-DoEscapeAnalysis): " +
                "IntIdSet=${intAlloc.perUnit} B/f ($intUs us/f, vis=${intVisible.size}, cast=${intCasters.size}) vs " +
                "HashSet=${boxedAlloc.perUnit} B/f ($boxedUs us/f)",
        )
        assertAllocatesNothing(intAlloc, "SpatialGrid IntIdSet frustum + shadow volume queries")
        assertTrue(boxedAlloc.perUnit > 0, "HashSet<Int> baseline should show Int boxing allocations")
    }

    private class BoxVolume(private val box: Aabb) : BoundedVolume {
        override fun admits(
            minX: Float,
            minY: Float,
            minZ: Float,
            maxX: Float,
            maxY: Float,
            maxZ: Float,
        ): Boolean = maxX >= box.min.x &&
            minX <= box.max.x &&
            maxY >= box.min.y &&
            minY <= box.max.y &&
            maxZ >= box.min.z &&
            minZ <= box.max.z

        override fun bounds(within: Aabb, out: Aabb): Boolean {
            out.min.set(box.min)
            out.max.set(box.max)
            return true
        }
    }

    private companion object {
        const val ENTITY_COUNT = 500
        const val GRID_SIDE = 25
        const val HALF_SIDE = 12
        const val SPACING = 8f
        const val ASPECT = 16f / 9f
        const val WARMUP = 200
        const val FRAMES = 500
        const val READ_ALLOC_WINDOWS = 5
        const val NANOS_PER_MICRO = 1_000.0
    }
}
