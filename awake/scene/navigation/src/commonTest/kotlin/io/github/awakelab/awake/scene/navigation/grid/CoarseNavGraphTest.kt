/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.navigation.grid

import io.github.awakelab.awake.scene.world.WorldCellCoord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A coarse graph fails by claiming a route that does not exist — the corridor looks fine, every
 * local path along it fails, and the agent stands still for reasons nothing logs. So the cases
 * here are mostly about refusing: gaps that do not line up, cells split by a wall, and cells
 * nobody has described.
 */
class CoarseNavGraphTest {

    private val cellSize = 4f

    private fun tile(vararg rows: String): NavGridTile {
        val width = rows.first().length
        require(rows.all { it.length == width }) { "Every row must be the same width." }
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

    private fun open() = tile("....", "....", "....", "....")

    private fun graphOf(vararg cells: Pair<WorldCellCoord, NavGridTile>) = CoarseNavGraph(cellSize).apply {
        cells.forEach { (coord, tile) -> put(coord, tile.summarize()) }
    }

    @Test
    fun routesAcrossCellsThatAreNotLoaded() {
        val graph = graphOf(
            WorldCellCoord(0, 0) to open(),
            WorldCellCoord(1, 0) to open(),
            WorldCellCoord(2, 0) to open(),
        )

        val corridor = assertNotNull(graph.corridor(WorldCellCoord(0, 0), WorldCellCoord(2, 0)))

        assertEquals(
            listOf(WorldCellCoord(0, 0), WorldCellCoord(1, 0), WorldCellCoord(2, 0)),
            corridor,
        )
    }

    @Test
    fun routesAroundACellThatIsFullyBlocked() {
        val blocked = tile("####", "####", "####", "####")
        val graph = graphOf(
            WorldCellCoord(0, 0) to open(),
            WorldCellCoord(1, 0) to blocked,
            WorldCellCoord(2, 0) to open(),
            WorldCellCoord(0, 1) to open(),
            WorldCellCoord(1, 1) to open(),
            WorldCellCoord(2, 1) to open(),
        )

        val corridor = assertNotNull(graph.corridor(WorldCellCoord(0, 0), WorldCellCoord(2, 0)))

        assertTrue(WorldCellCoord(1, 0) !in corridor, "Routed through a solid cell: $corridor")
        assertTrue(WorldCellCoord(1, 1) in corridor, "The only way round is the row below: $corridor")
    }

    /**
     * Two cells can each have an opening on the shared edge without those openings lining up. A
     * graph that only asked "is this side open at all" would connect them and be wrong.
     */
    @Test
    fun refusesAnEdgeWhoseGapsDoNotLineUp() {
        val leftGapNorth = tile("...#", "...#", "####", "####")
        val rightGapSouth = tile("####", "####", "#...", "#...")
        val graph = graphOf(
            WorldCellCoord(0, 0) to leftGapNorth,
            WorldCellCoord(1, 0) to rightGapSouth,
        )

        assertNull(graph.corridor(WorldCellCoord(0, 0), WorldCellCoord(1, 0)))
    }

    /**
     * The failure regions exist to prevent: a cell split down the middle connects on both sides,
     * so a cell-level graph would route in one side and out the other, through the wall.
     */
    @Test
    fun refusesToCrossACellItsOwnWallSplits() {
        val split = tile(".#..", ".#..", ".#..", ".#..")
        val graph = graphOf(
            WorldCellCoord(0, 0) to open(),
            WorldCellCoord(1, 0) to split,
            WorldCellCoord(2, 0) to open(),
        )

        assertNull(
            graph.corridor(WorldCellCoord(0, 0), WorldCellCoord(2, 0)),
            "The middle cell's two halves do not connect to each other.",
        )
    }

    @Test
    fun aCellNobodyHasDescribedIsNotRoutable() {
        val graph = graphOf(WorldCellCoord(0, 0) to open())

        assertNull(graph.corridor(WorldCellCoord(0, 0), WorldCellCoord(5, 5)))
        assertNull(graph.corridor(WorldCellCoord(5, 5), WorldCellCoord(0, 0)))
    }

    @Test
    fun aRouteWithinOneCellIsThatCell() {
        val graph = graphOf(WorldCellCoord(3, 3) to open())

        assertEquals(listOf(WorldCellCoord(3, 3)), graph.corridor(WorldCellCoord(3, 3), WorldCellCoord(3, 3)))
    }

    /** Summaries outlive residency; that is the entire reason they are kept separately. */
    @Test
    fun summariesAreKeptUntilExplicitlyRemoved() {
        val graph = graphOf(WorldCellCoord(0, 0) to open(), WorldCellCoord(1, 0) to open())

        graph.remove(WorldCellCoord(1, 0))

        assertEquals(setOf(WorldCellCoord(0, 0)), graph.knownCells)
        assertNull(graph.corridor(WorldCellCoord(0, 0), WorldCellCoord(1, 0)))
    }
}
