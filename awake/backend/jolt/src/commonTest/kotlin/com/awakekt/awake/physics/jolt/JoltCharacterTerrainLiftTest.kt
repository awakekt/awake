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
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.character.CharacterConfig
import com.awakekt.awake.physics.character.KinematicCharacterController
import com.awakekt.awake.physics.character.TerrainSurface
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Lifting a character found under a heightfield back onto it, in a real Jolt world (#1209).
 *
 * The terrain is a slope, y = 0.35x. Under it, a capsule either falls for good or rests on
 * something buried; here it rests on a buried static box, as the reported capsules rested on the
 * buried parts of tree meshes.
 *
 * Negative control, measured: with the search taking the first static hit as the terrain, the
 * capsule under a root is put on the root, its feet 0.52 m above the terrain, and a capsule standing
 * under a one-sided roof is lifted onto the roof.
 */
class JoltCharacterTerrainLiftTest {

    private companion object {
        const val SLOPE = 0.35f
        const val SAMPLES = 33
        const val HALF_EXTENT = 0.8f
        const val SKIN = 0.02f
    }

    private val config = CharacterConfig(CapsuleShape(halfHeight = 0.4f, radius = 0.4f))
    private val heights = FloatArray(SAMPLES * SAMPLES) { (it % SAMPLES - SAMPLES / 2) * SLOPE }
    private val shape = HeightFieldShape(heights, SAMPLES, Vec3f(1f, 1f, 1f))

    private suspend fun world(block: suspend (PhysicsWorld, TerrainSurface) -> Unit) {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            val origin = Vec3f(0f, 0f, 0f)
            val terrain = world.createBody(shape, origin, Quat.IDENTITY, MotionType.STATIC)
            block(world, TerrainSurface(terrain, shape, origin))
        } finally {
            world.destroy()
        }
    }

    private fun PhysicsWorld.addStatic(shape: com.awakekt.awake.physics.PhysicsShape, at: Vec3f) =
        createBody(shape, at, Quat.IDENTITY, MotionType.STATIC)

    @Test
    fun aCapsuleRestingUnderTheTerrainIsPutBackOnTop() = runTest {
        world { world, terrain ->
            // Feet on a buried box 2 m under the slope at x = 3: held there, as the reported ones were.
            val ground = SLOPE * 3f
            world.addStatic(BoxShape(Vec3f(1f, 0.25f, 1f)), Vec3f(3f, ground - 2.25f, 0f))
            val character = KinematicCharacterController(world, config, Vec3f(3f, ground - 2f + HALF_EXTENT + SKIN, 0f))
            repeat(5) { character.move(Vec3f(0f, -0.01f, 0f)) }
            assertTrue(character.isGrounded, "precondition: standing on the buried box, y=${character.position.y}")

            assertTrue(character.liftOutOf(terrain), "a capsule under the terrain must be lifted")

            val feet = character.position.y - HALF_EXTENT
            assertTrue(abs(feet - ground) < 0.05f, "feet at $feet, expected on the slope at $ground")
            repeat(5) { character.move(Vec3f(0f, -0.01f, 0f)) }
            assertTrue(character.isGrounded, "back on the terrain, the capsule stands on it")
        }
    }

    @Test
    fun aCapsuleUnderARootIsPutOnTheTerrainNotOnTheRoot() = runTest {
        world { world, terrain ->
            // A one-sided plate standing over the spot, like a root over the ground: the search must
            // see past it to the terrain.
            val ground = SLOPE * 3f
            world.addStatic(rootPlate(y = ground + 0.5f), Vec3f(0f, 0f, 0f))
            val character = KinematicCharacterController(world, config, Vec3f(3f, ground - 1.5f, 0f))

            assertTrue(character.liftOutOf(terrain), "a capsule under the terrain must be lifted")

            val feet = character.position.y - HALF_EXTENT
            assertTrue(abs(feet - ground) < 0.05f, "feet at $feet, expected on the terrain at $ground, not on the root")
        }
    }

    @Test
    fun aCapsuleOnOrAboveTheTerrainIsLeftAlone() = runTest {
        world { world, terrain ->
            // Standing on it; under a one-sided roof above it; and off its edge, below its lowest point.
            val ground = SLOPE * 3f
            world.addStatic(rootPlate(y = ground + 2.5f), Vec3f(0f, 0f, 0f))
            val starts = listOf(
                Vec3f(3f, ground + HALF_EXTENT + SKIN, 0f),
                Vec3f(3f, ground + HALF_EXTENT + 0.5f, 0f),
                Vec3f(30f, -20f, 0f),
            )
            for (start in starts) {
                val character = KinematicCharacterController(world, config, start)
                assertFalse(character.liftOutOf(terrain), "lifted from $start")
                assertTrue(character.position.y == start.y, "moved from $start to ${character.position}")
            }
        }
    }

    /** A 2 x 2 plate facing up at [y], centred on x = 3: one-sided, like a root or a roof. */
    private fun rootPlate(y: Float) = MeshShape(
        vertices = floatArrayOf(2f, y, -1f, 2f, y, 1f, 4f, y, 1f, 4f, y, -1f),
        // Counter-clockwise seen from above, so both triangles face up.
        indices = intArrayOf(0, 1, 2, 0, 2, 3),
    )
}
