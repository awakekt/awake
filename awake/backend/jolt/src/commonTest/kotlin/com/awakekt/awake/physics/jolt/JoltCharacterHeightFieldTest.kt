/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.character.CharacterConfig
import com.awakekt.awake.physics.character.KinematicCharacterController
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The character controller over a sloped heightfield in a real Jolt world (#1209).
 *
 * A heightfield is solid only from above. A capsule pushed out of an overlap, which is not swept,
 * could be carried through it, and from underneath nothing stops it falling. These pin that the
 * push stops at the terrain instead.
 *
 * The crowd around a buried tree is [JoltCharacterCrowdSoakTest].
 *
 * Negative controls, measured:
 * - With the push unguarded, the capsule pushed off the wall goes through the slope and falls: its
 *   feet end 10.7 m under it.
 * - With a hit on the body being left never counted as starting inside it, the capsule in the crate
 *   stays inside, at x = 0.35.
 */
class JoltCharacterHeightFieldTest {

    private companion object {
        const val RADIUS = 0.4f
        const val HALF_HEIGHT = 0.4f
        const val HALF_EXTENT = RADIUS + HALF_HEIGHT
        const val SLOPE = 0.35f
        const val SAMPLES = 33
        const val GRAVITY = -20f
        const val STEP = 1f / 60f

        /** Feet this far under the slope are under the terrain, not resting a skin width into it. */
        const val UNDER = 0.05f
    }

    private val shape = CapsuleShape(halfHeight = HALF_HEIGHT, radius = RADIUS)
    private val config = CharacterConfig(shape, slopeLimitRadians = 1.3f, stepHeight = 0.3f)

    private suspend fun world(block: suspend (PhysicsWorld) -> Unit) {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            world.createBody(slope(), Vec3f(0f, 0f, 0f), Quat.IDENTITY, MotionType.STATIC)
            block(world)
        } finally {
            world.destroy()
        }
    }

    /** A 32x32 heightfield centred on the origin, rising [SLOPE] per metre along x: y = SLOPE * x. */
    private fun slope() = HeightFieldShape(
        FloatArray(SAMPLES * SAMPLES) { (it % SAMPLES - SAMPLES / 2) * SLOPE },
        SAMPLES,
        Vec3f(1f, 1f, 1f),
    )

    /** How far the capsule's feet are above the slope under it; negative is under the terrain. */
    private fun feetAboveGround(character: KinematicCharacterController): Float =
        character.position.y - HALF_EXTENT - SLOPE * character.position.x

    /** Gravity and a walk, [steps] times; the character's velocity resets once it is grounded. */
    private fun KinematicCharacterController.walk(steps: Int, x: Float, z: Float) {
        var velocity = 0f
        repeat(steps) {
            velocity = if (isGrounded) 0f else velocity + GRAVITY * STEP
            move(Vec3f(x * STEP, velocity * STEP, z * STEP), STEP)
        }
    }

    @Test
    fun aCapsulePushedOffAWallIntoTheSlopeStaysAboveIt() = runTest {
        world { world ->
            // A wall on the slope's low side, its face at x = -2, and a capsule 0.5 m into it with its
            // feet 0.1 m into the slope, walking at the wall: the push out of it runs uphill, into
            // the rising ground.
            world.createBody(BoxShape(Vec3f(1f, 4f, 4f)), Vec3f(-3f, 0f, 0f), Quat.IDENTITY, MotionType.STATIC)
            val start = Vec3f(-2.1f, SLOPE * -2.1f + HALF_EXTENT - 0.1f, 0f)
            val character = KinematicCharacterController(world, config, start)

            character.walk(steps = 60, x = -2f, z = 0f)

            assertTrue(feetAboveGround(character) > -UNDER, "pushed into the slope: feet ${feetAboveGround(character)}")
            assertTrue(character.position.x - RADIUS > -2f - 0.01f, "still inside the wall at x=${character.position.x}")
        }
    }

    @Test
    fun aCapsuleStartingInsideABoxStillGetsOut() = runTest {
        world { world ->
            // The centre inside a static crate: a ray from there stops at the crate at once, so the
            // guard must not count the body being left, or the capsule is never pushed out of it.
            world.createBody(BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), Vec3f(0f, 0.5f, 0f), Quat.IDENTITY, MotionType.STATIC)
            val character = KinematicCharacterController(world, config, Vec3f(0.3f, 0.9f, 0f))

            character.walk(steps = 30, x = 0f, z = 0f)

            val outside = character.position.x - RADIUS > 0.5f - 0.01f ||
                character.position.y - HALF_EXTENT > 1f - 0.01f
            assertTrue(outside, "still inside the crate at ${character.position}")
        }
    }
}
