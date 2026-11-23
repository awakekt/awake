/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.world

import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.world.WorldCellCoord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BudgetedCellNodeSpawnerTest {

    @Test
    fun spawnsAndDespawnsInFrameBudgetedBatches() {
        val activeBatches = mutableListOf<List<String?>>()
        val spawner = BudgetedCellNodeSpawner(nodesPerFrame = 3) { batchRoot ->
            val names = batchRoot.children.map { it.name }
            activeBatches.add(names)
            SpawnedNode { activeBatches.remove(names) }
        }

        val coord = WorldCellCoord(2, 4)
        val children = (1..7).map { i -> SceneNode(name = "prop-$i") }
        val root = SceneNode(name = "cell-2-4", children = children)

        spawner.enqueueCell(coord, root)
        assertEquals(7, spawner.pendingSpawnCount)

        // Frame 1: spawns 3 props
        spawner.step()
        assertEquals<List<List<String?>>>(listOf(listOf("prop-1", "prop-2", "prop-3")), activeBatches)
        assertEquals(4, spawner.pendingSpawnCount)

        // Frame 2: spawns next 3 props
        spawner.step()
        assertEquals(2, activeBatches.size)
        assertEquals(1, spawner.pendingSpawnCount)

        // Unload before the 7th prop is spawned: unspawned 7th prop is dropped, 2 batches queued to despawn
        spawner.unloadCell(coord)
        assertEquals(0, spawner.pendingSpawnCount)
        assertEquals(2, spawner.pendingDespawnBatches)

        // Frame 3: despawns first batch of 3 nodes
        spawner.step()
        assertEquals(1, activeBatches.size)
        assertEquals(1, spawner.pendingDespawnBatches)

        // Frame 4: despawns second batch of 3 nodes
        spawner.step()
        assertTrue(activeBatches.isEmpty())
        assertEquals(0, spawner.pendingDespawnBatches)
    }

    @Test
    fun sharedBudgetAcrossMultipleSpawnersCapsTotalNodesPerFrame() {
        var totalSpawned = 0
        var totalDespawned = 0
        fun makeSpawner(): BudgetedCellNodeSpawner = BudgetedCellNodeSpawner(nodesPerFrame = 10) { batchRoot ->
            val count = batchRoot.children.size
            totalSpawned += count
            SpawnedNode { totalDespawned += count }
        }

        val spawners = listOf(makeSpawner(), makeSpawner(), makeSpawner())
        spawners.forEachIndexed { idx, spawner ->
            val children = (1..4).map { i -> SceneNode(name = "s$idx-p$i") }
            spawner.enqueueCell(WorldCellCoord(idx, 0), SceneNode(name = "cell-$idx", children = children))
        }

        // Frame 1: shared budget of 5 across 3 spawners spawns 4 from spawner[0] + 1 from spawner[1] = 5 total.
        var remaining = 5
        for (spawner in spawners) remaining = spawner.stepSpawns(remaining)
        assertEquals(0, remaining)
        assertEquals(5, totalSpawned)
        assertEquals(0, spawners[0].pendingSpawnCount)
        assertEquals(3, spawners[1].pendingSpawnCount)
        assertEquals(4, spawners[2].pendingSpawnCount)

        // Frame 2: shared budget of 10 drains the remaining 7 nodes with 3 left over.
        remaining = 10
        for (spawner in spawners) remaining = spawner.stepSpawns(remaining)
        assertEquals(3, remaining)
        assertEquals(12, totalSpawned)

        // Unload all cells and despawn under a shared budget of 5.
        spawners.forEachIndexed { idx, spawner -> spawner.unloadCell(WorldCellCoord(idx, 0)) }
        remaining = 5
        for (spawner in spawners) remaining = spawner.stepDespawns(remaining)
        assertEquals(0, remaining)
        assertEquals(5, totalDespawned)
    }
}
