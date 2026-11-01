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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** [CharacterMotionMode.Free]: a world velocity, no gravity or ground, the same collision. */
class CharacterFreeMotionTest {
    private val registry = SceneComponentRegistry().registerControls().registerPhysics().registerCharacter()

    @Test
    fun freeMotionMovesByTheWholeVelocityAndIgnoresGravity() = runTest {
        val arena = standingArena()
        arena.character.motionMode = CharacterMotionMode.Free
        arena.intent.moveY = 2f

        arena.run(STEPS / 4) // 0.5 s at 2 m/s
        assertEquals(STANDING_Y + 1f, arena.position.y, TOLERANCE, "moveY is a vertical velocity")
        assertFalse(arena.character.isGrounded, "a free character is never grounded")
        assertFalse(arena.ground.grounded)

        arena.intent.moveY = 0f
        arena.run(STEPS / 2)
        assertEquals(STANDING_Y + 1f, arena.position.y, TOLERANCE, "with no velocity it hangs where it is")
    }

    @Test
    fun freeMotionIsAWorldVelocityNotAWalkDirection() = runTest {
        val arena = standingArena()
        arena.character.motionMode = CharacterMotionMode.Free

        // A player's +Z walks toward -Z with no camera, at the intent's 4 m/s; free motion takes it as given.
        arena.intent.moveX = 3f
        arena.intent.moveZ = 1f
        arena.run(STEPS / 4)

        assertEquals(1.5f, arena.position.x, TOLERANCE)
        assertEquals(0.5f, arena.position.z, TOLERANCE)
    }

    @Test
    fun aWallStillStopsAFreeCharacterAndReportsTheContactBeforeTheSlide() = runTest {
        val arena = standingArena()
        arena.character.motionMode = CharacterMotionMode.Free
        arena.intent.moveX = 1f
        arena.intent.moveZ = -4f

        arena.run(STEPS)

        assertTrue(arena.position.z > -2.5f + 0.4f, "the wall must stop it; z = ${arena.position.z}")
        val contact = assertNotNull(arena.character.lastContact)
        assertTrue(contact.hit, "pressing into the wall is a hit every step")
        // The wall's own face, not the slide along it that the x velocity keeps up.
        assertEquals(1f, contact.normal.z, TOLERANCE, "the normal points out of the wall: ${contact.normal}")
        assertEquals(0f, contact.normal.x, TOLERANCE)
    }

    @Test
    fun aCeilingStopsAFreeCharacterRising() = runTest {
        val arena = standingArena()
        arena.character.motionMode = CharacterMotionMode.Free
        arena.intent.moveY = 4f

        arena.run(STEPS)

        // Ceiling underside at 4.5; the capsule reaches 1 above its centre.
        assertTrue(arena.position.y < 3.5f + TOLERANCE, "the ceiling must stop it; y = ${arena.position.y}")
        assertTrue(arena.position.y > 3f, "it rose up to the ceiling; y = ${arena.position.y}")
        assertEquals(-1f, assertNotNull(arena.character.lastContact).normal.y, TOLERANCE)
    }

    @Test
    fun aFreeCharacterFindsTheFloorWithoutBecomingGrounded() = runTest {
        val arena = standingArena()
        arena.character.motionMode = CharacterMotionMode.Free
        arena.intent.moveY = 1f
        arena.run(STEPS / 4)
        assertFalse(assertNotNull(arena.character.lastContact).supportBelow, "half a metre up, nothing is below")

        arena.intent.moveY = -3f
        arena.run(STEPS / 2)

        assertEquals(STANDING_Y, arena.position.y, TOLERANCE, "the floor stops the descent")
        assertTrue(assertNotNull(arena.character.lastContact).supportBelow, "and is reported below it")
        assertFalse(arena.character.isGrounded, "without grounding it")
    }

    @Test
    fun switchingModesDropsTheFallAndWalkingFindsTheGroundAgain() = runTest {
        val arena = arena()
        arena.run(FALLING_STEPS) // still falling from its spawn at y = 3
        assertTrue(arena.character.verticalVelocity < -1f, "it was falling")

        arena.character.motionMode = CharacterMotionMode.Free
        assertEquals(0f, arena.character.verticalVelocity, "entering free motion drops the fall speed")
        val held = arena.position.y
        arena.run(STEPS / 2)
        assertEquals(held, arena.position.y, TOLERANCE, "nothing carried over keeps it falling")

        arena.character.motionMode = CharacterMotionMode.Grounded
        assertEquals(0f, arena.character.verticalVelocity)
        arena.run(STEPS)
        assertEquals(STANDING_Y, arena.position.y, TOLERANCE, "walking again, it falls onto the floor")
        assertTrue(arena.character.isGrounded)
        assertTrue(arena.ground.grounded)
    }

    @Test
    fun freeMotionLeavesTheBodyFacingWhereTheCallerTurnedIt() = runTest {
        val arena = standingArena()
        arena.character.motionMode = CharacterMotionMode.Free
        arena.world.get<Transform>(arena.player)!!.rotation.y = 0.25f
        arena.intent.turnSpeed = 10f
        arena.intent.moveX = 2f

        arena.run(STEPS / 4)

        assertEquals(0.25f, arena.world.get<Transform>(arena.player)!!.rotation.y, "free motion never turns it")
    }

    @Test
    fun freeMotionStaysWithinItsHeightBounds() = runTest {
        val arena = standingArena()
        arena.character.motionMode = CharacterMotionMode.Free
        arena.character.freeMaxY = 2.5f
        arena.intent.moveY = 3f

        arena.run(STEPS / 2)

        assertEquals(2.5f, arena.position.y, TOLERANCE)
    }

    private suspend fun standingArena(): Arena = arena().also { it.run(STEPS) }

    private suspend fun arena(): Arena {
        val world = World()
        SceneLoader.decode(SCENE).instantiate(world = world, componentRegistry = registry)
        val physicsWorld = createJoltPhysicsWorld()
        var player: Entity? = null
        world.queryEach(Name::class) { entity, name -> if (name.value == "Player") player = entity }
        return Arena(world, PhysicsSystem(physicsWorld), CharacterControllerSystem(physicsWorld), player!!)
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

    private companion object {
        const val DELTA = 1f / 60f
        const val STEPS = 120
        const val FALLING_STEPS = 20
        const val TOLERANCE = 0.05f

        /** Floor top at 0, capsule centre half its height above it. */
        const val STANDING_Y = 1f

        val SCENE = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "Floor", "transform": { "position": { "x": 0.0, "y": -0.1, "z": 0.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 10.0, "y": 0.1, "z": 10.0 } } }
  ] },
  { "name": "Wall", "transform": { "position": { "x": 0.0, "y": 1.0, "z": -3.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 5.0, "y": 1.0, "z": 0.5 } } }
  ] },
  { "name": "Ceiling", "transform": { "position": { "x": 0.0, "y": 5.0, "z": 0.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 10.0, "y": 0.5, "z": 10.0 } } }
  ] },
  { "name": "Player", "transform": { "position": { "x": 0.0, "y": 3.0, "z": 0.0 } }, "components": [
    { "component": "movement_control", "speed": 4.0 },
    { "component": "character_controller" }
  ] }
] }
"""
    }
}
