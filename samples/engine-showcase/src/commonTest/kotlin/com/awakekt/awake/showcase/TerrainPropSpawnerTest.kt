/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.ConvexHullShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.showcase.examples.PropShapeKind
import com.awakekt.awake.showcase.examples.TerrainPhysicsExampleDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TerrainPropSpawnerTest {

    private val showcase = EngineShowcases.single { it.id == "heightfield-terrain" }
    private lateinit var world: World

    @BeforeTest
    fun setup() {
        world = World()
        TerrainPhysicsExampleDriver.detach(world)
    }

    @AfterTest
    fun tearDown() {
        TerrainPhysicsExampleDriver.detach(world)
    }

    @Test
    fun theShowcaseDefinesControls() {
        assertNotNull(showcase.controls, "heightfield-terrain must define controls panel")
    }

    @Test
    fun wedgeGeometryHasValidFormatAndTopology() {
        val geometry = TerrainPhysicsExampleDriver.wedgeGeometry
        assertTrue(geometry.vertices.isNotEmpty(), "wedge vertices must not be empty")
        assertEquals(24, geometry.indices.size, "wedge has 8 triangles (24 indices)")
        assertEquals(VertexFormat.PositionNormalColor, geometry.format)
    }

    @Test
    fun spawnPropAddsDynamicEntitiesForEveryShapeKind() {
        val box = TerrainPhysicsExampleDriver.spawnProp(world, PropShapeKind.Box, position = Vec3f(0f, 5f, 0f))
        val sphere = TerrainPhysicsExampleDriver.spawnProp(world, PropShapeKind.Sphere, position = Vec3f(1f, 5f, 0f))
        val wedge = TerrainPhysicsExampleDriver.spawnProp(world, PropShapeKind.Wedge, position = Vec3f(2f, 5f, 0f))

        assertEquals(3, TerrainPhysicsExampleDriver.spawnedCount)

        val boxBody = assertNotNull(world.get<PhysicsBody>(box))
        val sphereBody = assertNotNull(world.get<PhysicsBody>(sphere))
        val wedgeBody = assertNotNull(world.get<PhysicsBody>(wedge))

        assertTrue(boxBody.shape is BoxShape, "box prop has BoxShape")
        assertTrue(sphereBody.shape is SphereShape, "sphere prop has SphereShape")
        assertTrue(wedgeBody.shape is ConvexHullShape, "wedge prop has ConvexHullShape")

        assertEquals(MotionType.DYNAMIC, boxBody.motionType)
        assertEquals(MotionType.DYNAMIC, sphereBody.motionType)
        assertEquals(MotionType.DYNAMIC, wedgeBody.motionType)

        assertNotNull(world.get<Transform>(box))
        assertNotNull(world.get<Transform>(sphere))
        assertNotNull(world.get<Transform>(wedge))
    }

    @Test
    fun spawnPropWithImpulsesQueuesPendingImpulse() {
        TerrainPhysicsExampleDriver.applyImpulsesOnSpawn = true
        val entity = TerrainPhysicsExampleDriver.spawnProp(world, PropShapeKind.Box)
        assertEquals(1, TerrainPhysicsExampleDriver.spawnedCount)
        assertTrue(world.isAlive(entity))
        TerrainPhysicsExampleDriver.applyImpulsesOnSpawn = false
    }

    @Test
    fun resetPropsClearsSpawnedEntitiesAndResetsCount() {
        val box = TerrainPhysicsExampleDriver.spawnProp(world, PropShapeKind.Box)
        val sphere = TerrainPhysicsExampleDriver.spawnProp(world, PropShapeKind.Sphere)

        assertEquals(2, TerrainPhysicsExampleDriver.spawnedCount)
        assertTrue(world.isAlive(box))
        assertTrue(world.isAlive(sphere))

        TerrainPhysicsExampleDriver.resetProps(world)

        assertEquals(0, TerrainPhysicsExampleDriver.spawnedCount)
        assertFalse(world.isAlive(box), "spawned box entity must be destroyed on reset")
        assertFalse(world.isAlive(sphere), "spawned sphere entity must be destroyed on reset")
    }

    @Test
    fun cameraModeToggleUpdatesCameraRig() {
        val cameraEntity = world.create()
        val rig = CameraRig().apply { mode = CameraMode.ThirdPerson }
        world.add(cameraEntity, rig)
        world.add(cameraEntity, ActiveCamera())

        assertEquals(CameraMode.ThirdPerson, TerrainPhysicsExampleDriver.cameraMode)

        TerrainPhysicsExampleDriver.setCameraMode(world, CameraMode.FreeFly)
        assertEquals(CameraMode.FreeFly, TerrainPhysicsExampleDriver.cameraMode)
        assertEquals(CameraMode.FreeFly, rig.mode)

        TerrainPhysicsExampleDriver.setCameraMode(world, CameraMode.ThirdPerson)
        assertEquals(CameraMode.ThirdPerson, TerrainPhysicsExampleDriver.cameraMode)
        assertEquals(CameraMode.ThirdPerson, rig.mode)
    }

    @Test
    fun detachCleansUpSpawnedEntities() {
        val box = TerrainPhysicsExampleDriver.spawnProp(world, PropShapeKind.Box)
        assertEquals(1, TerrainPhysicsExampleDriver.spawnedCount)

        TerrainPhysicsExampleDriver.detach(world)
        assertEquals(0, TerrainPhysicsExampleDriver.spawnedCount)
        assertFalse(world.isAlive(box), "spawned entity must be destroyed on detach")
    }
}
