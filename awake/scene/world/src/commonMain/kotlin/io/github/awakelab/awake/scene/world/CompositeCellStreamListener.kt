/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.world

import io.github.awakelab.awake.ecs.World
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * One listener that streams several things per cell.
 *
 * `WorldPartitionSystem` holds a single async listener, so a world that streams both navigation
 * and terrain meshes would otherwise have its second registration silently replace its first.
 * That is the failure this exists to prevent — nothing errors, one of the two simply never runs.
 *
 * Loads run concurrently, because they are independent by construction: each produces a
 * [CellContent] and none of them touch the `World`. The applies run in list order on the frame
 * thread, so a listener that depends on an earlier one's entities goes after it.
 *
 * Unloads run in reverse order, for the same reason teardown usually does: whatever was built on
 * top of something else comes off first.
 */
class CompositeCellStreamListener(
    private val listeners: List<AsyncWorldCellStreamListener>,
) : AsyncWorldCellStreamListener {

    init {
        require(listeners.isNotEmpty()) { "A composite cell stream listener needs at least one listener." }
    }

    constructor(vararg listeners: AsyncWorldCellStreamListener) : this(listeners.toList())

    /**
     * Every listener's load, then one apply that runs them all.
     *
     * A failure in any of them fails the whole cell rather than applying a half-loaded one:
     * `coroutineScope` cancels the siblings and rethrows, and the partition system treats it the
     * same way it treats a cancelled load — nothing is applied.
     */
    override suspend fun loadCell(coord: WorldCellCoord): CellContent = coroutineScope {
        val contents = listeners.map { listener -> async { listener.loadCell(coord) } }.awaitAll()
        CellContent { world -> contents.forEach { it.applyTo(world) } }
    }

    override fun onCellUnload(world: World, coord: WorldCellCoord) {
        for (index in listeners.indices.reversed()) listeners[index].onCellUnload(world, coord)
    }
}
