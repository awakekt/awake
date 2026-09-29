/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneVec3
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhysicsBodySceneTest {
    private val registry = SceneComponentRegistry().registerPhysics()

    @Test
    fun authoredBodiesRoundTrip() {
        val world = World()

        SceneLoader.decode(SCENE).instantiate(world = world, componentRegistry = registry)

        val exported = SceneLoader.fromWorld(world, name = "x", componentRegistry = registry)
            .nodes.associate { node -> node.name to node.components.filterIsInstance<ScenePhysicsBody>().single() }
        assertEquals(ScenePhysicsBody(shape = SceneBoxShape(SceneVec3(5f, 0.1f, 5f))), exported["Floor"])
        assertEquals(ScenePhysicsBody(shape = SceneSphereShape(0.5f), motion = MotionType.DYNAMIC), exported["Ball"])
    }

    @Test
    fun aDynamicBallFallsOntoAnAuthoredFloorAndRests() = runTest {
        val world = World()
        SceneLoader.decode(SCENE).instantiate(world = world, componentRegistry = registry)
        val physics = PhysicsSystem(createJoltPhysicsWorld())

        repeat(STEPS) { physics.update(world, 1f / 60f) }

        val ball = world.get<Transform>(world.named("Ball"))!!.position
        // Floor top at 0.1, ball radius 0.5.
        assertEquals(0.6f, ball.y, 0.05f, "the ball must rest on the floor, not fall through it")
    }

    @Test
    fun aBodyAShapeCantDescribeIsLeftOutOfTheSave() {
        val body = PhysicsBody(HeightFieldShape(FloatArray(16), sampleCount = 4, scale = Vec3f(1f, 1f, 1f)), MotionType.STATIC)

        assertNull(PhysicsBodyBinding.export(World(), World().create(), body))
    }

    @Test
    fun aZeroSizedShapeIsReported() {
        val issues = ScenePhysicsBody(shape = SceneSphereShape(0f)).validate("nodes[0]")
        assertTrue(issues.isNotEmpty())
    }

    private fun World.named(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return found!!
    }

    private companion object {
        const val STEPS = 180
        const val SCENE = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "Floor", "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 5.0, "y": 0.1, "z": 5.0 } } }
  ] },
  { "name": "Ball", "transform": { "position": { "x": 0.0, "y": 3.0, "z": 0.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "sphere", "radius": 0.5 }, "motion": "DYNAMIC" }
  ] }
] }
"""
    }
}
