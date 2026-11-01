/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.physics.character.CharacterConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [CharacterControllerSystem] lifting a character found under the scene's terrain back onto it (#1209).
 *
 * The terrain is a heightfield body on a scene entity, as a `terrain` collider builds one. The
 * character starts 2 m under it, standing on a buried box.
 *
 * Negative control, measured: with the system's lift taken out, the character stays on the box, its
 * feet 1.98 m under the terrain after 2 s.
 */
class CharacterTerrainLiftTest {

    @Test
    fun aCharacterUnderTheTerrainIsBackOnItWithinAStep() = runTest {
        val world = World()
        val physicsWorld = createJoltPhysicsWorld()
        try {
            world.body(Vec3f(0f, 0f, 0f), HeightFieldShape(FloatArray(SAMPLES * SAMPLES), SAMPLES, Vec3f(1f, 1f, 1f)))
            world.body(Vec3f(2f, -2.25f, 0f), BoxShape(Vec3f(1f, 0.25f, 1f)))
            val player = world.create()
            world.add(player, Transform(position = Vec3f(2f, -2f + HALF_EXTENT + 0.02f, 0f)))
            world.add(player, CharacterController(CharacterConfig(CapsuleShape(0.4f, 0.4f)), jumpSpeed = 0f, gravity = -14f))
            val physics = PhysicsSystem(physicsWorld)
            val characters = CharacterControllerSystem(physicsWorld)

            repeat(STEPS) {
                physics.update(world, DELTA)
                characters.update(world, DELTA)
            }

            val feet = world.get<Transform>(player)!!.position.y - HALF_EXTENT
            assertTrue(feet > -0.05f, "expected back on the terrain at y = 0, feet at $feet")
            assertTrue(world.get<CharacterController>(player)!!.isGrounded, "standing on the terrain")
        } finally {
            physicsWorld.destroy()
        }
    }

    private fun World.body(at: Vec3f, shape: com.awakekt.awake.physics.PhysicsShape) {
        val entity = create()
        add(entity, Transform(position = at))
        add(entity, PhysicsBody(shape = shape, motionType = MotionType.STATIC))
    }

    private companion object {
        const val SAMPLES = 17
        const val HALF_EXTENT = 0.8f
        const val DELTA = 1f / 60f
        const val STEPS = 120
    }
}
