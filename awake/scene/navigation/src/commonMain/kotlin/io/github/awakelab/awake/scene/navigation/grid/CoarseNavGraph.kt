/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.navigation.grid

import io.github.awakelab.awake.scene.world.WorldCellCoord
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Which cells connect to which, for routing beyond what is loaded.
 *
 * The fine grid can only answer questions about resident cells, which is correct and is also why an
 * agent cannot be told to walk to a town three kilometres away. This holds the other half: one
 * [NavCellSummary] per known cell, kept after the cell unloads, and an A* over them that returns a
 * *corridor* — the sequence of cells a route passes through. Turning that corridor into steps is
 * the fine grid's job, on the part of it that is currently loaded.
 *
 * Nodes are `(cell, region)` rather than cells. A cell split by a ridge has two regions that do not
 * connect inside it, and a cell-level graph would happily route through the wall: the corridor
 * would look right and every local path along it would fail. Regions cost a few hundred bytes per
 * cell and remove that failure entirely.
 *
 * Summaries accumulate and are not dropped on unload — that is the point of having them. A world
 * that describes cells procedurally can also [put] summaries for cells that have never been
 * resident, which is what makes a route to somewhere the player has never been possible at all.
 */
class CoarseNavGraph(
    /** Metres along one edge of a cell. Only used to weight the search, never to place anything. */
    private val worldCellSize: Float,
) {
    init {
        require(worldCellSize > 0f && worldCellSize.isFinite()) {
            "worldCellSize must be positive and finite; was $worldCellSize."
        }
    }

    @Volatile
    private var summaries: Map<WorldCellCoord, NavCellSummary> = emptyMap()

    /** Every cell this graph can route through. */
    val knownCells: Set<WorldCellCoord> get() = summaries.keys

    fun summaryAt(coord: WorldCellCoord): NavCellSummary? = summaries[coord]

    /** Records what [coord] contributes to routing, replacing any summary already held. */
    fun put(coord: WorldCellCoord, summary: NavCellSummary) {
        summaries = summaries + (coord to summary)
    }

    /** Forgets [coord]. For a world whose terrain actually changed, not for unloading one. */
    fun remove(coord: WorldCellCoord) {
        if (coord in summaries) summaries = summaries - coord
    }

    fun clear() {
        summaries = emptyMap()
    }

    /**
     * The cells a route from [from] to [to] passes through, or null when none connects them.
     *
     * Both endpoints are cells rather than positions: this layer does not know where in a cell an
     * agent stands, and does not need to. Every region of [from] is a starting point, which is a
     * deliberate over-claim in exactly one place — the start cell is where the agent is, so the
     * fine search validates that first leg for real before anything walks anywhere.
     */
    fun corridor(from: WorldCellCoord, to: WorldCellCoord): List<WorldCellCoord>? {
        val cells = summaries
        val start = cells[from]
        return when {
            start == null || to !in cells -> null
            from == to -> listOf(from)
            else -> CorridorSearch(cells, worldCellSize, to).run(from, start)
        }
    }
}

/**
 * One A* over `(cell, region)` nodes.
 *
 * A class rather than functions passing five maps to each other, and an open *list* rather than a
 * heap: a corridor spans tens of cells, where a linear scan for the cheapest entry costs less than
 * maintaining the heap would. The fine search is the one that walks hundreds of thousands of
 * nodes; this one does not.
 */
