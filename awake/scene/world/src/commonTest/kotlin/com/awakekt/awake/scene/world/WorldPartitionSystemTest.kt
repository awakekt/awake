/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.world

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldPartitionSystemTest {

    private class RecordingStreamListener : WorldCellStreamListener {
        val loadedCells = mutableListOf<WorldCellCoord>()
        val unloadedCells = mutableListOf<WorldCellCoord>()

        override fun onCellLoad(world: World, coord: WorldCellCoord) {
            loadedCells.add(coord)
        }

        override fun onCellUnload(world: World, coord: WorldCellCoord) {
            unloadedCells.add(coord)
        }
    }

    @Test
    fun worldCellCoordResolvesFromWorldPosition() {
        val coord = WorldCellCoord.fromWorldPosition(550.0f, 1100.0f, cellSize = 512.0f)
        assertEquals(1, coord.x)
        assertEquals(2, coord.z)

        val negativeCoord = WorldCellCoord.fromWorldPosition(-100.0f, -600.0f, cellSize = 512.0f)
        assertEquals(-1, negativeCoord.x)
        assertEquals(-2, negativeCoord.z)
    }

    @Test
    fun worldPartitionSystemLoadsNearbyCellsAndUnloadsDistantCells() {
        val world = World()
        val listener = RecordingStreamListener()
        val config = WorldPartitionConfig(
            cellSize = 256.0f,
            loadingRadius = 512.0f,
            unloadRadius = 768.0f,
        )
        val system = WorldPartitionSystem(config = config, streamListener = listener)

        // 1. Initial observer at origin (0, 0, 0)
        system.updateObserverPosition(world, Vec3f(0f, 0f, 0f))
        assertTrue(system.activeCells.isNotEmpty(), "Active cells should be populated at origin.")
        assertTrue(listener.loadedCells.contains(WorldCellCoord(0, 0)))

        // 2. Move observer far away to (2000, 0, 2000)
        system.updateObserverPosition(world, Vec3f(2000f, 0f, 2000f))

        // Origin cell [0, 0] should now be unloaded
        assertTrue(listener.unloadedCells.contains(WorldCellCoord(0, 0)), "Origin cell should be unloaded.")
        val newTargetCoord = WorldCellCoord.fromWorldPosition(2000f, 2000f, cellSize = 256f)
        assertTrue(system.activeCells.contains(newTargetCoord), "New target cell should be active.")

        // 3. Clear system
        system.clear(world)
        assertTrue(system.activeCells.isEmpty(), "Active cells should be empty after clear.")
    }
}
