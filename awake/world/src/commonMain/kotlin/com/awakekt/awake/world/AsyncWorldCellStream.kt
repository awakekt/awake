/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.world

import com.awakekt.awake.ecs.World

/**
 * What a finished cell load produces: an action that puts it into the `World`.
 *
 * The load itself runs off the frame thread and this runs on it. Returning an applicator, rather
 * than letting the loader touch the `World` directly, is what keeps cell streaming from being the
 * one place in the engine that races the ECS -- and it is structural rather than documented,
 * because [AsyncWorldCellStreamListener.loadCell] is handed no `World` to race with.
 */
fun interface CellContent {
    /**
     * Runs on the frame thread, only while the cell is still active.
     *
     * @param world The active ECS world instance.
     */
    fun applyTo(world: World)
}

/**
 * The streaming callbacks for a consumer whose cells cost real IO.
 *
 * `scene:world`'s `WorldCellStreamListener` has a synchronous `onCellLoad` that runs inline on the
 * frame thread, which is fine for cells built from memory and a frame hitch for anything else. This
 * splits that into a suspending half and a frame-thread half; see [CellContent].
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
     *
     * @param coord The world cell coordinate to load.
     * @return A [CellContent] applicator that applies the loaded content onto the frame thread.
     */
    suspend fun loadCell(coord: WorldCellCoord): CellContent

    /**
     * Unloads content for [coord] synchronously on the frame thread.
     *
     * @param world The active ECS world instance.
     * @param coord The world cell coordinate being unloaded.
     */
    fun onCellUnload(world: World, coord: WorldCellCoord)
}