private class CorridorSearch(
    private val cells: Map<WorldCellCoord, NavCellSummary>,
    private val worldCellSize: Float,
    private val goal: WorldCellCoord,
) {
    private val open = ArrayList<Node>()
    private val best = HashMap<Long, Float>()
    private val cameFrom = HashMap<Long, Long>()

    fun run(from: WorldCellCoord, start: NavCellSummary): List<WorldCellCoord>? {
        for (region in 0 until start.regionCount) {
            val key = key(from, region)
            best[key] = 0f
            open += Node(key, heuristic(from))
        }

        var corridor: List<WorldCellCoord>? = null
        while (open.isNotEmpty() && corridor == null) {
            val current = open.removeAt(lowestIndex())
            val coord = coordOf(current.key)
            if (coord == goal) corridor = reconstruct(current.key) else expand(current.key, coord)
        }
        return corridor
    }

    private fun expand(key: Long, coord: WorldCellCoord) {
        val summary = cells[coord] ?: return
        val region = regionOf(key)
        val cost = best[key] ?: return
        for (side in CellSide.ordered) {
            val neighbourCoord = coord.step(side)
            val neighbour = cells[neighbourCoord]
            if (neighbour != null) {
                relax(summary, neighbour, side, region, coord, key, cost)
            }
        }
    }

    @Suppress("LongParameterList")
    private fun relax(
        summary: NavCellSummary,
        neighbour: NavCellSummary,
        side: CellSide,
        region: Int,
        coord: WorldCellCoord,
        key: Long,
        cost: Float,
    ) {
        val neighbourCoord = coord.step(side)
        for (target in crossings(summary, neighbour, side, region)) {
            val neighbourKey = key(neighbourCoord, target)
            val tentative = cost + worldCellSize
            if (tentative < (best[neighbourKey] ?: Float.MAX_VALUE)) {
                best[neighbourKey] = tentative
                cameFrom[neighbourKey] = key
                open += Node(neighbourKey, tentative + heuristic(neighbourCoord))
            }
        }
    }

    /**
     * The neighbour regions reachable across [side], for samples where *both* cells are walkable.
     *
     * Sample-by-sample rather than "both sides have something open": two cells can each have a gap
     * on the shared edge without those gaps lining up, and a route through them does not exist.
     */
    private fun crossings(
        summary: NavCellSummary,
        neighbour: NavCellSummary,
        side: CellSide,
        region: Int,
    ): Set<Int> {
        val reachable = HashSet<Int>()
        if (summary.samplesPerSide == neighbour.samplesPerSide) {
            for (index in 0 until summary.samplesPerSide) {
                val here = summary.regionAt(side, index)
                val there = neighbour.regionAt(side.opposite, index)
                if (here == region && there != NavCellSummary.NO_REGION) reachable.add(there)
            }
        }
        return reachable
    }

    /** Straight-line cell distance. Never overestimates: a step costs a whole cell. */
    private fun heuristic(from: WorldCellCoord): Float {
        val dx = abs(from.x - goal.x).toFloat()
        val dz = abs(from.z - goal.z).toFloat()
        return sqrt(dx * dx + dz * dz) * worldCellSize
    }

    private fun reconstruct(goalKey: Long): List<WorldCellCoord> {
        val corridor = ArrayList<WorldCellCoord>()
        var key: Long? = goalKey
        while (key != null) {
            val coord = coordOf(key)
            // Regions are per-cell detail the caller has no use for; a corridor is cells, and two
            // regions of the same cell in sequence would read as walking on the spot.
            if (corridor.lastOrNull() != coord) corridor.add(coord)
            key = cameFrom[key]
        }
        corridor.reverse()
        return corridor
    }

    private fun lowestIndex(): Int {
        var index = 0
        for (candidate in open.indices) {
            if (open[candidate].priority < open[index].priority) index = candidate
        }
        return index
    }

    private class Node(val key: Long, val priority: Float)

    private companion object {
        const val REGION_BITS = 16
        const val COORD_BITS = 24
        const val COORD_MASK = 0xFF_FFFFL
        const val COORD_BIAS = 0x80_0000L

        fun key(coord: WorldCellCoord, region: Int): Long =
            ((coord.x.toLong() + COORD_BIAS) shl (COORD_BITS + REGION_BITS)) or
                ((coord.z.toLong() + COORD_BIAS) shl REGION_BITS) or
                region.toLong()

        fun coordOf(key: Long): WorldCellCoord = WorldCellCoord(
            x = (((key ushr (COORD_BITS + REGION_BITS)) and COORD_MASK) - COORD_BIAS).toInt(),
            z = (((key ushr REGION_BITS) and COORD_MASK) - COORD_BIAS).toInt(),
        )

        fun regionOf(key: Long): Int = (key and ((1L shl REGION_BITS) - 1)).toInt()
    }
}

/** The cell one step across [side]. */
internal fun WorldCellCoord.step(side: CellSide): WorldCellCoord = when (side) {
    CellSide.North -> WorldCellCoord(x, z - 1)
    CellSide.East -> WorldCellCoord(x + 1, z)
    CellSide.South -> WorldCellCoord(x, z + 1)
    CellSide.West -> WorldCellCoord(x - 1, z)
}
