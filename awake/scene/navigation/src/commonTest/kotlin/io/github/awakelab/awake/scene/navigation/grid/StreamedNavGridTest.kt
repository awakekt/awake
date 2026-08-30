/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.navigation.grid

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.scene.world.WorldCellCoord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Crossing a cell boundary is the only thing this adds over a single tile, so every case here is
 * about the seam: does a route cross it, does it stop at the edge of what is loaded, and does a
 * tile that does not line up get rejected instead of leaving a gap nobody sees.
 */
class StreamedNavGridTest {

    /** A [samples]-square tile from an ASCII map, `.` walkable and `#` blocked. */
    private fun tileOf(vararg rows: String): NavGridTile {
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

    private fun openTile(samples: Int) = tileOf(*Array(samples) { ".".repeat(samples) })

    @Test
    fun routesFromOneCellIntoTheNext() {
        val grid = StreamedNavGrid(samplesPerCell = 4, sampleSize = 1f)
        grid.load(WorldCellCoord(0, 0), openTile(4))
        grid.load(WorldCellCoord(1, 0), openTile(4))

        val path = grid.findPath(Vec3f(0f, 0f, 0f), Vec3f(7f, 0f, 0f))

        assertEquals(0f to 0f, path.first().let { it.x to it.z })
        assertEquals(7f to 0f, path.last().let { it.x to it.z }, "The goal is in cell [1, 0].")
    }

    /** Cells on the negative side of the origin are ordinary cells, not a special case. */
    @Test
    fun routesAcrossTheOrigin() {
        val grid = StreamedNavGrid(samplesPerCell = 4, sampleSize = 1f)
        grid.load(WorldCellCoord(-1, 0), openTile(4))
        grid.load(WorldCellCoord(0, 0), openTile(4))

        val path = grid.findPath(Vec3f(-4f, 0f, 0f), Vec3f(3f, 0f, 0f))

        assertEquals(-4f to 0f, path.first().let { it.x to it.z })
        assertEquals(3f to 0f, path.last().let { it.x to it.z })
    }

    /** An unloaded cell is not "probably walkable" — a route through it cannot be verified. */
    @Test
    fun willNotRouteIntoACellThatIsNotResident() {
        val grid = StreamedNavGrid(samplesPerCell = 4, sampleSize = 1f)
        grid.load(WorldCellCoord(0, 0), openTile(4))

        assertEquals(emptyList(), grid.findPath(Vec3f(0f, 0f, 0f), Vec3f(7f, 0f, 0f)))
    }

    /** Walls do not stop at a cell boundary, and neither may the detour around them. */
    @Test
    fun routesAroundAWallThatSpansTwoCells() {
        val grid = StreamedNavGrid(samplesPerCell = 4, sampleSize = 1f)
        grid.load(
            WorldCellCoord(0, 0),
            tileOf(
                "..#.",
                "..#.",
                "..#.",
                "....",
            ),
        )
        grid.load(WorldCellCoord(0, 1), openTile(4))

        val path = grid.findPath(Vec3f(0f, 0f, 0f), Vec3f(3f, 0f, 0f))

        assertTrue(path.isNotEmpty(), "The way around is through cell [0, 1].")
        assertTrue(
            path.any { it.z >= 3f },
            "The only gap is below the wall: ${path.map { it.x to it.z }}",
        )
    }

    @Test
    fun unloadingACellRemovesItsRoutes() {
        val grid = StreamedNavGrid(samplesPerCell = 4, sampleSize = 1f)
        grid.load(WorldCellCoord(0, 0), openTile(4))
        grid.load(WorldCellCoord(1, 0), openTile(4))
        assertTrue(grid.findPath(Vec3f(0f, 0f, 0f), Vec3f(7f, 0f, 0f)).isNotEmpty())

        grid.unload(WorldCellCoord(1, 0))

        assertEquals(emptyList(), grid.findPath(Vec3f(0f, 0f, 0f), Vec3f(7f, 0f, 0f)))
        assertEquals(setOf(WorldCellCoord(0, 0)), grid.residentCells)
    }

    /**
     * The seam the plan's risk section names: a tile that is not exactly one cell of samples would
     * shift every sample after it by a fraction, and the failure would show up as paths that
     * mysteriously stop at a boundary rather than as an error.
     */
    @Test
    fun rejectsATileThatDoesNotMatchTheCellGeometry() {
        val grid = StreamedNavGrid(samplesPerCell = 4, sampleSize = 1f)

        assertFailsWith<IllegalArgumentException>("A 5-sample tile overlaps its neighbour.") {
            grid.load(WorldCellCoord(0, 0), openTile(5))
        }
        assertFailsWith<IllegalArgumentException>("A tile baked at another resolution.") {
            grid.load(
                WorldCellCoord(0, 0),
                NavGridTile(width = 4, depth = 4, cellSize = 2f, walkable = LongArray(1) { -1L }),
            )
        }
    }

    /** World cells are metres, samples are not; a coarser grid still lands on the right cell. */
    @Test
    fun mapsWorldPositionsThroughTheSampleSize() {
        val grid = StreamedNavGrid(samplesPerCell = 4, sampleSize = 2f)
        val tile = NavGridTile(width = 4, depth = 4, cellSize = 2f, walkable = LongArray(1) { -1L })
        grid.load(WorldCellCoord(1, 0), tile)

        assertEquals(8f, grid.worldCellSize)
        val path = grid.findPath(Vec3f(8f, 0f, 0f), Vec3f(14f, 0f, 0f))
        assertEquals(8f to 14f, path.first().x to path.last().x)
    }
}
