/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.CapsuleShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.jolt.JoltPhysicsWorld
import io.github.awakelab.awake.scene.physics.character.CharacterConfig
import io.github.awakelab.awake.scene.physics.character.KinematicCharacterController
import io.github.awakelab.awake.showcase.terrain.TerrainExampleAsset
import kotlin.test.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * The character controller against a real Jolt world, rather than against sweeps a test wrote.
 *
 * `KinematicCharacterControllerTest` pins the controller's arithmetic with a stub, which is the
 * half that is Awake's. This is the other half: that a capsule swept through actual Jolt geometry
 * reports fractions and normals the controller can use. A disagreement between the two -- a
 * normal pointing the wrong way, a fraction measured from the wrong origin -- passes every stub
 * test and produces a character that walks through walls.
 */
class CharacterControllerJoltTest {

    private companion object {
        const val RADIUS = 0.3f
        const val HALF_HEIGHT = 0.9f

        /** A capsule resting on a surface has its centre this far above it. */
        const val STANDING_CENTRE = HALF_HEIGHT + RADIUS

        const val STEP = 1f / 60f
        const val GRAVITY = -9.81f
    }

    private val config = CharacterConfig(
        shape = CapsuleShape(halfHeight = HALF_HEIGHT, radius = RADIUS),
        stepHeight = 0.3f,
    )

