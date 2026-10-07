/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.DegreesOfFreedom
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PhysicsPlane2DSceneTest {
    private val registry = SceneComponentRegistry().registerPhysics()

    @Test
    fun anAuthoredPlanarBallLandsOnAFloorAndSurvivesAMotionTypeRebuild() = runTest {
        val world = World()
        SceneLoader.decode(SCENE).instantiate(world = world, componentRegistry = registry)
        val physicsWorld = createJoltPhysicsWorld(gravity = Vec3f(0f, -9.81f, 3f))
        try {
            val physics = PhysicsSystem(physicsWorld)
            physics.update(world, STEP)
            var ball: PhysicsBody? = null
            var transform: Transform? = null
            world.queryEach(Name::class) { entity, name ->
                if (name.value == "Ball") {
                    ball = world.get<PhysicsBody>(entity)
                    transform = world.get<Transform>(entity)
                }
            }
            val body = checkNotNull(ball)
            val pose = checkNotNull(transform)
            assertEquals(DegreesOfFreedom.PLANE_2D, body.degreesOfFreedom)
            physicsWorld.setLinearVelocity(checkNotNull(body.handle), Vec3f(0f, 0f, 10f))
            repeat(180) { physics.update(world, STEP) }
            assertEquals(0.6f, pose.position.y, 0.05f, "the ball must rest on the authored floor")
            assertEquals(2f, pose.position.z, EPSILON)

            val originalHandle = body.handle
            body.motionType = MotionType.KINEMATIC
            physics.update(world, STEP)
            assertNotEquals(originalHandle, body.handle)
            physicsWorld.setLinearVelocity(checkNotNull(body.handle), Vec3f(1f, 0f, 10f))
            repeat(30) { physics.update(world, STEP) }
            assertTrue(pose.position.x > 0.4f)
            assertEquals(2f, pose.position.z, EPSILON, "the rebuilt body must keep the restriction")

            val saved = SceneLoader.fromWorld(world, componentRegistry = registry)
            val component = saved.nodes.single { it.name == "Ball" }.components.filterIsInstance<ScenePhysicsBody>().single()
            assertEquals(DegreesOfFreedom.PLANE_2D, component.degreesOfFreedom)
            assertEquals(MotionType.KINEMATIC, component.motion)
            assertEquals(saved, SceneLoader.decode(SceneLoader.encode(saved)))
        } finally {
            physicsWorld.destroy()
        }
    }

    @Test
    fun allShapePathsPreserveTheOptionBeforeAndAfterBuilding() {
        val shapes = listOf(
            SceneBoxShape(), SceneSphereShape(), SceneCapsuleShape(),
            SceneMeshShape("models/body.glb"), SceneConvexHullShape("models/body.glb"),
        )
        for (shape in shapes) {
            val component = ScenePhysicsBody(
                shape = shape,
                motion = if (shape is SceneMeshShape) MotionType.STATIC else MotionType.DYNAMIC,
                degreesOfFreedom = DegreesOfFreedom.PLANE_2D,
            )
            val document = SceneDocument(nodes = listOf(SceneNode(name = "Body", components = listOf(component))))
            val world = World()
            SceneLoader.decode(SceneLoader.encode(document)).instantiate(world = world, componentRegistry = registry)
            assertEquals(component, SceneLoader.fromWorld(world, componentRegistry = registry).nodes.single().components.single())

            MeshColliderSystem { _, _ -> TETRAHEDRON }.update(world, STEP)
            world.queryEach(PhysicsBody::class) { _, body -> assertEquals(DegreesOfFreedom.PLANE_2D, body.degreesOfFreedom) }
            assertEquals(component, SceneLoader.fromWorld(world, componentRegistry = registry).nodes.single().components.single())
        }
    }

    @Test
    fun aSceneWithoutTheOptionKeepsAllDegreesOfFreedom() {
        val component = SceneLoader.decode(SCENE.replace(", \"degreesOfFreedom\": \"PLANE_2D\"", ""))
            .nodes.single { it.name == "Ball" }.components.filterIsInstance<ScenePhysicsBody>().single()
        assertEquals(DegreesOfFreedom.ALL, component.degreesOfFreedom)
        assertEquals(DegreesOfFreedom.ALL, PhysicsBody(BoxShape(Vec3f(1f, 1f, 1f)), MotionType.DYNAMIC).degreesOfFreedom)
    }

    private companion object {
        const val STEP = 1f / 60f
        const val EPSILON = 1e-4f
        val TETRAHEDRON = MeshShape(
            floatArrayOf(-0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0f, 0.5f),
            intArrayOf(0, 2, 1, 0, 1, 3, 1, 2, 3, 2, 0, 3),
        )
        const val SCENE = """
{ "version": 1, "name": "Planar physics", "nodes": [
  { "name": "Floor", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 2.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 5.0, "y": 0.1, "z": 0.5 } } }
  ] },
  { "name": "Ball", "transform": { "position": { "x": 0.0, "y": 3.0, "z": 2.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "sphere", "radius": 0.5 }, "motion": "DYNAMIC", "degreesOfFreedom": "PLANE_2D" }
  ] }
] }
"""
    }
}
