/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.world

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.world.AsyncWorldCellStreamListener
import com.awakekt.awake.world.CellContent
import com.awakekt.awake.world.WorldCellCoord
import com.awakekt.awake.world.WorldPartitionConfig
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// --8<-- [start:listener]
/** Spawns one named rock per cell, and removes it when the cell streams out. */
class RockCells : AsyncWorldCellStreamListener {
    private val spawned = HashMap<WorldCellCoord, List<Entity>>()

    override suspend fun loadCell(coord: WorldCellCoord): CellContent {
        // Off the frame thread: read files, decode, bake. No World access here.
        val names = listOf("rock ${coord.x},${coord.z}")
        // On the frame thread, and only if the cell is still in range.
        return CellContent { world ->
            spawned[coord] = names.map { name -> world.create().also { world.add(it, Name(name)) } }
        }
    }

    override fun onCellUnload(world: World, coord: WorldCellCoord) {
        spawned.remove(coord)?.forEach(world::destroy)
    }
}
// --8<-- [end:listener]

/** Every sample on the "Large worlds" guide is a region here, run against the real systems. */
class LargeWorldDocsSampleTest {

    @Test
    fun cellsStreamInAroundTheObserverAndOutBehindIt() = runTest {
        val world = World()
        // --8<-- [start:partition]
        val partition = WorldPartitionSystem(
            config = WorldPartitionConfig(cellSize = 256f, loadingRadius = 512f, unloadRadius = 768f),
            asyncStreamListener = RockCells(),
            loadScope = this, // a CoroutineScope that outlives the scene; the runtime has one
        )

        val player = world.create()
        world.add(player, Transform(position = Vec3f(0f, 0f, 0f)))
        world.add(player, StreamObserver) // streaming follows this entity
        // --8<-- [end:partition]

        partition.update(world, 0f)
        testScheduler.advanceUntilIdle()
        partition.update(world, 0f) // finished loads are applied at the start of the next update

        assertTrue(WorldCellCoord(0, 0) in partition.activeCells)
        assertTrue(world.hasRock("rock 0,0"), "the observer's cell is populated")

        world.add(player, Transform(position = Vec3f(5000f, 0f, 0f)))
        partition.update(world, 0f)

        assertTrue(WorldCellCoord(0, 0) !in partition.activeCells)
        assertTrue(!world.hasRock("rock 0,0"), "the cell left behind was emptied")
    }

    @Test
    fun theOriginFollowsAFarObserver() {
        val world = World()
        val player = world.create()
        world.add(player, Transform(position = Vec3f(5000f, 0f, 0f)))
        world.add(player, StreamObserver)
        // --8<-- [start:floating-origin]
        val origin = FloatingOriginSystem(threshold = 2048f, quantum = 1024f)
        // With physics: origin.addListener(PhysicsOriginShiftListener(physicsWorld))

        origin.update(world, 0f)

        val local = requireNotNull(world.get<Transform>(player)).position
        val absolute = requireNotNull(world.findWorldOrigin()).toAbsolute(local)
        // --8<-- [end:floating-origin]

        assertTrue(abs(local.x) < 2048f, "the observer was recentred: ${local.x}")
        assertEquals(5000f, assertNotNull(absolute).x, 0.001f)
    }

    private fun World.hasRock(name: String): Boolean {
        var found = false
        queryEach(Name::class) { _, value -> if (value.value == name) found = true }
        return found
    }
}
