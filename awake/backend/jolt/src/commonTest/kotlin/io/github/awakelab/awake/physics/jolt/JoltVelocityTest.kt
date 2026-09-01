/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics.jolt

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.syncTransforms
import io.github.awakelab.awake.physics.SphereShape
import kotlin.math.abs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Velocity and impulse against a real Jolt world.
 *
 * Each of these asserts the behaviour the API's own doc comment claims -- that an impulse is
 * mass-aware where a velocity write is not, and that a kinematic body moved through
 * `moveKinematic` genuinely has a velocity rather than teleporting. Those distinctions are the
 * only reason four methods exist instead of one.
 */
class JoltVelocityTest {

    private suspend fun world(block: suspend (PhysicsWorld) -> Unit) {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            block(world)
        } finally {
            world.destroy()
        }
    }

    private fun PhysicsWorld.dynamicSphere(radius: Float = 0.5f): BodyHandle = createBody(
        SphereShape(radius),
        Vec3f(0f, 0f, 0f),
        Quat.IDENTITY,
        MotionType.DYNAMIC,
    )

    @Test
    fun aVelocityWrittenIsAVelocityReadBack()  = runTest {
        world { world ->
            val body = world.dynamicSphere()

            world.setLinearVelocity(body, Vec3f(3f, -4f, 5f))
            val velocity = world.getLinearVelocity(body)

            assertTrue(abs(velocity.x - 3f) < 1e-3f, "x was ${velocity.x}")
            assertTrue(abs(velocity.y - (-4f)) < 1e-3f, "y was ${velocity.y}")
            assertTrue(abs(velocity.z - 5f) < 1e-3f, "z was ${velocity.z}")
        }
    }

    @Test
    fun aVelocitySetOnABodyActuallyMovesIt()  = runTest {
        world { world ->
            val body = world.dynamicSphere()
            world.setLinearVelocity(body, Vec3f(10f, 0f, 0f))

            repeat(60) { world.step(1f / 60f) }

            // A second at 10 units/s, in a world with no gravity to curve it.
            val x = world.syncTransforms().single { it.handle == body }.position.x
            assertTrue(abs(x - 10f) < 0.5f, "expected to travel ~10 units, reached $x")
        }
    }

    @Test
    fun theSameImpulseMovesALightBodyFurtherThanAHeavyOne()  = runTest {
        world { world ->
            // Jolt derives mass from shape volume at the default density, so the bigger box is
            // the heavier one. This is the property that makes addImpulse different from
            // setLinearVelocity, and the reason both exist.
            val light = world.createBody(
                BoxShape(Vec3f(0.25f, 0.25f, 0.25f)),
                Vec3f(0f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
            )
            val heavy = world.createBody(
                BoxShape(Vec3f(1f, 1f, 1f)),
                Vec3f(0f, 0f, 10f),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
            )

            world.addImpulse(light, Vec3f(10f, 0f, 0f))
            world.addImpulse(heavy, Vec3f(10f, 0f, 0f))

            val lightSpeed = world.getLinearVelocity(light).x
            val heavySpeed = world.getLinearVelocity(heavy).x
            assertTrue(
                lightSpeed > heavySpeed * 2f,
                "an impulse must be mass-aware: light=$lightSpeed heavy=$heavySpeed",
            )
        }
    }

    @Test
    fun aKinematicBodyMovedTowardAPoseHasAVelocityRatherThanTeleporting()  = runTest {
        world { world ->
            val platform = world.createBody(
                BoxShape(Vec3f(2f, 0.25f, 2f)),
                Vec3f(0f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.KINEMATIC,
            )

            val step = 1f / 60f
            world.moveKinematic(platform, Vec3f(0f, 0f, 1f), Quat.IDENTITY, step)

            // The velocity Jolt derived to arrive in one step: 1 unit in 1/60s is 60 units/s.
            // A teleport would leave this at zero, and anything riding the platform behind.
            val velocity = world.getLinearVelocity(platform)
            assertTrue(velocity.z > 50f, "expected a derived velocity along +z, got $velocity")
        }
    }

        @Test
    fun theVisitedPositionAndRotationAreScratchAndMustBeCopied()  = runTest {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            repeat(2) { index ->
                world.createBody(
                    SphereShape(0.5f),
                    Vec3f(index * 10f, 0f, 0f),
                    Quat.IDENTITY,
                    MotionType.DYNAMIC,
                )
            }
            world.step(1f / 60f)

            // Keeping the reference is the mistake the contract warns about, and this is what it
            // looks like: both entries end up as the last body visited.
            val kept = mutableListOf<Vec3f>()
            world.forEachBodyTransform { _, position, _ -> kept += position }

            assertEquals(2, kept.size)
            assertTrue(kept[0] === kept[1], "the same scratch vector is handed to every body")
        } finally {
            world.destroy()
        }
    }
}
