/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.grid

import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavGridSmoothingTest {
    /** `.` walkable, `#` blocked. Row 0 is z=0, so a map reads the way the grid is indexed. */
    private fun tileOf(vararg rows: String): NavGridTile {
        val width = rows.first().length
        val depth = rows.size
        require(rows.all { it.length == width }) { "Every row must be the same width." }
        val bits = LongArray((width * depth + NavGridTile.LONG_MASK) ushr NavGridTile.LONG_SHIFT)
        for (z in 0 until depth) {
            for (x in 0 until width) {
                if (rows[z][x] != '.') continue
                val bit = z * width + x
                val word = bit ushr NavGridTile.LONG_SHIFT
                bits[word] = bits[word] or (1L shl (bit and NavGridTile.LONG_MASK))
            }
        }
        return NavGridTile(width, depth, cellSize = 1f, walkable = bits)
    }

    private fun pathOf(vararg samples: Pair<Int, Int>): List<Vec3f> =
        samples.map { (x, z) -> Vec3f(x.toFloat(), 0f, z.toFloat()) }

    private fun List<Vec3f>.asGrid(): List<Pair<Int, Int>> = map { it.x.toInt() to it.z.toInt() }

    @Test
    fun collapsesAStraightRunToItsEndpoints() {
        val tile = tileOf(".....")

        val smoothed = tile.smoothPath(tile.findPath(0, 0, 4, 0))

        assertEquals(listOf(0 to 0, 4 to 0), smoothed.asGrid())
    }

    @Test
    fun collapsesADiagonalRunToItsEndpoints() {
        val tile = tileOf("....", "....", "....", "....")

        val smoothed = tile.smoothPath(tile.findPath(0, 0, 3, 3))

        assertEquals(listOf(0 to 0, 3 to 3), smoothed.asGrid())
    }

    @Test
    fun keepsTheCornerWhenRoundingAWall() {
        val tile = tileOf(
            "...",
            ".#.",
            "...",
        )

        val smoothed = tile.smoothPath(tile.findPath(0, 0, 2, 2))

        assertTrue(smoothed.size >= 3, "A wall between the endpoints needs a corner: ${smoothed.asGrid()}")
        assertTrue(
            smoothed.asGrid().none { it == 1 to 1 },
            "The route must not pass through the wall: ${smoothed.asGrid()}",
        )
    }

    /**
     * The rule that makes smoothing honest. [NavGridTile.findPath] refuses a diagonal unless both
     * shared orthogonal samples are open; if line of sight did not apply the same rule, smoothing
     * would straighten the path back through the corner the search had just refused, and the
     * refusal would be decorative.
     */
    @Test
    fun refusesToStraightenThroughACornerTheSearchRefused() {
        val tile = tileOf(
            ".#.",
            "#..",
            "...",
        )

        // Handed directly rather than produced by findPath: (0,0) is walled off, so no search
        // reaches it, and the corner rule still has to hold for any caller-supplied route.
        val smoothed = tile.smoothPath(pathOf(0 to 0, 1 to 1, 2 to 2))

        assertEquals(
            listOf(0 to 0, 1 to 1, 2 to 2),
            smoothed.asGrid(),
            "The 45-degree run passes through the corner shared with two blocked samples.",
        )
    }

    @Test
    fun neverStraightensThroughABlockedSample() {
        val tile = tileOf(
            "....",
            "##.#",
            "....",
        )

        val smoothed = tile.smoothPath(tile.findPath(0, 0, 0, 2))

        for (waypoint in smoothed.asGrid()) {
            assertTrue(tile.isWalkable(waypoint.first, waypoint.second), "Waypoint $waypoint is blocked.")
        }
        assertTrue(
            smoothed.asGrid().any { it.first == 2 },
            "The only gap is at x=2, so the route has to go through it: ${smoothed.asGrid()}",
        )
    }

    @Test
    fun leavesShortPathsAlone() {
        val tile = tileOf("...")

        assertEquals(emptyList(), tile.smoothPath(emptyList()))
        assertEquals(pathOf(1 to 0).asGrid(), tile.smoothPath(pathOf(1 to 0)).asGrid())
        assertEquals(pathOf(0 to 0, 2 to 0).asGrid(), tile.smoothPath(pathOf(0 to 0, 2 to 0)).asGrid())
    }

    @Test
    fun carriesWaypointHeightsThrough() {
        val tile = tileOf(".....")
        val withHeights = listOf(
            Vec3f(0f, 7f, 0f),
            Vec3f(1f, 8f, 0f),
            Vec3f(4f, 9f, 0f),
        )

        val smoothed = tile.smoothPath(withHeights)

        assertEquals(listOf(7f, 9f), smoothed.map { it.y }, "Y is carried through untouched.")
    }

    @Test
    fun mapsSamplesThroughTheTileCellSize() {
        val tile = NavGridTile(width = 5, depth = 1, cellSize = 2f, walkable = LongArray(1) { -1L })
        val path = tile.findPath(startX = 0, startZ = 0, goalX = 4, goalZ = 0)

        val smoothed = tile.smoothPath(path)

        assertEquals(listOf(0f, 8f), smoothed.map { it.x }, "Endpoints keep their world spacing.")
    }
}
