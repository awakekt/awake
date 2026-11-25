/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.DegreesOfFreedom
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.CollisionMeshSource
import com.awakekt.awake.scene.physics.MeshCollider
import com.awakekt.awake.scene.physics.MeshColliderSystem
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StaticColliderStreamSystemTest {

    @Test
    fun parksFarTerrainsAndMeshesOnTickZeroBeforeColliderSystemsBuildThem() = runTest {
        val world = World()
        val physicsWorld = CountingPhysicsWorld(createJoltPhysicsWorld())
        val physicsSystem = PhysicsSystem(physicsWorld)
        val terrainColliderSystem = TerrainColliderSystem()
        val meshColliderSystem = MeshColliderSystem(flatPlatformMeshes())
        val activated = mutableListOf<Entity>()
        val config = ColliderStreamConfig(terrainReach = 100f, terrainLeave = 150f, meshReach = 100f, meshLeave = 150f, moveStep = 10f)
        val streamSystem = StaticColliderStreamSystem(physics = physicsSystem, config = config, onMeshActivated = { activated += it })

        try {
            spawnPrimaryCamera(world, x = 0f, y = 10f, z = 0f)
            val nearHeightmap = flatHeightmap(height = 2f)
            val farHeightmap = flatHeightmap(height = 5f)
            val nearTerrain = spawnTerrain(world, x = 0f, z = 0f, heightmap = nearHeightmap, collider = true)
            val farTerrain = spawnTerrain(world, x = 1000f, z = 0f, heightmap = farHeightmap, collider = true)
            val decorativeTerrain = spawnTerrain(world, x = 0f, z = 20f, heightmap = flatHeightmap(height = 1f), collider = false)
            val nearMesh = spawnMesh(world, x = 20f, z = 30f)
            val farMesh = spawnMesh(world, x = 1000f, z = 30f)

            stepAll(world, streamSystem, terrainColliderSystem, meshColliderSystem, physicsSystem)

            assertEquals(2, physicsWorld.createdBodies, "only near terrain and near mesh should build bodies on tick 0")
            assertEquals(0, physicsWorld.destroyedBodies)
            assertEquals(2, streamSystem.trackedTerrainCount, "non-collider terrain must not be tracked")
            assertEquals(1, streamSystem.parkedTerrainCount)
            assertEquals(2, streamSystem.trackedMeshCount)
            assertEquals(1, streamSystem.parkedMeshCount)
            assertTrue(activated.isEmpty(), "onMeshActivated fires only when previously parked meshes restore")

            assertActivePair(world, physicsWorld, nearTerrain, nearMesh, terrainX = 0f, meshX = 20f)
            assertParkedPair(world, physicsWorld, farTerrain, farHeightmap, farMesh, x = 1000f)
            assertEquals(5f, world.get<TerrainComponent>(farTerrain)!!.heightmap.heightAtWorld(0f, 0f), 0.01f)
            assertFalse(world.get<TerrainComponent>(decorativeTerrain)!!.collider)
            assertNull(world.get<PhysicsBody>(decorativeTerrain))
        } finally {
            streamSystem.close()
            physicsWorld.destroy()
        }
    }

    @Test
    fun destroysBodiesOnLeaveRestoresOnApproachAndNotifiesOnMeshActivated() = runTest {
        val world = World()
        val physicsWorld = CountingPhysicsWorld(createJoltPhysicsWorld())
        val physicsSystem = PhysicsSystem(physicsWorld)
        val terrainColliderSystem = TerrainColliderSystem()
        val meshColliderSystem = MeshColliderSystem(flatPlatformMeshes())
        val activated = mutableListOf<Entity>()
        val observerPos = Vec3f(0f, 10f, 0f)
        val config = ColliderStreamConfig(
            terrainReach = 100f,
            terrainLeave = 150f,
            meshReach = 80f,
            meshLeave = 120f,
            moveStep = 10f,
        )
        val streamSystem = StaticColliderStreamSystem(
            physics = physicsSystem,
            config = config,
            observer = { observerPos },
            onMeshActivated = { activated += it },
        )

        try {
            val nearHeightmap = flatHeightmap(height = 2f)
            val farHeightmap = flatHeightmap(height = 4f)
            val nearTerrain = spawnTerrain(world, x = 0f, z = 0f, heightmap = nearHeightmap, collider = true)
            val farTerrain = spawnTerrain(world, x = 500f, z = 0f, heightmap = farHeightmap, collider = true)
            val nearMesh = spawnMesh(world, x = 0f, z = 30f)
            val farMesh = spawnMesh(world, x = 500f, z = 30f)

            // Tick 0 at (0, 10, 0): near active, far parked.
            stepAll(world, streamSystem, terrainColliderSystem, meshColliderSystem, physicsSystem)
            assertEquals(2, physicsWorld.createdBodies)
            assertEquals(0, physicsWorld.destroyedBodies)

            // Move into hysteresis band: distance to nearMesh is ~114m (80 < 114 <= 120),
            // distance to nearTerrain edge is 102m (100 < 102 <= 150). Active colliders must stay live.
            observerPos.set(110f, 10f, 0f)
            stepAll(world, streamSystem, terrainColliderSystem, meshColliderSystem, physicsSystem)
            assertEquals(0, physicsWorld.destroyedBodies, "hysteresis band must keep active bodies live")
            assertNotNull(world.get<PhysicsBody>(nearTerrain)?.handle)
            assertNotNull(world.get<PhysicsBody>(nearMesh)?.handle)
            assertTrue(activated.isEmpty())

            // Move to (500, 10, 0): near leaves range and destroys bodies; far enters reach and activates.
            observerPos.set(500f, 10f, 0f)
            stepAll(world, streamSystem, terrainColliderSystem, meshColliderSystem, physicsSystem)
            assertEquals(2, physicsWorld.destroyedBodies, "leaving near terrain and mesh must destroy both bodies")
            assertEquals(4, physicsWorld.createdBodies, "approaching far terrain and mesh must build both bodies")
            assertParkedPair(world, physicsWorld, nearTerrain, nearHeightmap, nearMesh, x = 0f)
            assertActivePair(world, physicsWorld, farTerrain, farMesh, terrainX = 500f)
            assertEquals(listOf(farMesh), activated, "onMeshActivated must report the restored far mesh")

            // Return to (0, 10, 0): far destroys, near restores.
            activated.clear()
            observerPos.set(0f, 10f, 0f)
            stepAll(world, streamSystem, terrainColliderSystem, meshColliderSystem, physicsSystem)
            assertEquals(4, physicsWorld.destroyedBodies)
            assertEquals(6, physicsWorld.createdBodies)
            assertActivePair(world, physicsWorld, nearTerrain, nearMesh, terrainX = 0f)
            assertParkedPair(world, physicsWorld, farTerrain, farHeightmap, farMesh, x = 500f)
            assertEquals(listOf(nearMesh), activated, "onMeshActivated must report the restored near mesh")
        } finally {
            streamSystem.close()
            physicsWorld.destroy()
        }
    }

    private fun assertParkedPair(
        world: World,
        physicsWorld: PhysicsWorld,
        terrain: Entity,
        expectedHeightmap: Heightmap,
        mesh: Entity,
        x: Float,
    ) {
        assertNull(world.get<PhysicsBody>(terrain))
        assertFalse(world.get<TerrainComponent>(terrain)!!.collider)
        assertSame(expectedHeightmap, world.get<TerrainComponent>(terrain)!!.heightmap)
        assertNull(physicsWorld.raycastDown(x, 0f))
        assertNull(world.get<MeshCollider>(mesh))
        assertNull(world.get<PhysicsBody>(mesh))
        assertNull(physicsWorld.raycastDown(x, 30f))
    }

    private fun assertActivePair(
        world: World,
        physicsWorld: PhysicsWorld,
        terrain: Entity,
        mesh: Entity,
        terrainX: Float,
        meshX: Float = terrainX,
    ) {
        assertTrue(world.get<TerrainComponent>(terrain)!!.collider)
        assertNotNull(world.get<PhysicsBody>(terrain)?.handle)
        assertNotNull(physicsWorld.raycastDown(terrainX, 0f))
        assertNotNull(world.get<MeshCollider>(mesh))
        assertNotNull(world.get<PhysicsBody>(mesh)?.handle)
        assertNotNull(physicsWorld.raycastDown(meshX, 30f))
    }

    private fun spawnPrimaryCamera(world: World, x: Float, y: Float, z: Float) {
        world.create().also { entity ->
            world.add(
                entity,
                Camera(
                    lens = Lens(
                        eye = Vec3f(x, y, z),
                        center = Vec3f(x, 0f, z),
                        up = Vec3f(0f, 1f, 0f),
                        fovYRadians = 1f,
                        near = 0.1f,
                        far = 1000f,
                    ),
                    isPrimary = true,
                ),
            )
        }
    }

    @Test
    fun respectsMoveStepAndParksNewlySpawnedFarCollidersImmediately() = runTest {
        val world = World()
        val physicsWorld = CountingPhysicsWorld(createJoltPhysicsWorld())
        val physicsSystem = PhysicsSystem(physicsWorld)
        val terrainColliderSystem = TerrainColliderSystem()
        val meshColliderSystem = MeshColliderSystem(flatPlatformMeshes())
        val activated = mutableListOf<Entity>()
        val observerPos = Vec3f(0f, 0f, 0f)
        val streamSystem = StaticColliderStreamSystem(
            physics = physicsSystem,
            config = ColliderStreamConfig(
                terrainReach = 100f,
                terrainLeave = 150f,
                meshReach = 100f,
                meshLeave = 150f,
                moveStep = 50f,
            ),
            observer = { observerPos },
            onMeshActivated = { activated += it },
        )

        try {
            val meshA = spawnMesh(world, x = 110f, z = 0f)
            stepAll(world, streamSystem, terrainColliderSystem, meshColliderSystem, physicsSystem)
            assertEquals(0, physicsWorld.createdBodies)
            assertEquals(1, streamSystem.parkedMeshCount)

            // Move only 20m (< moveStep 50m): meshA stays parked even though distance is now 90m <= 100m.
            observerPos.set(20f, 0f, 0f)
            // Spawn a brand-new far mesh while observer has not exceeded moveStep.
            val meshB = spawnMesh(world, x = 500f, z = 0f)
            stepAll(world, streamSystem, terrainColliderSystem, meshColliderSystem, physicsSystem)

            assertEquals(0, physicsWorld.createdBodies, "newly spawned far mesh must be parked immediately before building")
            assertNull(world.get<MeshCollider>(meshB))
            assertNull(world.get<MeshCollider>(meshA), "existing parked mesh waits for moveStep")
            assertEquals(2, streamSystem.parkedMeshCount)
            assertTrue(activated.isEmpty())

            // Exceed moveStep (60m >= 50m from last evaluation at x = 0): meshA now activates, meshB stays parked.
            observerPos.set(60f, 0f, 0f)
            stepAll(world, streamSystem, terrainColliderSystem, meshColliderSystem, physicsSystem)

            assertEquals(1, physicsWorld.createdBodies)
            assertEquals(1, streamSystem.parkedMeshCount)
            assertNotNull(world.get<MeshCollider>(meshA))
            assertNull(world.get<MeshCollider>(meshB))
            assertEquals(listOf(meshA), activated)
        } finally {
            streamSystem.close()
            physicsWorld.destroy()
        }
    }

    @Test
    fun measuresTerrainDistanceFromHeightmapFootprintRatherThanCenter() = runTest {
        val world = World()
        val physicsWorld = CountingPhysicsWorld(createJoltPhysicsWorld())
        val physicsSystem = PhysicsSystem(physicsWorld)
        val terrainColliderSystem = TerrainColliderSystem()
        val meshColliderSystem = MeshColliderSystem(flatPlatformMeshes())
        val observerPos = Vec3f(240f, 5f, 0f)
        // Terrain spans [-256, +256] along X and Z: observer at x = 240 is standing ON it (edge distance 0).
        val largeHeightmap = Heightmap(
            samples = FloatArray(17 * 17) { 0f },
            width = 17,
            depth = 17,
            scale = Vec3f(32f, 1f, 32f),
        )
        val streamSystem = StaticColliderStreamSystem(
            physics = physicsSystem,
            config = ColliderStreamConfig(
                terrainReach = 50f,
                terrainLeave = 60f,
                meshReach = 50f,
                meshLeave = 60f,
                moveStep = 0f,
            ),
            observer = { observerPos },
        )

        try {
            val terrain = spawnTerrain(world, x = 0f, z = 0f, heightmap = largeHeightmap, collider = true)
            stepAll(world, streamSystem, terrainColliderSystem, meshColliderSystem, physicsSystem)

            assertTrue(world.get<TerrainComponent>(terrain)!!.collider, "observer standing on terrain edge must keep collider active")
            assertEquals(0, streamSystem.parkedTerrainCount)
            assertEquals(1, physicsWorld.createdBodies)

            // Move 74m outside eastern edge (256 + 74 = 330 > leave 60).
            observerPos.set(330f, 5f, 0f)
            stepAll(world, streamSystem, terrainColliderSystem, meshColliderSystem, physicsSystem)

            assertFalse(world.get<TerrainComponent>(terrain)!!.collider)
            assertSame(largeHeightmap, world.get<TerrainComponent>(terrain)!!.heightmap)
            assertEquals(1, streamSystem.parkedTerrainCount)
            assertEquals(1, physicsWorld.destroyedBodies)
        } finally {
            streamSystem.close()
            physicsWorld.destroy()
        }
    }

    @Test
    fun prunesDestroyedEntitiesAndCloseClearsAllInternalState() = runTest {
        val world = World()
        val physicsWorld = CountingPhysicsWorld(createJoltPhysicsWorld())
        val physicsSystem = PhysicsSystem(physicsWorld)
        val terrainColliderSystem = TerrainColliderSystem()
        val meshColliderSystem = MeshColliderSystem(flatPlatformMeshes())
        val observerPos = Vec3f(0f, 0f, 0f)
        val streamSystem = StaticColliderStreamSystem(
            physics = physicsSystem,
            config = ColliderStreamConfig(
                terrainReach = 50f,
                terrainLeave = 80f,
                meshReach = 50f,
                meshLeave = 80f,
                moveStep = 10f,
            ),
            observer = { observerPos },
        )

        try {
            spawnTerrain(world, x = 0f, z = 0f, heightmap = flatHeightmap(0f), collider = true)
            spawnTerrain(world, x = 500f, z = 0f, heightmap = flatHeightmap(0f), collider = true)
            val nearMesh = spawnMesh(world, x = 0f, z = 0f)
            spawnMesh(world, x = 500f, z = 0f)

            stepAll(world, streamSystem, terrainColliderSystem, meshColliderSystem, physicsSystem)
            assertEquals(2, streamSystem.trackedTerrainCount)
            assertEquals(1, streamSystem.parkedTerrainCount)
            assertEquals(2, streamSystem.trackedMeshCount)
            assertEquals(1, streamSystem.parkedMeshCount)

            physicsSystem.destroyBody(world, nearMesh)
            world.destroy(nearMesh)
            stepAll(world, streamSystem, terrainColliderSystem, meshColliderSystem, physicsSystem)
            assertEquals(1, streamSystem.trackedMeshCount, "destroyed mesh entity must be pruned from tracking")
            assertEquals(1, streamSystem.parkedMeshCount)

            streamSystem.close()
            assertEquals(0, streamSystem.trackedTerrainCount)
            assertEquals(0, streamSystem.parkedTerrainCount)
            assertEquals(0, streamSystem.trackedMeshCount)
            assertEquals(0, streamSystem.parkedMeshCount)

            // Closing twice and stepping after close are safe no-ops.
            streamSystem.close()
            observerPos.set(500f, 0f, 0f)
            streamSystem.update(world, STEP)
            assertEquals(0, streamSystem.trackedTerrainCount)
            assertEquals(0, streamSystem.trackedMeshCount)
        } finally {
            streamSystem.close()
            physicsWorld.destroy()
        }
    }

    @Test
    fun rejectsInvalidColliderStreamConfig() {
        assertFailsWith<IllegalArgumentException> { ColliderStreamConfig(terrainReach = -1f) }
        assertFailsWith<IllegalArgumentException> { ColliderStreamConfig(terrainReach = 100f, terrainLeave = 50f) }
        assertFailsWith<IllegalArgumentException> { ColliderStreamConfig(meshReach = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { ColliderStreamConfig(meshReach = 100f, meshLeave = 99f) }
        assertFailsWith<IllegalArgumentException> { ColliderStreamConfig(moveStep = -5f) }
    }

    private fun stepAll(
        world: World,
        stream: StaticColliderStreamSystem,
        terrainCollider: TerrainColliderSystem,
        meshCollider: MeshColliderSystem,
        physics: PhysicsSystem,
    ) {
        stream.update(world, STEP)
        terrainCollider.update(world, STEP)
        meshCollider.update(world, STEP)
        physics.update(world, STEP)
    }

    private fun spawnTerrain(
        world: World,
        x: Float,
        z: Float,
        heightmap: Heightmap,
        collider: Boolean,
    ): Entity = world.create().also { entity ->
        world.add(entity, Transform(position = Vec3f(x, 0f, z)))
        world.add(entity, TerrainComponent(heightmap = heightmap, collider = collider))
    }

    private fun spawnMesh(world: World, x: Float, z: Float): Entity = world.create().also { entity ->
        world.add(entity, Transform(position = Vec3f(x, 0f, z)))
        world.add(entity, MeshCollider(mesh = PLATFORM_MESH))
    }

    private fun flatHeightmap(height: Float): Heightmap = Heightmap(
        samples = FloatArray(TERRAIN_SIDE * TERRAIN_SIDE) { height },
        width = TERRAIN_SIDE,
        depth = TERRAIN_SIDE,
        scale = Vec3f(4f, 1f, 4f),
    )

    private fun flatPlatformMeshes(): CollisionMeshSource {
        val vertices = floatArrayOf(
            -4f, 0f, -4f,
            4f, 0f, -4f,
            4f, 0f, 4f,
            -4f, 0f, 4f,
        )
        val indices = intArrayOf(3, 2, 1, 3, 1, 0)
        val shape = MeshShape(vertices, indices)
        return CollisionMeshSource { mesh, _ -> if (mesh == PLATFORM_MESH) shape else null }
    }

    private fun PhysicsWorld.raycastDown(x: Float, z: Float) =
        raycast(Vec3f(x, RAY_HEIGHT, z), Vec3f(0f, -1f, 0f), RAY_HEIGHT * 2f)

    private class CountingPhysicsWorld(private val inner: PhysicsWorld) : PhysicsWorld by inner {
        var createdBodies = 0
        var destroyedBodies = 0

        override fun createBody(
            shape: PhysicsShape,
            position: Vec3f,
            rotation: com.awakekt.awake.core.math.Quat,
            motionType: MotionType,
            layer: CollisionLayer,
            sensor: Boolean,
            degreesOfFreedom: DegreesOfFreedom,
        ): BodyHandle {
            createdBodies++
            return inner.createBody(shape, position, rotation, motionType, layer, sensor, degreesOfFreedom)
        }

        override fun destroyBody(handle: BodyHandle) {
            destroyedBodies++
            inner.destroyBody(handle)
        }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val TERRAIN_SIDE = 5
        const val RAY_HEIGHT = 25f
        const val PLATFORM_MESH = "props/platform.glb"
    }
}
