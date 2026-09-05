/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.navigation.grid

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.navigation.NavMesh
import io.github.awakelab.awake.scene.world.WorldCellCoord
import kotlin.concurrent.Volatile

/**
 * A [NavMesh] over the tiles that are currently streamed in, one per world cell.
 *
 * Cells are the streaming system's own [WorldCellCoord] rather than a second spatial scheme, so a
 * consumer bakes a tile in its cell loader, hands it to [load], and drops it in `onCellUnload`.
 * A search crosses tile boundaries without knowing they exist — the field it reads is addressed in
 * whole-world samples — and stops at the edge of the resident set, because an unbaked sample reads
 * as blocked. That last part is a real limitation, not an oversight: a route into a cell nobody
 * has loaded cannot be verified, and the plan's answer is a coarse cell-level graph rather than
 * optimistically walking into unloaded terrain. See
 * docs/tasks/2026-08-30-navgrid-navigation-plan.md.
 *
 * **[samplesPerCell] must divide the streaming config's `cellSize` exactly**, which is what
 * [worldCellSize] is for: a tile that is not a whole number of samples leaves a seam where a path
 * fails to cross a cell boundary for reasons no test is looking at. [load] rejects a mismatched
 * tile rather than accepting a grid that is subtly off by a fraction of a sample.
 *
 * Thread-safety is one-directional and deliberate. [load] and [unload] replace the tile map rather
 * than mutating it, and [findPath] reads that reference once, so a search running off the frame
 * thread sees a consistent snapshot of immutable tiles even while the frame thread streams cells
 * in and out. Unloading during a search is therefore safe and needs no cancellation: the searcher
 * keeps reading tiles the world has moved on from, and the worst outcome is a route through
 * terrain that has just left the radius — which its owner is about to leave too.
 */
class StreamedNavGrid(
    /** Navigation samples along each axis of one world cell. */
    val samplesPerCell: Int,
    /** Metres between adjacent samples. */
    val sampleSize: Float,
) : NavMesh {

    init {
        require(samplesPerCell > 0) { "samplesPerCell must be positive; was $samplesPerCell." }
        require(sampleSize > 0f && sampleSize.isFinite()) {
            "sampleSize must be positive and finite; was $sampleSize."
        }
    }

    /** The streaming `cellSize` this grid is baked for. A different one is a configuration error. */
    val worldCellSize: Float get() = samplesPerCell * sampleSize

    @Volatile
    private var tiles: Map<WorldCellCoord, NavGridTile> = emptyMap()

    /** The cells with a baked tile right now. */
    val residentCells: Set<WorldCellCoord> get() = tiles.keys

    /** [coord]'s baked tile, or null when that cell is not resident. For debug drawing and tests. */
    fun tileAt(coord: WorldCellCoord): NavGridTile? = tiles[coord]

    /** Publishes [tile] as [coord]'s navigation, replacing any tile already there. */
    fun load(coord: WorldCellCoord, tile: NavGridTile) {
        require(tile.width == samplesPerCell && tile.depth == samplesPerCell) {
            "A tile for $coord must be ${samplesPerCell}x$samplesPerCell samples; " +
                "was ${tile.width}x${tile.depth}."
        }
        require(tile.cellSize == sampleSize) {
            "A tile for $coord must be baked at sampleSize $sampleSize; was ${tile.cellSize}."
        }
        tiles = tiles + (coord to tile)
    }

    /** Drops [coord]'s navigation. Searches already in flight keep reading the tile they hold. */
    fun unload(coord: WorldCellCoord) {
        if (coord in tiles) tiles = tiles - coord
    }

    /** Drops every tile. */
    fun clear() {
        tiles = emptyMap()
    }

    override fun findPath(start: Vec3f, end: Vec3f): List<Vec3f> {
        val field = ResidentField(tiles, samplesPerCell, sampleSize)
        return field.smoothPath(field.findPath(start, end))
    }
}

/**
 * One snapshot of the resident tiles, read as a single whole-world sample grid.
 *
 * Taken by value at the start of a search so streaming cannot change the field underneath it.
 * Caches the last tile it looked up, because a search walks a corridor and consecutive samples are
 * almost always in the same cell.
 */
private class ResidentField(
    private val tiles: Map<WorldCellCoord, NavGridTile>,
    private val samplesPerCell: Int,
    override val sampleSize: Float,
) : NavField {

    private var cachedCoord: WorldCellCoord? = null
    private var cachedTile: NavGridTile? = null

    override fun isWalkable(x: Int, z: Int): Boolean {
        val coord = WorldCellCoord(x.floorDiv(samplesPerCell), z.floorDiv(samplesPerCell))
        val tile = tileAt(coord) ?: return false
        return tile.isWalkable(x.mod(samplesPerCell), z.mod(samplesPerCell))
    }

    private fun tileAt(coord: WorldCellCoord): NavGridTile? {
        if (coord != cachedCoord) {
            cachedCoord = coord
            cachedTile = tiles[coord]
        }
        return cachedTile
    }
}
