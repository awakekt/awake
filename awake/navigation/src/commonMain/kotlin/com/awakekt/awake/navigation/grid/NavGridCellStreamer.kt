/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.grid

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.world.AsyncWorldCellStreamListener
import com.awakekt.awake.scene.world.CellContent
import com.awakekt.awake.scene.world.WorldCellCoord
import com.awakekt.awake.scene.world.WorldPartitionConfig

/**
 * Keeps a [StreamedNavGrid] populated with the cells `WorldPartitionSystem` has streamed in.
 *
 * Wire it as the partition system's async listener:
 * ```
 * val grid = StreamedNavGrid(samplesPerCell = 512, sampleSize = 1f)
 * WorldPartitionSystem(
 *     config,
 *     asyncStreamListener = NavGridCellStreamer(grid, config, terrain::heightmapAt),
 *     loadScope = runtimeScope,
 * )
 * ```
 *
 * The bake is the expensive half — a 512-sample cell is a quarter of a million slope tests — and
 * it runs in [loadCell], off the frame thread. What comes back is one immutable tile and a
 * one-line apply, so the frame thread pays for a map write and nothing else. Cancellation needs
 * no handling here: a cell that leaves the radius mid-bake has its job cancelled and its result
 * discarded by the partition system, and a tile that lands anyway would only be published by an
 * apply that never runs.
 *
 * **A partition system holds one async listener.** A consumer that also streams entities or meshes
 * per cell composes the two itself — load both, apply both — rather than registering two listeners,
 * because the second would replace the first rather than run beside it. Nothing streams anything
 * else yet, so a composite listener is not written until there are two things to compose.
 *
 * [heightmapAt] returns the terrain for one cell, in that cell's own local coordinates — sample
 * `(0, 0)` is the cell's corner, not the world's. Null means nothing has been authored there,
 * which loads no tile: navigation then refuses to route into the cell, which is the honest answer
 * for ground that does not exist.
 */
class NavGridCellStreamer(
    private val grid: StreamedNavGrid,
    config: WorldPartitionConfig,
    private val heightmapAt: suspend (WorldCellCoord) -> Heightmap?,
    private val maxSlopeDegrees: Float = DEFAULT_MAX_SLOPE_DEGREES,
    /**
     * Told what each cell contributes to long-range routing, if anything is routing long-range.
     *
     * Filled from the bake that was happening anyway and kept after the cell unloads, which is
     * what lets a route be planned across ground that is no longer loaded. Null when a world only
     * ever paths within the streaming radius, and then nothing is summarised or kept.
     */
    private val coarse: CoarseNavGraph? = null,
) : AsyncWorldCellStreamListener {

    init {
        // The seam that fails silently if it is wrong: a grid whose cell is a different size from
        // the streaming cell puts every tile at the wrong world position, and the symptom is paths
        // that stop at a boundary rather than an error anyone can trace.
        require(grid.worldCellSize == config.cellSize) {
            "A nav grid of ${grid.samplesPerCell} samples at ${grid.sampleSize} covers " +
                "${grid.worldCellSize}m, but the world streams ${config.cellSize}m cells."
        }
    }

    override suspend fun loadCell(coord: WorldCellCoord): CellContent {
        val heightmap = heightmapAt(coord) ?: return CellContent { }
        val tile = heightmap.bakeNavGridCell(grid.samplesPerCell, grid.sampleSize, maxSlopeDegrees)
        // Summarised off the frame thread with the bake, since it reads the same tile and the
        // frame-thread half should stay a map write.
        val summary = coarse?.let { tile.summarize() }
        return CellContent {
            grid.load(coord, tile)
            if (summary != null) coarse.put(coord, summary)
        }
    }

    /**
     * Runs on the frame thread, like every unload. Any search still reading this tile keeps its
     * own reference to it — see [StreamedNavGrid].
     *
     * The coarse summary deliberately stays: routing across cells that are *not* loaded is the
     * only reason it exists.
     */
    override fun onCellUnload(world: World, coord: WorldCellCoord) {
        grid.unload(coord)
    }
}
