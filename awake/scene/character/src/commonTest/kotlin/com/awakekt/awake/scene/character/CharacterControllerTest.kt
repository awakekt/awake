/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.movement.MatrixRelativeMovementSystem
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.motion.GroundContact
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.physics.registerPhysics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

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
    fun anAgentWalksInWorldSpaceAndTheWallStopsIt() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)
        arena.intent.driver = MovementDriver.Agent

        // With no camera a player's forward is -Z, so an agent's world +Z is the other way.
        arena.intent.moveZ = 1f
        arena.run(STEPS / 4)
        assertTrue(arena.position.z > 0.5f, "an agent's +Z is world +Z; z = ${arena.position.z}")

        arena.intent.moveZ = -1f
        arena.run(STEPS * 3)
        assertTrue(arena.position.z > -2.5f + 0.4f, "the wall must stop the agent; z = ${arena.position.z}")
    }

    /** The positive control: the same agent moved without physics walks through the wall. */
    @Test
    fun anAgentMovedWithoutTheControllerPassesThroughTheWall() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)
        arena.intent.driver = MovementDriver.Agent
        arena.intent.moveZ = -1f

        repeat(STEPS * 3) { MatrixRelativeMovementSystem().update(arena.world, DELTA) }

        assertTrue(arena.position.z < -3.5f, "without the controller nothing stops it; z = ${arena.position.z}")
    }

    @Test
    fun teleportingPutsTheCharacterBehindTheWallThatWalkingStopsAt() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)

        // The wall's far face is at z = -3.5; walking there stops at its near face (see above).
        arena.character.teleport(arena.world.get<Transform>(arena.player)!!, Vec3f(0f, 1f, -5f))
        assertEquals(-5f, arena.position.z, "the scene shows the character there at once")
        arena.run(STEPS)

        assertEquals(-5f, arena.position.z, GROUND_TOLERANCE, "it stays behind the wall, not swept back")
        assertEquals(1f, arena.position.y, GROUND_TOLERANCE, "and stands on the floor there")
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

    /** At more frames than steps, a walking character still moves on every frame, and evenly. */
    @Test
    fun aWalkingCharacterMovesOnEveryFrameWhenFramesOutrunSteps() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)
        arena.intent.driver = MovementDriver.Agent
        arena.intent.moveX = 1f

        val shown = arena.frames(FRAMES, frameSeconds = 1f / 120f).map { it.x }

        val moves = shown.zipWithNext { a, b -> b - a }.drop(FRAMES / 4)
        assertTrue(moves.all { it > 0f }, "a frame without a step must still move the character: ${moves.take(8)}")
        assertTrue(moves.max() / moves.min() < EVEN_FRAMES, "each frame must move it about as far: ${moves.take(8)}")
    }

    /** A respawn puts the character there at once; it never slides across from where it was. */
    @Test
    fun aTeleportShowsAtOnceInsteadOfBlending() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)
        arena.intent.driver = MovementDriver.Agent
        arena.intent.moveX = 1f
        arena.frames(FRAMES / 2, frameSeconds = 1f / 120f)

        val target = Vec3f(4f, 1f, 4f)
        arena.character.teleport(arena.world.get<Transform>(arena.player)!!, target)
        (arena.characters as? InterpolatedSystem)?.interpolate(arena.world, 0.5f)

        assertEquals(target.x, arena.position.x, GROUND_TOLERANCE)
        assertEquals(target.z, arena.position.z, GROUND_TOLERANCE)
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

        private var unsimulated = 0f

        /**
         * [count] rendered frames of [frameSeconds], stepping at [DELTA] as a host's fixed-step loop
         * does and blending between steps where the systems can; the position each frame shows.
         */
        fun frames(count: Int, frameSeconds: Float): List<Vec3f> = List(count) {
            unsimulated += frameSeconds
            while (unsimulated >= DELTA) {
                run(1)
                unsimulated -= DELTA
            }
            (characters as? InterpolatedSystem)?.interpolate(world, unsimulated / DELTA)
            position.copy()
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
        const val FRAMES = 120

        /** How much farther the longest frame's move may be than the shortest's. */
        const val EVEN_FRAMES = 1.5f
    }
}
