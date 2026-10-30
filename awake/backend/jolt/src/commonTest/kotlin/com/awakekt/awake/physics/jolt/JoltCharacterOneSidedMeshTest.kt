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
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.character.CharacterConfig
import com.awakekt.awake.physics.character.KinematicCharacterController
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The character controller against a one-sided triangle mesh in a real Jolt world (#1030).
 *
 * A mesh is one-sided to the simulation, and character sweeps used to be too: a capsule that
 * jumped into a roof from below passed through its back face, ended inside it, and was held there
 * for good. The stub tests in `awake:physics:api` pin what the controller does with an overlap;
 * this pins that Jolt's sweeps stop a capsule at the back face in the first place.
 */
class JoltCharacterOneSidedMeshTest {

    private companion object {
        const val RADIUS = 0.3f
        const val HALF_HEIGHT = 0.5f
        const val ROOF_Y = 1.64f
        const val JUMP_SPEED = 6f
        const val GRAVITY = -14f
        const val STEP = 1f / 60f

        /** Half the capsule's height: its centre above the floor it stands on, its top above that. */
        const val HALF_EXTENT = HALF_HEIGHT + RADIUS
    }

    private val config = CharacterConfig(shape = CapsuleShape(halfHeight = HALF_HEIGHT, radius = RADIUS))

    private suspend fun world(block: suspend (PhysicsWorld) -> Unit) {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            block(world)
        } finally {
            world.destroy()
        }
    }

    /** A 20x20 floor whose top surface is exactly y = 0. */
    private fun PhysicsWorld.addFloor() = createBody(
        BoxShape(Vec3f(10f, 0.5f, 10f)),
        Vec3f(0f, -0.5f, 0f),
        Quat.IDENTITY,
        MotionType.STATIC,
    )

    /**
     * A 4x4 roof at y = [ROOF_Y]: four triangles fanned around its centre, all facing up, so it is
     * solid from above and open from below to the simulation.
     */
    private fun PhysicsWorld.addRoof() = createBody(
        MeshShape(
            vertices = floatArrayOf(
                0f, ROOF_Y, 0f,
                -2f, ROOF_Y, -2f,
                2f, ROOF_Y, -2f,
                2f, ROOF_Y, 2f,
                -2f, ROOF_Y, 2f,
            ),
            // Counter-clockwise seen from above, so every face points up.
            indices = intArrayOf(0, 2, 1, 0, 3, 2, 0, 4, 3, 0, 1, 4),
        ),
        Vec3f(0f, 0f, 0f),
        Quat.IDENTITY,
        MotionType.STATIC,
    )

    @Test
    fun aJumpUnderAOneSidedRoofIsBackOnTheGroundWithinASecond() = runTest {
        world { world ->
            world.addFloor()
            world.addRoof()
            // Off-centre, so the head meets the inside of one triangle rather than a shared edge.
            // Settled onto the floor, the head is a skin width below the roof.
            val character = KinematicCharacterController(world, config, Vec3f(0.25f, 0.83f, -0.4f))
            character.move(Vec3f(0f, 0f, 0f), STEP)
            assertTrue(character.isGrounded, "precondition: standing under the roof, y=${character.position.y}")

            var velocity = JUMP_SPEED
            var highestTop = character.position.y + HALF_EXTENT
            var jumped = false
            var landedAfter = -1
            for (step in 1..60) {
                character.move(Vec3f(0f, velocity * STEP, 0f), STEP)
                highestTop = max(highestTop, character.position.y + HALF_EXTENT)
                if (!character.isGrounded) {
                    jumped = true
                } else if (jumped) {
                    landedAfter = step
                    break
                }
                velocity = if (character.isGrounded) 0f else velocity + GRAVITY * STEP
            }

            assertTrue(jumped, "the jump must leave the ground for this to test anything")
            // Stopped by the back face: the head never passes through the roof.
            assertTrue(highestTop <= ROOF_Y + 0.01f, "the capsule rose into the roof, top reached $highestTop")
            assertTrue(landedAfter > 0, "expected to land within a second, still at y=${character.position.y}")
            assertTrue(
                abs(character.position.y - HALF_EXTENT) < 0.1f,
                "expected back on the floor, at y=${character.position.y}",
            )
        }
    }

    @Test
    fun aCapsuleStartingInsideTheRoofGetsOutWithinAFewSteps() = runTest {
        // Inside the surface already, so back faces in the sweep cannot help: the head through it,
        // the centre below it, at the centre, off it; first moves with no motion, then falling.
        val starts = listOf(Vec3f(0.25f, 1.3f, -0.4f), Vec3f(0f, 1.3f, 0f), Vec3f(0.6f, 1.9f, -1.1f))
        for (start in starts) {
            world { world ->
                world.addFloor()
                world.addRoof()
                val character = KinematicCharacterController(world, config, start)

                var velocity = 0f
                repeat(3) {
                    character.move(Vec3f(0f, velocity * STEP, 0f), STEP)
                    velocity = if (character.isGrounded) 0f else velocity + GRAVITY * STEP
                }

                // Before, the overlap read as ground and a character standing still stayed inside.
                val clearance = abs(character.position.y - ROOF_Y) - HALF_EXTENT
                assertTrue(clearance > -0.005f, "from $start: still inside the roof at y=${character.position.y}")
            }
        }
    }
}
