/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.world

import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.world.WorldCellCoord

/**
 * Handle returned when a batch of cell [SceneNode]s is instantiated into the active scene.
 */
fun interface SpawnedNode {
    /** Removes the spawned node batch from the scene. */
    fun despawn()
}

/**
 * Frame-budgeted spawner and despawner for streamed world-cell [SceneNode] hierarchies.
 *
 * Queues cell child nodes when a cell enters the streaming radius ([enqueueCell]), moves spawned
 * batches to a despawn queue when a cell leaves ([unloadCell]), and drains at most [nodesPerFrame]
 * nodes per [step] call in each direction so entering or leaving a dense cell never stalls a frame.
 *
 * @property nodesPerFrame Maximum node weight to spawn and despawn during a single [step] call.
 * @param spawn Callback that instantiates a batch [SceneNode] into the scene and returns its [SpawnedNode] handle.
 */
class BudgetedCellNodeSpawner(
    val nodesPerFrame: Int = DEFAULT_NODES_PER_FRAME,
    private val spawn: (SceneNode) -> SpawnedNode,
) : AutoCloseable {
    init {
        require(nodesPerFrame > 0) { "nodesPerFrame must be positive, not $nodesPerFrame." }
    }

    private val shown = LinkedHashMap<WorldCellCoord, ShownCell>()
    private val leaving = ArrayDeque<SpawnedBatch>()

    /** Number of child nodes currently waiting to be spawned across all active cells. */
    val pendingSpawnCount: Int
        get() = shown.values.sumOf { it.waiting.size }

    /** Number of spawned batches from unloaded cells waiting to be despawned. */
    val pendingDespawnBatches: Int
        get() = leaving.size

    /**
     * Enqueues [children] under [root] for [coord] if [coord] is not already resident.
     *
     * @param coord World cell coordinate being loaded.
     * @param root Cell root [SceneNode] template used to wrap each spawned batch.
     * @param children Child prop/entity nodes to instantiate across subsequent frames.
     */
    fun enqueueCell(
        coord: WorldCellCoord,
        root: SceneNode,
        children: List<SceneNode> = root.children,
    ) {
        if (coord !in shown) {
            shown[coord] = ShownCell(root, ArrayDeque(children))
        }
    }

    /**
     * Marks [coord] as unloaded, discarding any unspawned nodes for that cell and queuing its
     * already-spawned batches for frame-budgeted despawning.
     *
     * @param coord World cell coordinate being unloaded.
     */
    fun unloadCell(coord: WorldCellCoord) {
        shown.remove(coord)?.let { cell ->
            leaving.addAll(cell.despawns)
        }
    }

    /**
     * Advances one frame's worth of despawns and spawns, each bounded by [nodesPerFrame].
     */
    fun step() {
        despawnSome()
        spawnSome()
    }

    /**
     * Immediately despawns all active and leaving cell batches and clears all queues.
     */
    fun clear() {
        leaving.forEach { it.handle.despawn() }
        leaving.clear()
        shown.values.forEach { cell ->
            cell.despawns.forEach { it.handle.despawn() }
        }
        shown.clear()
    }

    override fun close() {
        clear()
    }

    private fun despawnSome() {
        var budget = nodesPerFrame
        while (budget > 0) {
            val batch = leaving.removeFirstOrNull() ?: break
            batch.handle.despawn()
            budget -= batch.nodes
        }
    }

    private fun spawnSome() {
        var budget = nodesPerFrame
        for (cell in shown.values) {
            if (budget <= 0) break
            if (cell.waiting.isNotEmpty()) {
                budget = spawnBatchForCell(cell, budget)
            }
        }
    }

    private fun spawnBatchForCell(cell: ShownCell, initialBudget: Int): Int {
        var budget = initialBudget
        val batch = ArrayList<SceneNode>()
        var nodes = 0
        while (budget > 0) {
            val prop = cell.waiting.removeFirstOrNull() ?: break
            batch += prop
            val weight = 1 + prop.children.size
            nodes += weight
            budget -= weight
        }
        val spawned = spawn(cell.root.copy(children = batch))
        cell.despawns += SpawnedBatch(nodes, spawned)
        return budget
    }

    private class ShownCell(
        val root: SceneNode,
        val waiting: ArrayDeque<SceneNode>,
    ) {
        val despawns = ArrayList<SpawnedBatch>()
    }

    private class SpawnedBatch(
        val nodes: Int,
        val handle: SpawnedNode,
    )

    /** Default budget constants for [BudgetedCellNodeSpawner]. */
    companion object {
        /** Default maximum number of nodes spawned or despawned per frame. */
        const val DEFAULT_NODES_PER_FRAME = 200
    }
}
