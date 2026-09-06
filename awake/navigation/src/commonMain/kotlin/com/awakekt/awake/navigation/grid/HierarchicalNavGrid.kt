/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.grid

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.navigation.NavMesh
import com.awakekt.awake.scene.world.WorldCellCoord
import kotlin.math.abs
import kotlin.math.floor

/**
 * Paths that leave the loaded world: fine steps while the ground is there, a corridor beyond it.
 *
 * [StreamedNavGrid] alone cannot answer "walk to the town three kilometres away" — everything past
 * the streaming radius reads as blocked, so the search returns nothing and the agent stands still.
 * This asks [coarse] which cells the route passes through, finds how far along that corridor the
 * world is actually loaded, and hands back a fine path to the point where it would leave.
 *
 * **The path is a leg, not the whole journey.** An agent walks it, more cells stream in behind the
 * moving observer, and the next request returns the next leg — which is what
 * [PathRequest][com.awakekt.awake.navigation.PathRequest]'s repath interval already
 * does for free. The alternative, planning the whole route through cells nobody has verified, is
 * the hierarchical-pathfinding lie this design exists to avoid: it produces a confident path
 * through a wall that nobody discovers until an agent walks into it.
 *
 * A route entirely inside the resident set never reaches the coarse layer at all — the fine search
 * answers first and this returns exactly what [StreamedNavGrid] would.
 */
class HierarchicalNavGrid(
    private val grid: StreamedNavGrid,
    private val coarse: CoarseNavGraph,
) : NavMesh {

    override fun findPath(start: Vec3f, end: Vec3f): List<Vec3f> {
        val direct = grid.findPath(start, end)
        return if (direct.isNotEmpty()) direct else legTowards(start, end)
    }

    /**
     * The part of the journey that can be verified right now, or nothing.
     *
     * Same-cell failures come back empty rather than going to the coarse layer: the fine search has
     * already looked at every sample between the two points and said no, and a corridor of one cell
     * cannot add anything to that.
     */
    private fun legTowards(start: Vec3f, end: Vec3f): List<Vec3f> {
        val startCell = cellOf(start)
        val goalCell = cellOf(end)
        val corridor = if (startCell == goalCell) null else coarse.corridor(startCell, goalCell)
        return if (corridor == null) emptyList() else furthestReachableLeg(start, corridor)
    }

    /**
     * The longest leg along [corridor] the fine grid will actually confirm, trying far exits first.
     *
     * The two layers disagree in one direction and it has to be handled here: the coarse graph
     * knows a route exists across cells, but at sample resolution the way through the *loaded* part
     * may need a detour into a cell that is not loaded yet. Aiming at the furthest loaded corridor
     * cell then fails, and an agent that treats one failure as "no route" stops dead a few metres
     * into a journey the graph correctly says is possible.
     *
     * So each candidate exit is offered to the fine search and the first one it confirms wins. The
     * near exits are the ones most likely to be reachable, which is why the fallback ends at the
     * agent's own cell rather than giving up earlier.
     */
    private fun furthestReachableLeg(start: Vec3f, corridor: List<WorldCellCoord>): List<Vec3f> {
        var index = corridor.indexOfLast { it in grid.residentCells }
        var leg = emptyList<Vec3f>()
        while (leg.isEmpty() && index >= 0) {
            val exit = exitPoint(corridor, index)
            if (exit != null) leg = grid.findPath(start, exit)
            index--
        }
        return leg
    }

    /**
     * Where to aim to leave `corridor[index]`: just inside it, on the edge it leaves by.
     *
     * Walking to the middle of that cell would be wrong at a boundary the corridor crosses at a
     * corner, and walking *onto* the border sample would put the agent on the first sample of a
     * cell that may not have loaded. One sample inside is both reachable and pointed the right way.
     */
    private fun exitPoint(corridor: List<WorldCellCoord>, index: Int): Vec3f? {
        val cell = corridor.getOrNull(index)
        val next = corridor.getOrNull(index + 1)
        val side = if (cell != null && next != null) sideTowards(cell, next) else null
        return if (cell != null && side != null) borderTarget(cell, side) else null
    }

    /** The edge of [cell] that faces [next], or null when they are not orthogonal neighbours. */
    private fun sideTowards(cell: WorldCellCoord, next: WorldCellCoord): CellSide? = when {
        next.x == cell.x && next.z == cell.z - 1 -> CellSide.North
        next.x == cell.x + 1 && next.z == cell.z -> CellSide.East
        next.x == cell.x && next.z == cell.z + 1 -> CellSide.South
        next.x == cell.x - 1 && next.z == cell.z -> CellSide.West
        else -> null
    }

    /**
     * The walkable sample nearest the middle of [side], stepped one sample back into the cell.
     *
     * The middle rather than the first opening found: a corridor that crosses a wide edge should
     * aim at the gap it is most likely to keep using as more terrain loads, and the middle is the
     * cheapest stable choice. Blocked edges yield null, which drops the whole plan rather than
     * sending an agent at a cliff.
     */
    private fun borderTarget(cell: WorldCellCoord, side: CellSide): Vec3f? {
        val tile = grid.tileAt(cell)
        val samples = grid.samplesPerCell
        val middle = samples / 2
        var bestIndex = -1
        for (index in 0 until samples) {
            val (x, z) = borderSample(side, index, samples)
            val walkable = tile != null && tile.isWalkable(x, z)
            val nearer = bestIndex < 0 || abs(index - middle) < abs(bestIndex - middle)
            if (walkable && nearer) bestIndex = index
        }
        if (tile == null || bestIndex < 0) return null

        val (borderX, borderZ) = borderSample(side, bestIndex, samples)
        val insideX = (borderX + INWARD_X[side.ordinal]).coerceIn(0, samples - 1)
        val insideZ = (borderZ + INWARD_Z[side.ordinal]).coerceIn(0, samples - 1)
        val (x, z) = if (tile.isWalkable(insideX, insideZ)) insideX to insideZ else borderX to borderZ
        return Vec3f(
            (cell.x * samples + x) * grid.sampleSize,
            0f,
            (cell.z * samples + z) * grid.sampleSize,
        )
    }

    private fun borderSample(side: CellSide, index: Int, samples: Int): Pair<Int, Int> = when (side) {
        CellSide.North -> index to 0
        CellSide.South -> index to samples - 1
        CellSide.West -> 0 to index
        CellSide.East -> samples - 1 to index
    }

    private fun cellOf(position: Vec3f): WorldCellCoord {
        val size = grid.worldCellSize
        return WorldCellCoord(floor(position.x / size).toInt(), floor(position.z / size).toInt())
    }

    private companion object {
        /** One sample back from each side, in the order [CellSide] declares. */
        val INWARD_X = intArrayOf(0, -1, 0, 1)
        val INWARD_Z = intArrayOf(1, 0, -1, 0)
    }
}
