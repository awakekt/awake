/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.world

import io.github.awakelab.awake.ecs.World

/**
 * What a finished cell load produces: an action that puts it into the `World`.
 *
 * The load itself runs off the frame thread and this runs on it. Returning an applicator, rather
 * than letting the loader touch the `World` directly, is what keeps cell streaming from being the
 * one place in the engine that races the ECS -- and it is structural rather than documented,
 * because [AsyncWorldCellStreamListener.loadCell] is handed no `World` to race with.
 */
fun interface CellContent {
    /** Runs on the frame thread, only while the cell is still active. */
    fun applyTo(world: World)
}

/**
 * The streaming callbacks for a consumer whose cells cost real IO.
 *
 * [WorldCellStreamListener]'s synchronous `onCellLoad` runs inline on the frame thread, which is
 * fine for cells built from memory and a frame hitch for anything else. This splits that into a
 * suspending half and a frame-thread half; see [CellContent].
 *
 * [onCellUnload] stays synchronous and frame-thread on both interfaces, because removing entities
 * is ECS mutation either way.
 */
interface AsyncWorldCellStreamListener {
    /**
     * Loads [coord]'s content, off the frame thread.
     *
     * Cancelled if the cell leaves the unload radius before this returns, so a long load should
     * be cooperative. A result that lands anyway is discarded rather than applied.
     */
    suspend fun loadCell(coord: WorldCellCoord): CellContent

    fun onCellUnload(world: World, coord: WorldCellCoord)
}
