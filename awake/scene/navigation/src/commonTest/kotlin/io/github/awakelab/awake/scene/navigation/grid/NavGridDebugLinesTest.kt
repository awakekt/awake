/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.navigation.grid

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.ai.ChaseBehavior
import io.github.awakelab.awake.scene.world.WorldCellCoord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A debug overlay fails by drawing something plausible in the wrong place, which no crash reports
 * and no screenshot review reliably catches. These pin the parts that carry meaning: only blocked
 * samples get a marker, markers sit at their own cell's offset, and two agents never share a
 * colour by accident.
 */
class NavGridDebugLinesTest {

    private val flat: (Float, Float) -> Float = { _, _ -> 0f }

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

    @Test
    fun onlyBlockedSamplesGetAMarker() {
        val lines = navGridDebugLines(World(), tile("..", "#."), flat)

        // Two segments per cross, one blocked sample.
        assertEquals(2, lines.size, "$lines")
    }

    @Test
    fun aStreamedCellsMarkersSitAtThatCellsOffset() {
        val grid = StreamedNavGrid(samplesPerCell = 2, sampleSize = 1f)
        grid.load(WorldCellCoord(2, 0), tile("#.", ".."))

        val lines = navGridDebugLines(World(), grid, flat)
        val markers = lines.filter { it.color != outlineColorOf(lines) }

        assertTrue(
            markers.all { it.start.x > 3f && it.start.x < 6f },
            "A cell at x=2 draws around x=4, not at the origin: ${markers.map { it.start.x }}",
        )
    }

    /** Residency is the thing a streamed overlay has to show; markers alone cannot. */
    @Test
    fun everyResidentCellIsOutlined() {
        val grid = StreamedNavGrid(samplesPerCell = 2, sampleSize = 1f)
        grid.load(WorldCellCoord(0, 0), tile("..", ".."))
        grid.load(WorldCellCoord(1, 0), tile("..", ".."))

        val lines = navGridDebugLines(World(), grid, flat)

        // No blocked samples at all, so everything drawn is outline: four edges per cell.
        assertTrue(lines.isNotEmpty())
        assertEquals(0, lines.size % 2, "Edges are drawn in equal segments per cell: ${lines.size}")
        assertTrue(lines.size >= 2 * 4, "Two cells should contribute two outlines: ${lines.size}")
    }

    @Test
    fun twoChasersGetTwoRouteColours() {
        val world = World()
        val first = world.create()
        val second = world.create()
        world.add(first, ChaseBehavior().also { it.path = route() })
        world.add(second, ChaseBehavior().also { it.path = route() })

        val colors = navGridDebugLines(world, tile("..", ".."), flat).map { it.color }.toSet()

        assertEquals(2, colors.size, "Both routes drew in the same colour: $colors")
    }

    /** The goal is where a route stops making sense first, so it gets a marker of its own. */
    @Test
    fun aRouteEndsInAGoalMarker() {
        val world = World()
        world.add(world.create(), ChaseBehavior().also { it.path = route() })

        val lines = navGridDebugLines(world, tile("..", ".."), flat)

        // One segment for the route itself, two for the cross on its goal.
        assertEquals(3, lines.size, "$lines")
    }

    private fun route() = listOf(Vec3f(0f, 0f, 0f), Vec3f(1f, 0f, 1f))

    /** The outline is the only thing drawn in its own colour, so a marker is anything else. */
    private fun outlineColorOf(lines: List<io.github.awakelab.awake.render.renderer.LineSegment>) =
        lines.groupBy { it.color }.maxBy { it.value.size }.key
}
