/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.grid

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.scene.world.WorldCellCoord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The point of this layer is that an agent starts walking toward somewhere it cannot yet verify,
 * without ever being handed a path through terrain nobody has checked. Both halves are asserted:
 * a leg comes back when the goal is far away, and it stops inside the loaded world.
 */
class HierarchicalNavGridTest {

    private val samples = 4
    private val cellSize = samples.toFloat()

    private fun tile(vararg rows: String): NavGridTile {
        val width = rows.first().length
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

    /** Resident cells go in both layers; the rest are described to the coarse graph only. */
    private fun world(resident: List<WorldCellCoord>, known: List<WorldCellCoord>): HierarchicalNavGrid {
        val grid = StreamedNavGrid(samplesPerCell = samples, sampleSize = 1f)
        val coarse = CoarseNavGraph(cellSize)
        known.forEach { coarse.put(it, open().summarize()) }
        resident.forEach { grid.load(it, open()) }
        return HierarchicalNavGrid(grid, coarse)
    }

    @Test
    fun aGoalInsideTheLoadedWorldIsPathedNormally() {
        val nav = world(
            resident = listOf(WorldCellCoord(0, 0), WorldCellCoord(1, 0)),
            known = listOf(WorldCellCoord(0, 0), WorldCellCoord(1, 0)),
        )

        val path = nav.findPath(Vec3f(0f, 0f, 0f), Vec3f(6f, 0f, 0f))

        assertEquals(0f to 0f, path.first().let { it.x to it.z })
        assertEquals(6f to 0f, path.last().let { it.x to it.z }, "The fine search answers this one.")
    }

    /**
     * The case this class exists for: three cells away, one of them loaded. The agent gets a leg
     * toward the goal instead of the empty list the fine grid alone returns.
     */
    @Test
    fun aGoalBeyondTheLoadedWorldReturnsALegTowardIt() {
        val known = (0..3).map { WorldCellCoord(it, 0) }
        val nav = world(resident = listOf(WorldCellCoord(0, 0)), known = known)

        val path = nav.findPath(Vec3f(0f, 0f, 0f), Vec3f(13f, 0f, 0f))

        assertTrue(path.isNotEmpty(), "A route exists across cells that are merely not loaded.")
        assertTrue(path.last().x > path.first().x, "The leg should head toward the goal: $path")
        assertTrue(
            path.all { it.x < cellSize },
            "The leg must stay inside the one loaded cell: ${path.map { it.x }}",
        )
    }

    /** No corridor means no route, and no route means nothing to walk — not a guess. */
    @Test
    fun aGoalNothingConnectsToReturnsNoPath() {
        val nav = world(
            resident = listOf(WorldCellCoord(0, 0)),
            known = listOf(WorldCellCoord(0, 0), WorldCellCoord(9, 9)),
        )

        assertEquals(emptyList(), nav.findPath(Vec3f(0f, 0f, 0f), Vec3f(38f, 0f, 38f)))
    }

    /** Nothing loaded at all: the corridor exists, but there is no ground to take a step on. */
    @Test
    fun aStartCellThatIsNotLoadedReturnsNoPath() {
        val nav = world(resident = emptyList(), known = (0..2).map { WorldCellCoord(it, 0) })

        assertEquals(emptyList(), nav.findPath(Vec3f(0f, 0f, 0f), Vec3f(9f, 0f, 0f)))
    }

    /** A goal in the same cell that the fine search already refused is not made up by this layer. */
    @Test
    fun anUnreachableGoalInTheSameCellStaysUnreachable() {
        val grid = StreamedNavGrid(samplesPerCell = samples, sampleSize = 1f)
        val walled = tile("..#.", "..#.", "..#.", "..#.")
        grid.load(WorldCellCoord(0, 0), walled)
        val coarse = CoarseNavGraph(cellSize)
        coarse.put(WorldCellCoord(0, 0), walled.summarize())

        val path = HierarchicalNavGrid(grid, coarse).findPath(Vec3f(0f, 0f, 0f), Vec3f(3f, 0f, 0f))

        assertEquals(emptyList(), path)
    }
}
