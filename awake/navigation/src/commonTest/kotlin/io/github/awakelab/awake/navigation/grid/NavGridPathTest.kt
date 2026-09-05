/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.navigation.grid

import io.github.awakelab.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavGridPathTest {
    /**
     * Builds a tile from an ASCII map, `.` walkable and `#` blocked. Row 0 is z=0, so the maps
     * below read the same way the grid is indexed rather than upside down.
     */
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

    private fun List<Vec3f>.asGrid(): List<Pair<Int, Int>> = map { it.x.toInt() to it.z.toInt() }

    @Test
    fun walksAnOpenGridFromStartToGoal() {
        val tile = tileOf(
            "....",
            "....",
            "....",
        )

        val path = tile.findPath(startX = 0, startZ = 0, goalX = 3, goalZ = 2)

        assertEquals(0 to 0, path.first().let { it.x.toInt() to it.z.toInt() })
        assertEquals(3 to 2, path.last().let { it.x.toInt() to it.z.toInt() })
        // dx=3, dz=2 on an open grid: two diagonals cover the Z span, one straight finishes X.
        assertEquals(3, path.size - 1, "Expected the octile-optimal move count.")
    }

    @Test
    fun routesAroundAWall() {
        val tile = tileOf(
            ".#..",
            ".#..",
            "....",
        )

        val path = tile.findPath(startX = 0, startZ = 0, goalX = 3, goalZ = 0)

        assertTrue(path.isNotEmpty(), "A gap exists along the bottom row.")
        assertTrue(
            path.asGrid().none { (x, z) -> x == 1 && z < 2 },
            "The path must not cross the wall: ${path.asGrid()}",
        )
    }

    @Test
    fun returnsEmptyWhenTheGoalIsWalledOff() {
        val tile = tileOf(
            "..#..",
            "..#..",
            "..#..",
        )

        assertEquals(emptyList(), tile.findPath(startX = 0, startZ = 1, goalX = 4, goalZ = 1))
    }

    @Test
    fun returnsEmptyWhenAnEndpointIsBlockedOrOffTile() {
        val tile = tileOf(
            "..#",
            "...",
        )

        assertEquals(emptyList(), tile.findPath(0, 0, 2, 0), "Goal is blocked.")
        assertEquals(emptyList(), tile.findPath(2, 0, 0, 0), "Start is blocked.")
        assertEquals(emptyList(), tile.findPath(0, 0, 3, 0), "Goal is off the tile.")
        assertEquals(emptyList(), tile.findPath(-1, 0, 0, 0), "Start is off the tile.")
    }

    @Test
    fun returnsASingleWaypointWhenAlreadyAtTheGoal() {
        val path = tileOf("...", "...").findPath(1, 1, 1, 1)

        assertEquals(listOf(1 to 1), path.asGrid())
    }

    /**
     * The classic 8-connected bug: stepping diagonally between two blocked orthogonal neighbours
     * clips the corner of a wall the agent cannot actually pass through. Here (1,0) and (0,1) are
     * blocked, so (0,0) and (1,1) are only connected the long way — which does not exist.
     */
    @Test
    fun refusesToCutADiagonalCorner() {
        val tile = tileOf(
            ".#",
            "#.",
        )

        assertEquals(emptyList(), tile.findPath(0, 0, 1, 1))
    }

    /**
     * One open side is not enough. Shaving past a single blocked corner is geometrically the same
     * clip as cutting between two, for any agent with width, so the diagonal is refused and the
     * route goes around. This is the case that distinguishes the strict rule from the loose one —
     * [refusesToCutADiagonalCorner] above passes under either.
     */
    @Test
    fun refusesADiagonalPastASingleBlockedCorner() {
        val tile = tileOf(
            ".#",
            "..",
        )

        assertEquals(listOf(0 to 0, 0 to 1, 1 to 1), tile.findPath(0, 0, 1, 1).asGrid())
    }

    /** The corner rule must not cost a diagonal that is open on both sides. */
    @Test
    fun takesADiagonalWithBothSidesOpen() {
        val tile = tileOf(
            "..",
            "..",
        )

        assertEquals(listOf(0 to 0, 1 to 1), tile.findPath(0, 0, 1, 1).asGrid())
    }

    /**
     * Ties are common on a uniform grid. Breaking them on the node index rather than on whatever
     * order the heap happened to leave equal entries in is what makes a repeated query reproduce
     * its path, which an authoritative server needs.
     */
    @Test
    fun producesTheSamePathForTheSameQuery() {
        val tile = tileOf(
            ".....",
            ".....",
            ".....",
            ".....",
        )

        val first = tile.findPath(0, 0, 4, 3).asGrid()
        val second = tile.findPath(0, 0, 4, 3).asGrid()

        assertEquals(first, second)
    }

    @Test
    fun roundsWorldPositionsToTheNearestSample() {
        val tile = tileOf("...", "...", "...")

        val path = tile.findPath(Vec3f(0.4f, 0f, 0.4f), Vec3f(1.6f, 0f, 2.1f))

        assertEquals(0 to 0, path.first().let { it.x.toInt() to it.z.toInt() }, "0.4 rounds to 0.")
        assertEquals(2 to 2, path.last().let { it.x.toInt() to it.z.toInt() }, "1.6 rounds to 2.")
    }

    @Test
    fun mapsWaypointsThroughTheTileCellSize() {
        val bits = LongArray(1) { -1L }
        val tile = NavGridTile(width = 3, depth = 1, cellSize = 4f, walkable = bits)

        val path = tile.findPath(startX = 0, startZ = 0, goalX = 2, goalZ = 0)

        assertEquals(listOf(0f, 4f, 8f), path.map { it.x })
        assertTrue(path.all { it.y == 0f }, "Y is the caller's to resolve from the heightmap.")
    }
}
