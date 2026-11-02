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
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.character.CharacterConfig
import com.awakekt.awake.physics.character.KinematicCharacterController
import com.awakekt.awake.physics.character.TerrainCollider
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Static ground on a layer of the game's own, which the controller's rays find once its
 * `staticLayer` names that layer (#1241): the guard on a push out of an overlap, the lift out from
 * under terrain, and `groundHeightBelow`. Each test also runs the default, `CollisionLayers.World`,
 * which can't see that ground, so it shows the layer is what made the difference.
 */
class JoltCharacterStaticLayerTest {

    private companion object {
        const val RADIUS = 0.4f
        const val HALF_HEIGHT = 0.4f
        const val HALF_EXTENT = RADIUS + HALF_HEIGHT
        const val SLOPE = 0.35f
        const val SAMPLES = 33
        const val GRAVITY = -20f
        const val STEP = 1f / 60f
        const val UNDER = 0.05f

        /** Static scenery the game keeps apart from [CollisionLayers.World]. */
        val Scenery = CollisionLayer(2)

        /** The default two layers plus [Scenery], which moving bodies collide with as they do with the world. */
        val layers = CollisionLayers(count = 3, movingLayers = setOf(CollisionLayers.Moving)) { a, b ->
            a == CollisionLayers.Moving || b == CollisionLayers.Moving
        }
    }

    private val shape = CapsuleShape(halfHeight = HALF_HEIGHT, radius = RADIUS)
    private val config = CharacterConfig(shape, slopeLimitRadians = 1.3f, stepHeight = 0.3f)
    private val heights = FloatArray(SAMPLES * SAMPLES) { (it % SAMPLES - SAMPLES / 2) * SLOPE }
    private val slope = HeightFieldShape(heights, SAMPLES, Vec3f(1f, 1f, 1f))

    /** A world with [slope], y = SLOPE * x, on [Scenery]; [block] gets the slope's collider. */
    private suspend fun world(block: suspend (PhysicsWorld, TerrainCollider) -> Unit) {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f), layers = layers)
        try {
            val origin = Vec3f(0f, 0f, 0f)
            val terrain = world.createBody(slope, origin, Quat.IDENTITY, MotionType.STATIC, layer = Scenery)
            block(world, TerrainCollider(terrain, slope, origin))
        } finally {
            world.destroy()
        }
    }

    private fun feetAboveGround(character: KinematicCharacterController): Float =
        character.position.y - HALF_EXTENT - SLOPE * character.position.x

    private fun KinematicCharacterController.walk(steps: Int, x: Float) {
        var velocity = 0f
        repeat(steps) {
            velocity = if (isGrounded) 0f else velocity + GRAVITY * STEP
            move(Vec3f(x * STEP, velocity * STEP, 0f), STEP)
        }
    }

    /** The case from #1209: a capsule in a wall on the slope's low side is pushed out uphill, into the slope. */
    private fun pushedOffAWall(world: PhysicsWorld, staticLayer: CollisionLayer?): KinematicCharacterController {
        world.createBody(BoxShape(Vec3f(1f, 4f, 4f)), Vec3f(-3f, 0f, 0f), Quat.IDENTITY, MotionType.STATIC)
        val start = Vec3f(-2.1f, SLOPE * -2.1f + HALF_EXTENT - 0.1f, 0f)
        return KinematicCharacterController(world, config, start).also {
            it.staticLayer = staticLayer
            it.walk(steps = 60, x = -2f)
        }
    }

    @Test
    fun aPushStopsShortOfStaticGroundOnTheGamesOwnLayer() = runTest {
        world { world, _ ->
            val character = pushedOffAWall(world, staticLayer = Scenery)

            assertTrue(feetAboveGround(character) > -UNDER, "pushed into the slope: feet ${feetAboveGround(character)}")
        }
    }

    @Test
    fun theDefaultLayerDoesNotGuardAgainstGroundOnAnother() = runTest {
        world { world, _ ->
            val character = pushedOffAWall(world, staticLayer = CollisionLayers.World)

            assertTrue(feetAboveGround(character) < -1f, "the push crossed the slope: feet ${feetAboveGround(character)}")
        }
    }

    @Test
    fun theLiftFindsTerrainOnTheGamesOwnLayer() = runTest {
        world { world, terrain ->
            val ground = SLOPE * 3f
            // Feet on a buried box 2 m under the slope at x = 3.
            world.createBody(BoxShape(Vec3f(1f, 0.25f, 1f)), Vec3f(3f, ground - 2.25f, 0f), Quat.IDENTITY, MotionType.STATIC)
            val start = Vec3f(3f, ground - 2f + HALF_EXTENT + 0.02f, 0f)
            val withDefault = KinematicCharacterController(world, config, start)
            val withScenery = KinematicCharacterController(world, config, start).also { it.staticLayer = Scenery }

            assertFalse(withDefault.liftOutOf(terrain), "the default layer can't see the terrain to lift onto")
            assertTrue(withScenery.liftOutOf(terrain), "a capsule under the terrain must be lifted")
            val feet = withScenery.position.y - HALF_EXTENT
            assertTrue(abs(feet - ground) < 0.05f, "feet at $feet, expected on the slope at $ground")
        }
    }

    @Test
    fun groundHeightBelowReportsGroundOnTheGamesOwnLayer() = runTest {
        world { world, _ ->
            val ground = SLOPE * 3f
            val character = KinematicCharacterController(world, config, Vec3f(3f, ground + 3f, 0f))

            assertNull(character.groundHeightBelow(10f), "the default layer can't see the slope")
            character.staticLayer = Scenery
            val height = assertNotNull(character.groundHeightBelow(10f))
            assertTrue(abs(height - ground) < 0.01f, "ground at $height, expected $ground")
        }
    }

    @Test
    fun withNoLayerTheRaysSeeEverythingButTheCharactersOwnBody() = runTest {
        world { world, _ ->
            val ground = SLOPE * 3f
            // Its own body around it, and a crate under it standing on the slope.
            val character = KinematicCharacterController(world, config.copy(innerBody = true), Vec3f(3f, ground + 3f, 0f))
            character.staticLayer = null
            assertNotNull(character.body, "precondition: the character carries a body of its own")

            val overSlope = assertNotNull(character.groundHeightBelow(10f))
            world.createBody(BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), Vec3f(3f, ground + 0.5f, 0f), Quat.IDENTITY, MotionType.KINEMATIC)
            val overCrate = assertNotNull(character.groundHeightBelow(10f))

            assertTrue(abs(overSlope - ground) < 0.01f, "ground at $overSlope, expected $ground: its own body is seen through")
            assertEquals(ground + 1f, overCrate, 0.01f, "with no layer, the crate is ground too")
        }
    }
}
