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
}
