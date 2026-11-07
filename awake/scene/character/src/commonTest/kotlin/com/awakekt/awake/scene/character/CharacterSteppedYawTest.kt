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

/** [CharacterController.steppedYaw]: game code faces a character per step, recorded and blended like a walking turn (#1417). */
class CharacterSteppedYawTest {
    private val registry = SceneComponentRegistry().registerControls().registerPhysics().registerCharacter()

    @Test
    fun aFreeCharacterFacedByGameCodeKeepsThatFacingAcrossStepsAndFrames() = runTest {
        val arena = flyingArena()
        arena.character.steppedYaw = 1f

        repeat(10) {
            arena.step()
            arena.frame(0.5f)
            arena.frame(1f)
        }

        assertEquals(1f, arena.yaw, TOLERANCE, "nothing turns a free character back")
        assertEquals(1f, arena.character.steppedYaw, TOLERANCE)
    }

    @Test
    fun aFacingSetBetweenStepsIsBlendedTowardOnTheFramesAfterIt() = runTest {
        val arena = flyingArena()
        arena.character.steppedYaw = 0.6f
        assertEquals(0.6f, arena.character.steppedYaw, "it reads back as asked before the step")

        arena.step()
        arena.frame(0.5f)
        assertEquals(0.3f, arena.yaw, TOLERANCE, "half way between the last two steps")
        arena.frame(1f)
        assertEquals(0.6f, arena.yaw, TOLERANCE)
        assertEquals(0.6f, arena.character.steppedYaw, TOLERANCE)
    }

    @Test
    fun aTurnRateEachStepReadsTheSteppedYawNotTheBlendedOne() = runTest {
        val arena = flyingArena()

        repeat(STEPS) {
            // A flight model's turn: the stepped yaw plus a rate, never the frame's blended rotation.
            arena.character.steppedYaw += TURN_PER_STEP
            arena.step()
            arena.frame(0.5f)
        }

        assertEquals(STEPS * TURN_PER_STEP, arena.character.steppedYaw, TOLERANCE)
        assertEquals((STEPS - 0.5f) * TURN_PER_STEP, arena.yaw, TOLERANCE, "the frame shows half a step behind")
    }

    @Test
    fun aFreeCharacterNotFacedByGameCodeDoesNotTurnTowardItsIntent() = runTest {
        val arena = flyingArena()
        arena.intent.turnSpeed = 10f
        arena.intent.moveX = 2f

        repeat(STEPS) { arena.step() }

        assertEquals(0f, arena.yaw, "free motion has no turn of its own")
    }

    @Test
    fun aWalkingCharacterStillTurnsTowardItsIntent() = runTest {
        val arena = arena()
        arena.intent.turnSpeed = 100f
        arena.intent.moveX = 1f

        repeat(STEPS) { arena.step() }

        assertEquals((PI / 2).toFloat(), arena.yaw, TOLERANCE, "an agent walking +X faces +X")
        assertEquals(arena.yaw, arena.character.steppedYaw, "the stepped yaw is the walk's")
    }

    @Test
    fun aNonFiniteYawIsRefused() = runTest {
        val arena = arena()
        assertFailsWith<IllegalArgumentException> { arena.character.steppedYaw = Float.NaN }
        assertFailsWith<IllegalArgumentException> { arena.character.steppedYaw = Float.POSITIVE_INFINITY }
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
        val yaw get() = world.get<Transform>(player)!!.rotation.y
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
        const val TURN_PER_STEP = 0.05f
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
