/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.grid

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.world.WorldCellCoord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavDebugSourceTest {

    /** `.` is walkable and `#` is blocked; row `z` of the strings is sample row `z`. */
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

    private val world = World()

    /** One agent walking from (0, 0) to (1, 1) to (2, 1), and one with no route. */
    private val routes = listOf(
        AgentRoute(world.create(), listOf(Vec3f(0f, 0f, 0f), Vec3f(1f, 0f, 1f), Vec3f(2f, 0f, 1f))),
        AgentRoute(world.create(), emptyList()),
    )

    @Test
    fun itsIdIsNav() {
        assertEquals("nav", NavDebugSource(tile(".")).id)
        assertEquals("nav", NavDebugSource.ID)
    }

    @Test
    fun theRowsCountWalkableAndBlockedSamplesAndRoutedAgents() {
        val rows = RecordedRows()

        NavDebugSource(tile("..#", "#..")) { routes }.rows(rows)

        assertEquals(listOf("Walkable" to 4, "Blocked" to 2, "Routes" to 1), rows.rows)
    }

    @Test
    fun aTilesLinesAreACrossOnEachBlockedSampleThenEachRoute() {
        val lines = RecordedLines()
        val raised = NavGroundHeight { _, _ -> 2f }

        NavDebugSource(tile("..", "#."), raised) { routes }.lines(lines)

        // Two lines for the blocked sample's cross, two for the route's legs and two for the cross on its goal.
        assertEquals(6, lines.drawn.size, "${lines.drawn}")
        // The blocked sample is (0, 1); its cross lies 0.05 m over the ground.
        lines.assertLine(0, Vec3f(-0.35f, 2.05f, 0.65f), Vec3f(0.35f, 2.05f, 1.35f), BLOCKED_COLOR)
        lines.assertLine(1, Vec3f(-0.35f, 2.05f, 1.35f), Vec3f(0.35f, 2.05f, 0.65f), BLOCKED_COLOR)
        // The route rides 0.35 m over the ground, in its agent's colour.
        val color = routeColorFor(routes[0].entity)
        lines.assertLine(2, Vec3f(0f, 2.35f, 0f), Vec3f(1f, 2.35f, 1f), color)
        lines.assertLine(3, Vec3f(1f, 2.35f, 1f), Vec3f(2f, 2.35f, 1f), color)
        lines.assertLine(4, Vec3f(1.4f, 2.35f, 0.4f), Vec3f(2.6f, 2.35f, 1.6f), color)
    }

    @Test
    fun theLinesAreTheOnesNavGridDebugLinesReturns() {
        val tile = tile("#.#", ".#.")
        val lines = RecordedLines()

        NavDebugSource(tile) { routes }.lines(lines)

        val listed = navGridDebugLines(tile, { _, _ -> 0f }, routes)
        assertEquals(listed.map { Drawn(it.start, it.end, it.color) }, lines.drawn)
    }

    @Test
    fun aStreamedGridDrawsEachResidentCellsMarkersAndOutline() {
        val grid = StreamedNavGrid(samplesPerCell = 2, sampleSize = 1f).apply {
            load(WorldCellCoord(0, 0), tile("..", "#."))
            load(WorldCellCoord(1, 0), tile("#.", ".#"))
        }
        val source = NavDebugSource(grid) { routes }
        val rows = RecordedRows()
        val lines = RecordedLines()

        source.rows(rows)
        source.lines(lines)

        assertEquals(listOf("Cells" to 2, "Walkable" to 5, "Blocked" to 3, "Routes" to 1), rows.rows)
        val outline = 4 * OUTLINE_LINES
        // Cell (0, 0): one cross and its outline; cell (1, 0): two crosses, in the alternate colour, and its outline.
        assertEquals(2 + outline + 4 + outline + 2 + 2, lines.drawn.size)
        assertEquals(BLOCKED_COLOR, lines.drawn[0].color)
        assertEquals(BLOCKED_COLOR_ALTERNATE, lines.drawn[2 + outline].color)
        val flat = { _: Float, _: Float -> 0f }
        assertEquals(
            navGridDebugLines(grid, flat, routes).map { Drawn(it.start, it.end, it.color) },
            lines.drawn,
        )
    }

    @Test
    fun aStreamedGridIsReadAgainWhenACellLoadsOrUnloads() {
        val grid = StreamedNavGrid(samplesPerCell = 2, sampleSize = 1f)
        val source = NavDebugSource(grid)
        assertEquals("Cells" to 0, RecordedRows().also(source::rows).rows.first())

        grid.load(WorldCellCoord(3, -2), tile("##", "##"))
        assertEquals(listOf("Cells" to 1, "Walkable" to 0, "Blocked" to 4), RecordedRows().also(source::rows).rows.take(3))

        grid.unload(WorldCellCoord(3, -2))
        assertEquals(listOf("Cells" to 0, "Walkable" to 0, "Blocked" to 0), RecordedRows().also(source::rows).rows.take(3))
    }

    @Test
    fun aSteadySecondFrameWritesTheSameLines() {
        val grid = StreamedNavGrid(samplesPerCell = 2, sampleSize = 1f).apply {
            load(WorldCellCoord(0, 0), tile("#.", ".#"))
        }
        val source = NavDebugSource(grid) { routes }
        val first = RecordedLines().also(source::lines)

        val second = RecordedLines().also(source::lines)

        assertTrue(first.drawn.isNotEmpty())
        assertEquals(first.drawn.size, second.drawn.size)
        assertEquals(first.drawn, second.drawn)
    }

    @Test
    fun theSinkStillBuildsItsOwnList() {
        val sink = NavGridDebugSink { _, _ -> 1f }

        sink.cross(0f, 0f, arm = 1f, lift = 0f, color = BLOCKED_COLOR)
        sink.cellOutline(WorldCellCoord(0, 0), cellSize = 4f)

        assertEquals(2 + 4 * OUTLINE_LINES, sink.lines.size)
        assertEquals(Vec3f(-1f, 1f, -1f), sink.lines[0].start)
        // Each segment keeps its own points, though the writer reuses two.
        assertTrue(sink.lines.map { it.start }.toSet().size > 2)
    }

    private data class Drawn(val a: Vec3f, val b: Vec3f, val color: Color)

    /** Copies each line's points, as a host does: the source reuses its own. */
    private class RecordedLines : DebugLines {
        val drawn = ArrayList<Drawn>()

        override fun line(a: Vec3f, b: Vec3f, color: Color) {
            drawn += Drawn(a.copy(), b.copy(), color)
        }

        fun assertLine(index: Int, a: Vec3f, b: Vec3f, color: Color) {
            val line = drawn[index]
            assertTrue(line.a.near(a) && line.b.near(b), "line $index is ${line.a} to ${line.b}, not $a to $b")
            assertEquals(color, line.color, "line $index")
        }
    }

    private class RecordedRows : DebugRows {
        val rows = ArrayList<Pair<String, Int>>()

        override fun row(label: String, value: Int, warn: Boolean) {
            rows += label to value
        }

        override fun row(label: String, value: Float, decimals: Int, warn: Boolean) = error("no float rows")

        override fun row(label: String, value: String, warn: Boolean) = error("no text rows")
    }

    private companion object {
        /** Lines an outline edge takes, so it follows the ground. */
        const val OUTLINE_LINES = 8
        const val EPSILON = 1e-4f

        fun Vec3f.near(other: Vec3f): Boolean =
            kotlin.math.abs(x - other.x) < EPSILON && kotlin.math.abs(y - other.y) < EPSILON &&
                kotlin.math.abs(z - other.z) < EPSILON
    }
}
