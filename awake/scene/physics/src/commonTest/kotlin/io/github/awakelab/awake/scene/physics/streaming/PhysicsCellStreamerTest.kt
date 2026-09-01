/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics.streaming

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.CollisionLayer
import io.github.awakelab.awake.physics.CollisionLayers
import io.github.awakelab.awake.physics.ContactEvent
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsShape
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.RaycastHit
import io.github.awakelab.awake.physics.ShapeCastHit
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.physics.PhysicsBody
import io.github.awakelab.awake.scene.physics.PhysicsSystem
import io.github.awakelab.awake.scene.world.WorldCellCoord
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That a streamed collider arrives with its cell and leaves with it.
 *
 * The leak is the thing worth testing: a body outliving its cell is invisible, still solid, and
 * accumulates until the backend's body cap ends the session. It cannot be seen in a screenshot and
 * it does not throw, so it has to be asserted.
 */
class PhysicsCellStreamerTest {

    private val cellShape = BoxShape(Vec3f(8f, 1f, 8f))

    private fun streamer(
        physics: PhysicsWorld,
        shapeFor: suspend (WorldCellCoord) -> PhysicsShape? = { cellShape },
    ) = PhysicsCellStreamer(physics, cellSize = 16f, shapeFor = shapeFor)

    /** Loads a cell the way `WorldPartitionSystem` does: suspend first, apply on the frame thread. */
    private suspend fun PhysicsCellStreamer.load(world: World, coord: WorldCellCoord) {
        loadCell(coord).applyTo(world)
    }

    @Test
    fun aLoadedCellGetsACollider() = runTest {
        val physics = RecordingPhysicsWorld()
        val world = World()
        val system = PhysicsSystem(physics)
        val streamer = streamer(physics)

        streamer.load(world, WorldCellCoord(1, 2))
        system.update(world, 1f / 60f)

        assertEquals(1, physics.created.size)
        assertEquals(setOf(WorldCellCoord(1, 2)), streamer.residentCells)
    }

    @Test
    fun theColliderSitsAtTheCellsCentre() = runTest {
        val physics = RecordingPhysicsWorld()
        val world = World()
        val streamer = streamer(physics)

        streamer.load(world, WorldCellCoord(1, 2))
        PhysicsSystem(physics).update(world, 1f / 60f)

        // Centre, not corner -- the same convention MeshCellStreamer uses. Half a cell out here
        // puts every streamed collider half a cell from the terrain it belongs to.
        val position = physics.positions.single()
        assertTrue(abs(position.x - 24f) < 1e-4f, "x=${position.x}, expected 16 + 8")
        assertTrue(abs(position.z - 40f) < 1e-4f, "z=${position.z}, expected 32 + 8")
    }

    @Test
    fun unloadingACellDestroysItsBody() = runTest {
        val physics = RecordingPhysicsWorld()
        val world = World()
        val system = PhysicsSystem(physics)
        val streamer = streamer(physics)
        streamer.load(world, WorldCellCoord(0, 0))
        system.update(world, 1f / 60f)

        streamer.onCellUnload(world, WorldCellCoord(0, 0))

        // The leak this exists to prevent: a body that outlives its cell is invisible, still
        // solid, and never removable again.
        assertTrue(physics.liveBodies.isEmpty(), "live bodies: ${physics.liveBodies}")
        assertTrue(streamer.residentCells.isEmpty())
    }

    @Test
    fun unloadingACellRemovesItsEntityToo() = runTest {
        val physics = RecordingPhysicsWorld()
        val world = World()
        val streamer = streamer(physics)
        streamer.load(world, WorldCellCoord(0, 0))
        PhysicsSystem(physics).update(world, 1f / 60f)

        streamer.onCellUnload(world, WorldCellCoord(0, 0))

        var remaining = 0
        world.queryEach(Transform::class, PhysicsBody::class) { _, _, _ -> remaining++ }
        assertEquals(0, remaining, "the entity outlived its cell")
    }

    @Test
    fun aCellWithNothingToCollideWithSpawnsNothing() = runTest {
        val physics = RecordingPhysicsWorld()
        val world = World()
        val streamer = streamer(physics) { null }

        streamer.load(world, WorldCellCoord(0, 0))
        PhysicsSystem(physics).update(world, 1f / 60f)

        // Ocean, void, an unauthored region: an ordinary answer, not an error.
        assertTrue(physics.created.isEmpty())
        assertTrue(streamer.residentCells.isEmpty())
        // And unloading a cell that never loaded must not throw.
        streamer.onCellUnload(world, WorldCellCoord(0, 0))
    }

    @Test
    fun loadingTheSameCellTwiceLeavesOneCollider() = runTest {
        val physics = RecordingPhysicsWorld()
        val world = World()
        val system = PhysicsSystem(physics)
        val streamer = streamer(physics)

        streamer.load(world, WorldCellCoord(3, 3))
        system.update(world, 1f / 60f)
        streamer.load(world, WorldCellCoord(3, 3))
        system.update(world, 1f / 60f)

        // The first body has to go, or it is left in the world with nothing holding its handle.
        assertEquals(1, physics.liveBodies.size, "live bodies: ${physics.liveBodies}")
    }

    @Test
    fun disposeTakesBackEverythingStreamed() = runTest {
        val physics = RecordingPhysicsWorld()
        val world = World()
        val system = PhysicsSystem(physics)
        val streamer = streamer(physics)
        repeat(4) { index -> streamer.load(world, WorldCellCoord(index, 0)) }
        system.update(world, 1f / 60f)
        assertEquals(4, physics.liveBodies.size)

        streamer.dispose(world)

        assertTrue(physics.liveBodies.isEmpty(), "teardown left bodies behind: ${physics.liveBodies}")
    }

    @Test
    fun streamedCollidersLandInTheWorldLayerByDefault() = runTest {
        val physics = RecordingPhysicsWorld()
        val world = World()
        val streamer = streamer(physics)

        streamer.load(world, WorldCellCoord(0, 0))
        PhysicsSystem(physics).update(world, 1f / 60f)

        // Streamed cells are the level. A camera sweeping the world layer must find them, and one
        // ignoring props must not lose them.
        assertEquals(CollisionLayers.World, assertNotNull(physics.layersUsed.singleOrNull()))
    }
}
