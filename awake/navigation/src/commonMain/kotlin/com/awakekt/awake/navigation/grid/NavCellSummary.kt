/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.grid

/** Which edge of a cell a border sample sits on. Order is the one [NavCellSummary] indexes by. */
enum class CellSide {
    /** Low Z. */
    North,

    /** High X. */
    East,

    /** High Z. */
    South,

    /** Low X. */
    West,

    ;

    /** The side of the neighbouring cell that touches this one. */
    val opposite: CellSide
        get() = when (this) {
            North -> South
            East -> West
            South -> North
            West -> East
        }

    internal companion object {
        val ordered = entries.toList()
    }
}

/**
 * What one cell contributes to long-range routing, without the cell being loaded.
 *
 * A [NavGridTile] is 32KB and answers every question; this is a few hundred bytes and answers one:
 * *can something get from this edge to that edge through this cell, and where does it cross?* That
 * is what a route across terrain nobody has streamed needs, and it is small enough to keep for
 * every cell a world has ever described rather than only the resident ones.
 *
 * Regions are the connected walkable areas of the cell, numbered from zero. Two border samples in
 * the same region are reachable from each other *within this cell*; two in different regions are
 * not, and that distinction is the whole reason a summary is regions rather than one bit per cell.
 * A cell split by a ridge would otherwise claim a crossing that does not exist, sending an agent
 * confidently at a wall — the classic hierarchical-pathfinding lie.
 *
 * [borderRegions] holds, per [CellSide] and per sample along that side, the region touching that
 * border sample, or [NO_REGION] where the edge is blocked.
 */
class NavCellSummary internal constructor(
    /** Samples along one edge; matches the tile this was summarised from. */
    val samplesPerSide: Int,
    /** Connected walkable areas in this cell. Zero means nothing here is walkable. */
    val regionCount: Int,
    private val borderRegions: Array<IntArray>,
) {
    /** The region touching [side] at [index] along it, or [NO_REGION] when that sample is blocked. */
    fun regionAt(side: CellSide, index: Int): Int {
        require(index in 0 until samplesPerSide) {
            "Border index must be in 0 until $samplesPerSide; was $index."
        }
        return borderRegions[side.ordinal][index]
    }

    /** Whether anything can enter or leave through [side] at all. */
    fun isOpen(side: CellSide): Boolean = borderRegions[side.ordinal].any { it != NO_REGION }

    companion object {
        /** No walkable sample here, so nothing crosses at this border position. */
        const val NO_REGION = -1
    }
}

/**
 * Reduces a baked tile to what long-range routing needs.
 *
 * Regions are flood-filled with the *same* connectivity rule the fine search uses — see [canStep].
 */
fun NavGridTile.summarize(): NavCellSummary {
    require(width == depth) { "A summarised tile must be square; was ${width}x$depth." }
    val regions = IntArray(width * depth) { NavCellSummary.NO_REGION }
    var regionCount = 0
    for (start in regions.indices) {
        val unvisited = regions[start] == NavCellSummary.NO_REGION
        if (unvisited && isWalkable(start % width, start / width)) {
            fillRegion(regions, start, regionCount)
            regionCount++
        }
    }

    val border = Array(CellSide.ordered.size) { IntArray(width) }
    for (index in 0 until width) {
        border[CellSide.North.ordinal][index] = regions[index]
        border[CellSide.South.ordinal][index] = regions[(depth - 1) * width + index]
        border[CellSide.West.ordinal][index] = regions[index * width]
        border[CellSide.East.ordinal][index] = regions[index * width + (width - 1)]
    }
    return NavCellSummary(width, regionCount, border)
}

/** Flood-fills everything reachable from [start] as [region], in [regions]. */
private fun NavGridTile.fillRegion(regions: IntArray, start: Int, region: Int) {
    val stack = ArrayList<Int>()
    regions[start] = region
    stack.add(start)
    while (stack.isNotEmpty()) {
        val node = stack.removeAt(stack.size - 1)
        val x = node % width
        val z = node / width
        for (direction in 0 until STEP_COUNT) {
            val nx = x + STEP_X[direction]
            val nz = z + STEP_Z[direction]
            val neighbour = nz * width + nx
            if (canStep(x, z, STEP_X[direction], STEP_Z[direction]) &&
                regions[neighbour] == NavCellSummary.NO_REGION
            ) {
                regions[neighbour] = region
                stack.add(neighbour)
            }
        }
    }
}

/**
 * The fine search's own step rule, corner included.
 *
 * A looser rule here would merge two regions the search refuses to move between, which is exactly
 * the false connectivity a coarse graph must never claim: the corridor would look fine and the
 * local path would fail every time an agent reached the pinch.
 */
private fun NavGridTile.canStep(x: Int, z: Int, dx: Int, dz: Int): Boolean {
    val nx = x + dx
    val nz = z + dz
    val onTile = nx in 0 until width && nz in 0 until depth
    return onTile &&
        isWalkable(nx, nz) &&
        (dx == 0 || dz == 0 || (isWalkable(x + dx, z) && isWalkable(x, z + dz)))
}

private const val STEP_COUNT = 8
private val STEP_X = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
private val STEP_Z = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)
