/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.character.CharacterConfig
import com.awakekt.awake.physics.character.KinematicCharacterController
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A terrain collider is the ground the clipmap draws, not the raw samples split once a quad (#1768).
 *
 * Negative control, measured with the raw samples (factor 1): the 3x3 saddle can't be a heightfield at
 * all (Jolt needs 4 samples a side), and on the twisted map the collider lies up to 0.82 m off the
 * drawn surface and the controllers rest up to 0.75 m off it.
 */
class TerrainColliderDrawnSurfaceTest {
    private val config = CharacterConfig(CapsuleShape(halfHeight = 0.4f, radius = 0.4f), slopeLimitRadians = 1.3f)

    @Test
    fun aControllerAtTheSaddleCentreStandsOnTheDrawnCentreVertex() = runTest {
        // a = 0, b = 4, c = 4, d = 0 in the first quad, at scale 4: drawn and bilinear say 2, (a + d) / 2 says 0.
        val saddle = Heightmap(floatArrayOf(0f, 4f, 0f, 4f, 0f, 4f, 0f, 4f, 0f), 3, 3, Vec3f(4f, 1f, 4f))

        onCollider(saddle) { physics ->
            val ground = rest(physics, -2f, -2f).groundHeightBelow(MAX_PROBE)

            assertEquals(2f, saddle.heightAtWorld(-2f, -2f), "the bilinear reference")
            assertEquals(2f, saddle.drawnHeight(-2f, -2f), "the drawn centre vertex")
            assertEquals(2f, ground ?: Float.NaN, TOLERANCE, "the controller stands on the drawn centre")
        }
    }

    @Test
    fun aControllerRestsOnTheDrawnSurfaceOfATwistedMap() = runTest {
        val map = twistedMap(Random(SEED))
        val random = Random(SEED + 1)
        var worst = 0f

        onCollider(map) { physics ->
            repeat(CONTROLLERS) {
                // A quad centre: where the raw split is furthest from what's drawn.
                val x = map.minX + (random.nextInt(2, SIDE - 3) + 0.5f) * SCALE
                val z = map.minZ + (random.nextInt(2, SIDE - 3) + 0.5f) * SCALE
                val character = rest(physics, x, z)
                assertTrue(character.isGrounded, "the controller at $x, $z rests on the ground")
                val position = character.position
                val ground = checkNotNull(character.groundHeightBelow(MAX_PROBE)) { "no ground under $position" }
                worst = maxOf(worst, abs(ground - map.drawnHeight(position.x, position.z)))
            }
        }

        assertTrue(worst <= TOLERANCE, "every controller stands within $TOLERANCE m of the drawn surface; worst $worst m")
    }

    @Test
    fun theColliderHasTheDrawnVerticesEverywhere() = runTest {
        val map = twistedMap(Random(SEED))
        var worst = 0f

        onCollider(map) { physics ->
            val origin = Vec3f()
            val down = Vec3f(0f, -1f, 0f)
            // Four rays a sample cell, each off the vertex lattice and inside the map.
            for (z in 0 until (SIDE - 1) * 4) {
                for (x in 0 until (SIDE - 1) * 4) {
                    val worldX = map.minX + x * SCALE / 4f + EDGE
                    val worldZ = map.minZ + z * SCALE / 4f + EDGE
                    origin.set(worldX, RAY_TOP, worldZ)
                    val hit = checkNotNull(physics.raycast(origin, down, RAY_TOP * 2f)) { "no collider at $worldX, $worldZ" }
                    worst = maxOf(worst, abs(RAY_TOP - hit.distance - map.drawnHeight(worldX, worldZ)))
                }
            }
        }

        assertTrue(worst <= TOLERANCE, "the collider lies on the drawn surface; worst $worst m")
    }

    /** A controller dropped at [x], [z] from above the map and let fall and settle. */
    private fun rest(physics: PhysicsWorld, x: Float, z: Float): KinematicCharacterController {
        val character = KinematicCharacterController(physics, config, Vec3f(x, RAY_TOP, z))
        var velocity = 0f
        repeat(SETTLE_STEPS) {
            velocity = if (character.isGrounded) 0f else velocity + GRAVITY * STEP
            character.move(Vec3f(0f, velocity * STEP, 0f), STEP)
        }
        return character
    }

    /** Builds [map]'s collider through [TerrainColliderSystem] and [PhysicsSystem], as a project does. */
    private suspend fun onCollider(map: Heightmap, block: (PhysicsWorld) -> Unit) {
        val physics = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            val world = World()
            val terrain = world.create()
            world.add(terrain, Transform())
            world.add(terrain, TerrainComponent(heightmap = map, collider = true))
            TerrainColliderSystem().update(world, STEP)
            PhysicsSystem(physics).update(world, STEP)
            block(physics)
        } finally {
            physics.destroy()
        }
    }

    private fun twistedMap(random: Random) =
        Heightmap(FloatArray(SIDE * SIDE) { random.nextFloat() * 2f * AMPLITUDE - AMPLITUDE }, SIDE, SIDE, Vec3f(SCALE, 1f, SCALE))

    private companion object {
        const val SEED = 1768
        const val SIDE = 17
        const val SCALE = 4f
        const val AMPLITUDE = 1.5f
        const val CONTROLLERS = 24
        const val TOLERANCE = 0.05f
        const val MAX_PROBE = 2f
        const val RAY_TOP = 20f

        /** Off the vertex lattice, so a ray lands inside a triangle rather than on a shared edge. */
        const val EDGE = 0.37f
        const val GRAVITY = -20f
        const val STEP = 1f / 60f
        const val SETTLE_STEPS = 120
    }
}
