/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.motion.GroundContact
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.physics.registerPhysics
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterControllerTest {
    private val registry = SceneComponentRegistry().registerControls().registerPhysics().registerCharacter()

    @Test
    fun theCharacterFallsOntoTheFloorAndStands() = runTest {
        val arena = arena(jumpSpeed = 5f)

        arena.run(STEPS)

        // Floor top at 0, capsule centre half its height (0.5 + 0.5) above it.
        assertEquals(1f, arena.position.y, GROUND_TOLERANCE, "the character must stand on the floor")
        assertTrue(arena.character.isGrounded)
    }

    @Test
    fun walkingForwardStopsAtTheWall() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)

        arena.intent.moveZ = 1f
        arena.run(STEPS * 2)

        // Wall face at z = -2.5; the capsule stops a radius short of it.
        assertTrue(arena.position.z < -1f, "forward (-Z) must walk the character; z = ${arena.position.z}")
        assertTrue(arena.position.z > -2.5f + 0.4f, "the wall must stop the character; z = ${arena.position.z}")
    }

    @Test
    fun jumpingRisesAndLandsBackOnTheFloor() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)
        val ground = arena.position.y

        assertTrue(arena.ground.grounded, "standing must be on the ground")

        arena.intent.jump = true
        arena.run(1)
        arena.intent.jump = false
        assertFalse(arena.ground.grounded, "taking off must leave the ground")
        var peak = ground
        var airborneSteps = 0
        repeat(STEPS) {
            arena.run(1)
            peak = maxOf(peak, arena.position.y)
            if (!arena.ground.grounded) airborneSteps++
        }

        assertTrue(peak > ground + 0.8f, "a 5 m/s jump must rise about 1.3 m; peak ${peak - ground}")
        assertEquals(ground, arena.position.y, GROUND_TOLERANCE, "it must land back on the floor")
        assertTrue(arena.ground.grounded, "landing must be back on the ground")
        assertTrue(airborneSteps > STEPS / 4, "the whole flight is off the ground, even its top: $airborneSteps steps")
    }

    @Test
    fun aCharacterWithoutJumpSpeedStaysDown() = runTest {
        val arena = arena(jumpSpeed = 0f)
        arena.run(STEPS)
        val ground = arena.position.y

        arena.intent.jump = true
        arena.run(STEPS / 2)

        assertEquals(ground, arena.position.y, GROUND_TOLERANCE)
    }

    @Test
    fun theCharacterRoundTrips() {
        val world = World()
        SceneLoader.decode(scene(jumpSpeed = 5f)).instantiate(world = world, componentRegistry = registry)

        val saved = SceneLoader.fromWorld(world, name = "x", componentRegistry = registry)
            .nodes.flatMap { it.components }.filterIsInstance<SceneCharacterController>().single()

        assertEquals(SceneCharacterController(jumpSpeed = 5f), saved)
    }

    private suspend fun arena(jumpSpeed: Float): Arena {
        val world = World()
        SceneLoader.decode(scene(jumpSpeed)).instantiate(world = world, componentRegistry = registry)
        val physicsWorld = createJoltPhysicsWorld()
        return Arena(world, PhysicsSystem(physicsWorld), CharacterControllerSystem(physicsWorld), world.named("Player"))
    }

    private class Arena(
        val world: World,
        val physics: PhysicsSystem,
        val characters: CharacterControllerSystem,
        val player: Entity,
    ) {
        val position get() = world.get<Transform>(player)!!.position
        val intent get() = world.get<MovementControl>(player)!!
        val character get() = world.get<CharacterController>(player)!!
        val ground get() = world.get<GroundContact>(player)!!

        fun run(steps: Int) = repeat(steps) {
            physics.update(world, DELTA)
            characters.update(world, DELTA)
        }
    }

    private fun World.named(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return found!!
    }

    private fun scene(jumpSpeed: Float) = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "Floor", "transform": { "position": { "x": 0.0, "y": -0.1, "z": 0.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 10.0, "y": 0.1, "z": 10.0 } } }
  ] },
  { "name": "Wall", "transform": { "position": { "x": 0.0, "y": 1.0, "z": -3.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 5.0, "y": 1.0, "z": 0.5 } } }
  ] },
  { "name": "Player", "transform": { "position": { "x": 0.0, "y": 3.0, "z": 0.0 } }, "components": [
    { "component": "movement_control", "speed": 4.0 },
    { "component": "character_controller", "jumpSpeed": $jumpSpeed }
  ] }
] }
"""

    private companion object {
        const val DELTA = 1f / 60f
        const val STEPS = 120
        const val GROUND_TOLERANCE = 0.05f
    }
}
