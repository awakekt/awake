/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.character.CharacterConfig
import com.awakekt.awake.physics.character.KinematicCharacterController
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/**
 * A crowd of characters around a partly buried tree on a sloped heightfield, in a real Jolt world:
 * a small version of the headless soak in which capsules ended under the terrain (#1209).
 *
 * 120 capsules with inner bodies, so they push on one another, spawn in an overlapping ring 4 m out
 * and walk at the trunk for 10 s, weaving so they keep pressing from new angles. The sink this guards
 * against happens on the second step; the length is for the crowd to keep pressing, and is kept short
 * enough that the iOS simulator, the slowest target, finishes well inside the timeout.
 *
 * Negative control, measured: with the push out of an overlap unguarded, one capsule is pushed
 * through the slope on the second step, by 1.5 m mostly downward out of its neighbours, and falls
 * for the rest of the run.
 */
class JoltCharacterCrowdSoakTest {

    private companion object {
        const val SLOPE = 0.35f
        const val SAMPLES = 33
        const val COUNT = 120
        const val STEPS = 600
        const val STEP = 1f / 60f
        const val GRAVITY = -20f
        const val SPEED = 3f
        const val HALF_EXTENT = 0.8f
    }

    @Test
    fun noCapsuleEndsUnderTheTerrain() = runTest(timeout = 5.minutes) {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            val heights = FloatArray(SAMPLES * SAMPLES) { (it % SAMPLES - SAMPLES / 2) * SLOPE }
            world.createBody(HeightFieldShape(heights, SAMPLES, Vec3f(1f, 1f, 1f)), Vec3f(0f, 0f, 0f), Quat.IDENTITY, MotionType.STATIC)
            world.createBody(buriedTreeMesh(SLOPE), Vec3f(0f, 0f, 0f), Quat.IDENTITY, MotionType.STATIC)
            val config = CharacterConfig(
                CapsuleShape(halfHeight = 0.4f, radius = 0.4f),
                slopeLimitRadians = 1.3f,
                stepHeight = 0.3f,
                innerBody = true,
            )
            val characters = List(COUNT) { i ->
                val angle = 2 * PI * i / COUNT
                val x = (cos(angle) * 4).toFloat()
                val z = (sin(angle) * 4).toFloat()
                KinematicCharacterController(world, config, Vec3f(x, x * SLOPE + 1.2f, z))
            }
            val velocity = FloatArray(COUNT)
            repeat(STEPS) { step ->
                characters.forEachIndexed { i, character -> character.walk(step, i, velocity) }
            }

            val under = characters.count { it.position.y - HALF_EXTENT < it.position.x * SLOPE - 0.3f }
            assertEquals(0, under, "capsules under the terrain")
        } finally {
            world.destroy()
        }
    }

    /** One step at the trunk, weaving by up to 0.6 of a right angle, with gravity until grounded. */
    private fun KinematicCharacterController.walk(step: Int, index: Int, velocity: FloatArray) {
        val weave = sin(step * 0.05f + index) * 0.6f
        val x = -position.x + weave * position.z
        val z = -position.z - weave * position.x
        val length = sqrt(x * x + z * z).coerceAtLeast(1e-3f)
        velocity[index] = if (isGrounded) 0f else velocity[index] + GRAVITY * STEP
        move(Vec3f(x / length * SPEED * STEP, velocity[index] * STEP, z / length * SPEED * STEP), STEP)
    }
}