    private fun world(block: (JoltPhysicsWorld) -> Unit) {
        val world = JoltPhysicsWorld()
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
     * A caller doing the integration the controller deliberately does not.
     *
     * Gravity is an acceleration, so it accumulates into a velocity and only then becomes a
     * displacement -- feeding `g * dt * dt` straight to [KinematicCharacterController.move] falls
     * about three millimetres a second and never reaches the floor at all.
     */
    private class Walker(val character: KinematicCharacterController) {
        private var verticalVelocity = 0f

        fun step(x: Float = 0f, z: Float = 0f) {
            verticalVelocity = if (character.isGrounded) 0f else verticalVelocity + GRAVITY * STEP
            character.move(Vec3f(x, verticalVelocity * STEP, z))
        }

        /** One step that also carries whatever the ground underfoot is doing. */
        fun rideAndStep(groundVelocity: Vec3f) {
            verticalVelocity = if (character.isGrounded) 0f else verticalVelocity + GRAVITY * STEP
            character.move(
                Vec3f(
                    groundVelocity.x * STEP,
                    verticalVelocity * STEP + groundVelocity.y * STEP,
                    groundVelocity.z * STEP,
                ),
            )
        }

        /** Falls until grounded, so a test starts from a known stance. */
        fun settle(steps: Int = 240) {
            repeat(steps) {
                if (character.isGrounded) return
                step()
            }
        }
    }

    @Test
    fun aCharacterFallsOntoTheFloorAndStandsOnIt() {
        world { world ->
            world.addFloor()
            val character = KinematicCharacterController(world, config, Vec3f(0f, 3f, 0f))

            Walker(character).settle()

            assertTrue(character.isGrounded, "expected to land on the floor")
            // Standing means the capsule's bottom touches y=0, so its centre is a radius plus a
            // half-height above -- give or take the skin width holding it off the surface.
            val error = kotlin.math.abs(character.position.y - STANDING_CENTRE)
            assertTrue(error < 0.1f, "expected to stand at y=$STANDING_CENTRE, got ${character.position.y}")
        }
    }

    @Test
    fun aCharacterDoesNotWalkThroughARealWall() {
        world { world ->
            world.addFloor()
            // A wall 1 thick centred at x=3, so its near face is at x=2.5.
            world.createBody(
                BoxShape(Vec3f(0.5f, 2f, 5f)),
                Vec3f(3f, 2f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            val character = KinematicCharacterController(world, config, Vec3f(0f, 3f, 0f))
            val walker = Walker(character)
            walker.settle()

            repeat(120) { walker.step(x = 0.1f) }

            // The capsule's surface stops at the wall, so its centre stops a radius short of it.
            assertTrue(
                character.position.x < 2.5f - RADIUS + 0.1f,
                "expected to be stopped by the wall, reached x=${character.position.x}",
            )
            assertTrue(character.position.x > 1.5f, "expected to actually reach the wall, x=${character.position.x}")
        }
    }

    @Test
    fun aCharacterSlidesAlongARealWallRatherThanStopping() {
        world { world ->
            world.addFloor()
            world.createBody(
                BoxShape(Vec3f(0.5f, 2f, 10f)),
                Vec3f(3f, 2f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            val character = KinematicCharacterController(world, config, Vec3f(0f, 3f, 0f))
            val walker = Walker(character)
            walker.settle()

            // Pushing diagonally into the wall: the x is absorbed, the z must survive.
            repeat(120) { walker.step(x = 0.1f, z = 0.1f) }

            assertTrue(
                character.position.z > 5f,
                "expected to keep sliding along the wall, z=${character.position.z}",
            )
        }
    }

    @Test
    fun aCharacterWalksUpARealStepAndBackDownTheFarSide() {
        world { world ->
            world.addFloor()
            // A 0.25-high step spanning x = 1 to 5, inside the 0.3 step height.
            world.createBody(
                BoxShape(Vec3f(2f, 0.125f, 5f)),
                Vec3f(3f, 0.125f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            val character = KinematicCharacterController(world, config, Vec3f(0f, 3f, 0f))
            val walker = Walker(character)
            walker.settle()
            val floorHeight = character.position.y

            // Far enough to be well onto the step, not far enough to reach its far edge.
            // Deliberately slow: a step is easy to clear at speed and easy to stall against at a
            // crawl, and the crawl is the case that found the edge-contact bug.
            repeat(60) { walker.step(x = 0.05f) }

            assertTrue(character.position.x > 1.5f, "expected to get onto the step, x=${character.position.x}")
            assertTrue(
                kotlin.math.abs(character.position.y - (floorHeight + 0.25f)) < 0.05f,
                "expected to stand a step's height higher, at ${floorHeight + 0.25f}, " +
                    "got ${character.position.y}",
            )
            assertTrue(character.isGrounded, "expected to be grounded on the step")

            // Keep going, off the far edge at x = 5: step-down must follow the drop rather than
            // leaving the character hanging at step height.
            repeat(120) { walker.step(x = 0.05f) }

            assertTrue(character.position.x > 5.5f, "expected to walk off the step, x=${character.position.x}")
            assertTrue(
                kotlin.math.abs(character.position.y - floorHeight) < 0.05f,
                "expected to be back at floor height $floorHeight, got ${character.position.y}",
            )
            assertTrue(character.isGrounded, "expected to stay grounded stepping down")
        }
    }

    @Test
    fun aCharacterStandsOnTheShowcaseTerrainItself() {
        world { world ->
            world.createBody(
                TerrainExampleAsset.collisionShape,
                Vec3f(0f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            // Straight above the terrain's centre mound, which is 1.17 units high.
            val character = KinematicCharacterController(world, config, Vec3f(0f, 6f, 0f))

            Walker(character).settle()

            assertTrue(character.isGrounded, "expected to land on the terrain")
            assertTrue(
                character.position.y > 1.17f,
                "expected to stand above the mound, not inside it: y=${character.position.y}",
            )
        }
    }

    @Test
    fun aCharacterStandingOnAMovingPlatformIsCarriedByIt() {
        world { world ->
            // A kinematic slab under the character, driven sideways one step at a time.
            val platform = world.createBody(
                BoxShape(Vec3f(3f, 0.5f, 3f)),
                Vec3f(0f, -0.5f, 0f),
                Quat.IDENTITY,
                MotionType.KINEMATIC,
            )
            val character = KinematicCharacterController(world, config, Vec3f(0f, 3f, 0f))
            val walker = Walker(character)
            walker.settle()
            assertTrue(character.isGrounded, "precondition: standing on the platform")

            val speed = 1f
            var platformX = 0f
            repeat(120) {
                platformX += speed * STEP
                world.moveKinematic(platform, Vec3f(platformX, -0.5f, 0f), Quat.IDENTITY, STEP)
                world.step(STEP)
                // What a game does with groundVelocity: add it to the displacement it asks for.
                walker.rideAndStep(character.groundVelocity)
            }

            // Two seconds at one unit per second. Without carrying the ground's velocity the
            // character simply stands still while the platform slides out from under it.
            assertTrue(
                character.position.x > 1.5f,
                "expected to be carried about $platformX, got ${character.position.x}",
            )
        }
    }

    @Test
    fun aCharacterOnStaticGroundIsNotCarriedAnywhere() {
        world { world ->
            world.addFloor()
            val character = KinematicCharacterController(world, config, Vec3f(0f, 3f, 0f))
            val walker = Walker(character)
            walker.settle()

            repeat(120) {
                world.step(STEP)
                walker.rideAndStep(character.groundVelocity)
            }

            // The floor is static, so its velocity is zero and nothing drifts. A stale or phantom
            // ground velocity would show up here as a character sliding off on its own.
            assertTrue(abs(character.position.x) < 0.05f, "drifted to x=${character.position.x}")
            assertTrue(abs(character.position.z) < 0.05f, "drifted to z=${character.position.z}")
        }
    }

    @Test
    fun aCharacterCrouchesUnderARealCeilingAndCannotStandBeneathIt() {
        world { world ->
            world.addFloor()
            // A slab whose underside is 1.6 above the floor: too low for a 2.4-tall capsule to
            // stand under, high enough for a crouched one to fit.
            world.createBody(
                BoxShape(Vec3f(3f, 0.5f, 3f)),
                Vec3f(4f, 2.1f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            val crouchable = CharacterConfig(
                shape = CapsuleShape(halfHeight = HALF_HEIGHT, radius = RADIUS),
                stepHeight = 0.3f,
                crouchHalfHeight = 0.3f,
            )
            val character = KinematicCharacterController(world, crouchable, Vec3f(0f, 3f, 0f))
            val walker = Walker(character)
            walker.settle()

            character.crouch()
            assertTrue(character.isCrouching)
            repeat(140) { walker.step(x = 0.05f) }

            // Under the slab now, and standing must be refused against real geometry rather than
            // against a scripted sweep.
            assertTrue(character.position.x > 3f, "expected to get under the slab, x=${character.position.x}")
            assertTrue(!character.standUp(), "standing under a ceiling must be refused")
            assertTrue(character.isCrouching)
        }
    }

    @Test
    fun aCharacterStandsOnceItIsBackInTheOpen() {
        world { world ->
            world.addFloor()
            val crouchable = CharacterConfig(
                shape = CapsuleShape(halfHeight = HALF_HEIGHT, radius = RADIUS),
                crouchHalfHeight = 0.3f,
            )
            val character = KinematicCharacterController(world, crouchable, Vec3f(0f, 3f, 0f))
            Walker(character).settle()
            val standingY = character.position.y

            character.crouch()
            val crouchedY = character.position.y
            val stood = character.standUp()

            assertTrue(crouchedY < standingY, "crouching should lower the centre")
            assertTrue(stood, "nothing overhead, so standing must succeed")
            assertTrue(abs(character.position.y - standingY) < 1e-3f, "y=${character.position.y}")
        }
    }
}
