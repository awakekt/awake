/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering.spatial

import io.github.awakelab.awake.core.math.Aabb
import io.github.awakelab.awake.core.math.Frustum
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.core.math.intersects
import io.github.awakelab.awake.core.math.squaredDistanceTo
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The grid's only real contract: it returns exactly what testing every entity would.
 *
 * An index that misses something is a hole in the world -- geometry that stops drawing, an
 * enemy that stops noticing you -- and the failure appears far from here. So the tests that
 * matter are differential: build a random scene, ask the grid, ask brute force, compare sets.
 * Anything the column walk gets wrong (a boundary rounding, a missed span, a stale entry after
 * a move) shows up as a difference rather than as a plausible-looking smaller answer.
 */
class SpatialGridTest {

    @Test
    fun anAabbQueryMatchesTestingEveryEntity() {
        val random = Random(seed = 20_260_830)
        val grid = SpatialGrid(cellSize = 16f)
        val boxes = scatter(random, count = 400, extent = 600f)
        boxes.forEach { (id, box) -> grid.insert(id, box) }

        repeat(QUERY_COUNT) {
            val query = randomBox(random, extent = 600f, maxSize = 120f)
            assertEquals(
                boxes.filterValues { it.intersects(query) }.keys,
                grid.queryAabb(query, mutableSetOf()).toSet(),
                "Query $query disagreed with brute force.",
            )
        }
    }

    @Test
    fun aRangeQueryMatchesTestingEveryEntity() {
        val random = Random(seed = 7)
        val grid = SpatialGrid(cellSize = 32f)
        val boxes = scatter(random, count = 300, extent = 400f)
        boxes.forEach { (id, box) -> grid.insert(id, box) }

        repeat(QUERY_COUNT) {
            val center = randomPoint(random, extent = 400f)
            val radius = random.nextFloat() * 150f
            assertEquals(
                boxes.filterValues { it.squaredDistanceTo(center) <= radius * radius }.keys,
                grid.queryRange(center, radius, mutableSetOf()).toSet(),
                "Range query at $center r=$radius disagreed with brute force.",
            )
        }
    }

    @Test
    fun aFrustumQueryMatchesTestingEveryEntity() {
        val random = Random(seed = 99)
        val grid = SpatialGrid(cellSize = 24f)
        val boxes = scatter(random, count = 500, extent = 300f)
        boxes.forEach { (id, box) -> grid.insert(id, box) }
        val lens = Lens(
            eye = Vec3f(0f, 20f, 200f),
            center = Vec3f(0f, 0f, 0f),
            fovYRadians = 1f,
            near = 0.5f,
            far = 500f,
        )

        assertEquals(
            boxes.filterValues { Frustum.intersects(lens, ASPECT, it) }.keys,
            grid.queryFrustum(lens, ASPECT, mutableSetOf()).toSet(),
            "The two-level column test must cull exactly what the per-entity test culls.",
        )
    }

    @Test
    fun anEntitySpanningSeveralColumnsIsReturnedOnce() {
        val grid = SpatialGrid(cellSize = 4f)
        // 20 units across a 4-unit grid: five columns on each axis, so 25 lists hold this id.
        grid.insert(1, box(-10f, 10f))

        val hits = grid.queryAabb(box(-10f, 10f), mutableListOf())

        assertEquals(listOf(1), hits.toList(), "Once per entity, not once per column it occupies.")
    }

    @Test
    fun movingAnEntityLeavesTheColumnsItUsedToOccupy() {
        val grid = SpatialGrid(cellSize = 8f)
        grid.insert(1, box(0f, 4f))

        grid.insert(1, box(400f, 404f))

        assertTrue(grid.queryAabb(box(-10f, 10f), mutableListOf()).isEmpty(), "Stale entry at the old place.")
        assertEquals(listOf(1), grid.queryAabb(box(395f, 410f), mutableListOf()).toList())
        assertEquals(1, grid.size, "A move is one entry, not two.")
    }

    @Test
    fun aRemovedEntityIsGoneFromEveryQuery() {
        val grid = SpatialGrid(cellSize = 8f)
        grid.insert(1, box(0f, 4f))
        grid.insert(2, box(0f, 4f))

        grid.remove(1)

        assertFalse(1 in grid.ids)
        assertEquals(listOf(2), grid.queryAabb(box(-10f, 10f), mutableListOf()).toList())
    }

    @Test
    fun aTallColumnDoesNotSwallowWhatIsAboveOrBelowIt() {
        // The Y range a column reports only grows, so this checks the per-entity test still
        // decides: a query above the tall box must not pick up the short one under it.
        val grid = SpatialGrid(cellSize = 32f)
        grid.insert(1, Aabb(Vec3f(0f, 0f, 0f), Vec3f(1f, 200f, 1f)))
        grid.insert(2, Aabb(Vec3f(0f, 0f, 0f), Vec3f(1f, 1f, 1f)))

        val high = grid.queryAabb(Aabb(Vec3f(0f, 150f, 0f), Vec3f(1f, 160f, 1f)), mutableListOf())

        assertEquals(listOf(1), high.toList())
    }

    private fun scatter(random: Random, count: Int, extent: Float): Map<Int, Aabb> =
        (1..count).associateWith { randomBox(random, extent, maxSize = 30f) }

    private fun randomBox(random: Random, extent: Float, maxSize: Float): Aabb {
        val center = randomPoint(random, extent)
        val half = Vec3f(
            random.nextFloat() * maxSize,
            random.nextFloat() * maxSize,
            random.nextFloat() * maxSize,
        )
        return Aabb(
            Vec3f(center.x - half.x, center.y - half.y, center.z - half.z),
            Vec3f(center.x + half.x, center.y + half.y, center.z + half.z),
        )
    }

    private fun randomPoint(random: Random, extent: Float) = Vec3f(
        (random.nextFloat() - HALF) * extent,
        (random.nextFloat() - HALF) * extent * VERTICAL_FRACTION,
        (random.nextFloat() - HALF) * extent,
    )

    private fun box(from: Float, to: Float) = Aabb(Vec3f(from, from, from), Vec3f(to, to, to))

    private companion object {
        const val QUERY_COUNT = 50
        const val ASPECT = 16f / 9f
        const val HALF = 0.5f

        /** Wide and thin, the distribution the grid's columns are chosen for. */
        const val VERTICAL_FRACTION = 0.1f
    }
}
