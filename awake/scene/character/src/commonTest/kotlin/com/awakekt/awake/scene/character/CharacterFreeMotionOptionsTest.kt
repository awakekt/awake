/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.physics.registerPhysics
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Free motion's options (#1239): a shorter capsule, passing through moving bodies, and the ground below. */
class CharacterFreeMotionOptionsTest {
    private val registry = SceneComponentRegistry().registerControls().registerPhysics().registerCharacter()

    @Test
    fun aShortFreeCapsuleFitsUnderAnOverhangTheWalkingOneCannot() = runTest {
        val walkingSize = standingArena()
        walkingSize.character.motionMode = CharacterMotionMode.Free
        walkingSize.intent.moveZ = -2f
        walkingSize.run(STEPS)
        assertTrue(walkingSize.position.z > -3.5f, "the 2 m capsule meets the overhang; z = ${walkingSize.position.z}")

        val short = standingArena()
        short.character.freeHalfHeight = FREE_HALF_HEIGHT
        short.character.motionMode = CharacterMotionMode.Free
        short.run(1)
        assertTrue(short.character.isFreeShape)
        assertEquals(SHORT_Y, short.position.y, TOLERANCE, "it shrinks with its feet where they were")

        short.intent.moveZ = -2f
        short.run(STEPS + STEPS / 2)
        assertTrue(short.position.z < -5f, "the 1.2 m capsule passes under it; z = ${short.position.z}")
    }

    @Test
    fun walkingAgainGrowsBackOnlyWhereThereIsRoom() = runTest {
        val arena = standingArena()
        arena.character.freeHalfHeight = FREE_HALF_HEIGHT
        arena.character.motionMode = CharacterMotionMode.Free
        arena.intent.moveZ = -2f
        arena.run(STEPS) // 4 m: right under the overhang

        arena.intent.moveZ = 0f
        arena.character.motionMode = CharacterMotionMode.Grounded
        arena.run(STEPS / 4)
        assertTrue(arena.character.isFreeShape, "no headroom under the overhang, so it stays short")
        assertEquals(SHORT_Y, arena.position.y, TOLERANCE, "and walks on the floor, not pushed into the overhang")
        assertTrue(arena.character.isGrounded)

        arena.character.motionMode = CharacterMotionMode.Free
        arena.intent.moveZ = -2f
        arena.run(STEPS / 2) // out the far side
        arena.intent.moveZ = 0f
        arena.character.motionMode = CharacterMotionMode.Grounded
        arena.run(STEPS / 4)
        assertFalse(arena.character.isFreeShape, "clear of the overhang, it straightens")
        assertEquals(STANDING_Y, arena.position.y, TOLERANCE, "with its feet still on the floor")
    }

    @Test
    fun freeMotionCanPassThroughMovingBodiesButNeverStaticOnes() = runTest {
        val blocked = standingArena()
        blocked.flyFrom(Vec3f(LANE_X, HOVER_Y, 0f))
        blocked.intent.moveZ = -2f
        blocked.run(STEPS)
        assertTrue(blocked.position.z > -1.5f, "the kinematic box stops it by default; z = ${blocked.position.z}")

        val through = standingArena()
        through.character.freeCollidesWithMoving = false
        through.flyFrom(Vec3f(LANE_X, HOVER_Y, 0f))
        through.intent.moveZ = -2f
        through.run(STEPS * 2)
        assertTrue(through.position.z < -4f, "it passes through the kinematic box; z = ${through.position.z}")
        assertTrue(through.position.z > -5.5f, "but the static post still stops it; z = ${through.position.z}")
    }

    @Test
    fun groundHeightBelowSeesOnlyStaticSurfaces() = runTest {
        assertNull(arena().character.groundHeightBelow(10f), "no controller before the first step")

        val arena = standingArena()
        arena.flyFrom(Vec3f(0f, 4f, 0f))
        assertEquals(0f, assertNotNull(arena.character.groundHeightBelow(10f)), TOLERANCE, "the floor")
        assertNull(arena.character.groundHeightBelow(2f), "its feet are 3 m up, so 2 m doesn't reach the floor")
        assertEquals(0f, assertNotNull(arena.character.groundHeightBelow(3.5f)), TOLERANCE)

        arena.flyFrom(Vec3f(TABLE_X, 4f, 0f))
        assertEquals(1f, assertNotNull(arena.character.groundHeightBelow(10f)), TOLERANCE, "the table top")

        arena.flyFrom(Vec3f(LANE_X, 4f, BOX_Z))
        assertEquals(0f, assertNotNull(arena.character.groundHeightBelow(10f)), TOLERANCE, "through the kinematic box")

        assertFailsWith<IllegalArgumentException> { arena.character.groundHeightBelow(-1f) }
    }

    @Test
    fun freeHalfHeightMustBeAShorterCapsule() = runTest {
        val character = standingArena().character
        assertFailsWith<IllegalArgumentException> { character.freeHalfHeight = 0f }
        assertFailsWith<IllegalArgumentException> { character.freeHalfHeight = HALF_HEIGHT }
        character.freeHalfHeight = FREE_HALF_HEIGHT
        character.freeHalfHeight = null
        assertFalse(character.isFreeShape)
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

        fun run(steps: Int) = repeat(steps) {
            physics.update(world, DELTA)
            characters.update(world, DELTA)
        }

        /** Puts it at [at], hanging in free motion. */
        fun flyFrom(at: Vec3f) {
            character.motionMode = CharacterMotionMode.Free
            character.teleport(world.get<Transform>(player)!!, at)
            run(1)
        }
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val STEPS = 120
        const val TOLERANCE = 0.05f
        const val HALF_HEIGHT = 0.5f
        const val FREE_HALF_HEIGHT = 0.1f

        /** Floor top at 0; the 2 m capsule's centre is 1 above it. */
        const val STANDING_Y = 1f

        /** The 1.2 m free capsule's centre, its feet on the floor. */
        const val SHORT_Y = 0.6f

        /** A 2 m capsule's centre hovering 0.1 above the floor, so its sweeps don't start touching it. */
        const val HOVER_Y = 1.1f

        const val LANE_X = 6f
        const val BOX_Z = -2f
        const val TABLE_X = -6f

        // Overhang: underside at 1.5 over x -2..2, z -4.5..-3.5. Lane at x 6: a kinematic box at z -2,
        // then a static post at z -6. Table: top at 1 at x -6.
        const val SCENE = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "Floor", "transform": { "position": { "x": 0.0, "y": -0.1, "z": 0.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 20.0, "y": 0.1, "z": 20.0 } } }
  ] },
  { "name": "Overhang", "transform": { "position": { "x": 0.0, "y": 2.0, "z": -4.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 2.0, "y": 0.5, "z": 0.5 } } }
  ] },
  { "name": "Box", "transform": { "position": { "x": 6.0, "y": 1.0, "z": -2.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 0.5, "y": 1.0, "z": 0.5 } }, "motion": "KINEMATIC" }
  ] },
  { "name": "Post", "transform": { "position": { "x": 6.0, "y": 1.0, "z": -6.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 0.5, "y": 1.0, "z": 0.5 } } }
  ] },
  { "name": "Table", "transform": { "position": { "x": -6.0, "y": 0.5, "z": 0.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 0.5, "y": 0.5, "z": 0.5 } } }
  ] },
  { "name": "Player", "transform": { "position": { "x": 0.0, "y": 3.0, "z": 0.0 } }, "components": [
    { "component": "movement_control", "speed": 4.0 },
    { "component": "character_controller" }
  ] }
] }
"""
    }
}
