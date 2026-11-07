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
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.physics.registerPhysics
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [CharacterController.steppedPitch] and [CharacterController.freeFacesIntent] (#1417). */
class CharacterSteppedPitchTest {
    private val registry = SceneComponentRegistry().registerControls().registerPhysics().registerCharacter()

    @Test
    fun aFreeCharacterFacedAndPitchedByGameCodeKeepsBothAcrossStepsAndFrames() = runTest {
        val arena = flyingArena()
        arena.character.steppedYaw = 1f
        arena.character.steppedPitch = -0.4f

        repeat(10) {
            arena.step()
            arena.frame(0.5f)
            arena.frame(1f)
        }

        assertEquals(1f, arena.yaw, TOLERANCE)
        assertEquals(-0.4f, arena.pitch, TOLERANCE, "nothing pitches a free character back")
        assertEquals(-0.4f, arena.character.steppedPitch, TOLERANCE)
    }

    @Test
    fun aPitchSetBetweenStepsIsBlendedTowardOnTheFramesAfterIt() = runTest {
        val arena = flyingArena()
        arena.character.steppedPitch = 0.6f
        assertEquals(0.6f, arena.character.steppedPitch, "it reads back as asked before the step")

        arena.step()
        arena.frame(0.5f)
        assertEquals(0.3f, arena.pitch, TOLERANCE, "half way between the last two steps")
        arena.frame(1f)
        assertEquals(0.6f, arena.pitch, TOLERANCE)

        arena.character.steppedPitch = 0.2f
        arena.step()
        arena.frame(0.25f)
        assertEquals(0.5f, arena.pitch, TOLERANCE, "a later pitch blends from the last stepped one")
    }

    @Test
    fun theFirstPitchBlendsFromThePitchTheCharacterHad() = runTest {
        val arena = flyingArena()
        arena.transform.rotation.x = 0.4f
        arena.character.steppedPitch = 0.8f

        arena.step()
        arena.frame(0.5f)

        assertEquals(0.6f, arena.pitch, TOLERANCE)
    }

    @Test
    fun aWalkingCharacterNeverPitchedKeepsItsOwnPitch() = runTest {
        val arena = arena()
        arena.transform.rotation.x = 0.2f
        arena.intent.turnSpeed = 100f
        arena.intent.moveX = 1f

        repeat(STEPS) {
            arena.step()
            arena.frame(0.5f)
        }

        assertEquals(0.2f, arena.pitch, "the controller writes no pitch it wasn't asked for")
        assertTrue(arena.yaw > 1f, "while it still turns toward its intent")
    }

    @Test
    fun aFreeCharacterFacesItsIntentWhenAskedTo() = runTest {
        val arena = flyingArena()
        arena.character.freeFacesIntent = true
        arena.intent.turnSpeed = 100f
        arena.intent.moveX = 2f
        arena.intent.moveY = 1f

        repeat(STEPS) { arena.step() }

        assertEquals((PI / 2).toFloat(), arena.yaw, TOLERANCE, "a free mover going +X faces +X")
        assertEquals(0f, arena.pitch, "facing the intent turns, it doesn't pitch")
    }

    @Test
    fun aNonFinitePitchIsRefused() = runTest {
        val arena = arena()
        assertFailsWith<IllegalArgumentException> { arena.character.steppedPitch = Float.NaN }
        assertFailsWith<IllegalArgumentException> { arena.character.steppedPitch = Float.NEGATIVE_INFINITY }
    }

    private suspend fun flyingArena(): Arena = arena().also {
        it.character.motionMode = CharacterMotionMode.Free
        it.step()
    }

    private suspend fun arena(): Arena {
        val world = World()
        SceneLoader.decode(SCENE).instantiate(world = world, componentRegistry = registry)
        val physicsWorld = createJoltPhysicsWorld()
        var player: Entity? = null
        world.queryEach(Name::class) { entity, name -> if (name.value == "Player") player = entity }
        return Arena(world, PhysicsSystem(physicsWorld), CharacterControllerSystem(physicsWorld), player!!).also {
            it.intent.driver = MovementDriver.Agent
        }
    }

    private class Arena(
        val world: World,
        val physics: PhysicsSystem,
        val characters: CharacterControllerSystem,
        val player: Entity,
    ) {
        val transform get() = world.get<Transform>(player)!!
        val yaw get() = transform.rotation.y
        val pitch get() = transform.rotation.x
        val intent get() = world.get<MovementControl>(player)!!
        val character get() = world.get<CharacterController>(player)!!

        fun step() {
            physics.update(world, DELTA)
            characters.update(world, DELTA)
        }

        fun frame(alpha: Float) = characters.interpolate(world, alpha)
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val STEPS = 30
        const val TOLERANCE = 1e-4f

        const val SCENE = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "Floor", "transform": { "position": { "x": 0.0, "y": -0.1, "z": 0.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 50.0, "y": 0.1, "z": 50.0 } } }
  ] },
  { "name": "Player", "transform": { "position": { "x": 0.0, "y": 1.0, "z": 0.0 } }, "components": [
    { "component": "movement_control", "speed": 4.0 },
    { "component": "character_controller" }
  ] }
] }
"""
    }
}
