/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.physics.syncTransforms
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real, falsifiable proof the jolt-jni step/readback loop actually simulates gravity, not
 * just returning whatever position [com.awakekt.awake.physics.PhysicsWorld.createBody]
 * was given back unchanged -- a dynamic body in free fall (no ground, nothing to collide
 * with) must have fallen by roughly the expected kinematic amount after a known number of
 * fixed-timestep updates. Physics under constant gravity with no other forces is
 * deterministic enough for a tolerance-based assertion.
 */
class JoltPhysicsWorldTest {
    @Test
    fun dynamicBodyFallsUnderGravity() = runTest {
        val gravity = -9.81f
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, gravity, 0f))
        try {
            val startY = 10f
            val handle = world.createBody(
                shape = SphereShape(radius = 0.5f),
                position = Vec3f(0f, startY, 0f),
                rotation = Quat.IDENTITY,
                motionType = MotionType.DYNAMIC,
            )

            val deltaTime = 1f / 60f
            val steps = 60
            repeat(steps) { world.step(deltaTime) }

            val transform = world.syncTransforms().single { it.handle == handle }
            val fallDistance = startY - transform.position.y

            // Semi-implicit ("symplectic") Euler integration over `steps` fixed ticks of
            // `deltaTime` gives fallDistance = g * dt^2 * steps * (steps + 1) / 2, which is
            // slightly larger than the continuous 0.5 * g * t^2 kinematic formula -- use a
            // generous +/-20% tolerance around that discrete estimate rather than assuming
            // Jolt's exact internal integration scheme.
            val expectedFallDistance = -gravity * deltaTime * deltaTime * steps * (steps + 1) / 2f
            assertTrue(
                abs(fallDistance - expectedFallDistance) < expectedFallDistance * 0.2f,
                "Expected the sphere to fall about $expectedFallDistance m over $steps steps " +
                    "of $deltaTime s each, but it fell $fallDistance m " +
                    "(from y=$startY to y=${transform.position.y}).",
            )
        } finally {
            world.destroy()
        }
    }

    @Test
    fun shiftingTheOriginMovesEveryBodyAndTheyStayThere() = runTest {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            val staticBody = world.createBody(
                shape = BoxShape(Vec3f(1f, 1f, 1f)),
                position = Vec3f(10f, 0f, 0f),
                rotation = Quat.IDENTITY,
                motionType = MotionType.STATIC,
            )
            val dynamicBody = world.createBody(
                shape = SphereShape(radius = 1f),
                position = Vec3f(-4f, 6f, 2f),
                rotation = Quat.IDENTITY,
                motionType = MotionType.DYNAMIC,
            )
            val shift = Vec3f(-1_024f, 0f, 512f)

            world.shiftOrigin(shift)
            // Stepped afterwards on purpose: the simulation gets the last word on a body's
            // position, so a shift that does not survive a step has not really happened -- which
            // is exactly what shifting only the ECS transform looks like.
            repeat(STEPS_AFTER_SHIFT) { world.step(FIXED_STEP) }

            // The static body is checked by casting at it rather than by reading it back: a
            // static body is never awake, so an active-body readback never reports one. Hitting it
            // where the shift should have put it -- and missing where it used to be -- proves the
            // move reached Jolt, which reading a cached pose would not.
            val atNewPosition = world.raycast(
                origin = Vec3f(10f + shift.x, 10f, shift.z),
                direction = Vec3f(0f, -1f, 0f),
                maxDistance = 20f,
            )
            assertEquals(staticBody, assertNotNull(atNewPosition).handle)
            assertNull(
                world.raycast(Vec3f(10f, 10f, 0f), Vec3f(0f, -1f, 0f), maxDistance = 20f),
                "the static body must not still be at its old position",
            )

            // The dynamic body is checked the same way, and for a reason worth knowing: with no
            // gravity it stops moving, Jolt puts it to sleep, and a sleeping body is not in the
            // active readback either. Casting at it asks the simulation where it is rather than
            // asking for a list it is no longer on.
            val dynamicAtNewPosition = world.raycast(
                origin = Vec3f(-4f + shift.x, 12f, 2f + shift.z),
                direction = Vec3f(0f, -1f, 0f),
                maxDistance = 20f,
            )
            assertEquals(dynamicBody, assertNotNull(dynamicAtNewPosition).handle)
        } finally {
            world.destroy()
        }
    }

    @Test
    fun staticHeightfieldRaycastsAtItsBodyPosition() = runTest {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            val position = Vec3f(10f, 2f, -4f)
            val handle = world.createBody(
                shape = HeightFieldShape(
                    heights = FloatArray(16) { 1f },
                    sampleCount = 4,
                    scale = Vec3f(2f, 3f, 2f),
                ),
                position = position,
                rotation = Quat.IDENTITY,
                motionType = MotionType.STATIC,
            )

            // Straight down the body's own position: the field is centred there, so this is the
            // middle of the collider rather than a corner that edge cases could decide either way.
            val hit = assertNotNull(
                world.raycast(Vec3f(10f, 20f, -4f), Vec3f(0f, -1f, 0f), maxDistance = 30f),
            )
            assertEquals(handle, hit.handle)
            assertEquals(5f, hit.point.y, absoluteTolerance = 0.01f)
        } finally {
            world.destroy()
        }
    }

    @Test
    fun heightfieldMapsAsymmetricRowMajorSamples() = runTest {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            val position = Vec3f(-5f, 3f, 7f)
            val scale = Vec3f(2f, 3f, 4f)
            world.createBody(
                shape = HeightFieldShape(
                    heights = FloatArray(16) { it.toFloat() },
                    sampleCount = 4,
                    scale = scale,
                ),
                position = position,
                rotation = Quat.IDENTITY,
                motionType = MotionType.STATIC,
            )

            // Sample (2, 1) is index 1 * 4 + 2 = 6, therefore y = 3 + 6 * 3 = 21.
            //
            // The field is centred on the body, so that sample is at the body position plus
            // (2 * 2 - 3, _, 1 * 4 - 6) = (-4, _, 5) -- not at (-1, _, 11), which is where a
            // corner-anchored field put it and is off this collider entirely.
            // Jolt stores heightfield blocks quantized, so its documented lossy mapping needs
            // a tolerance that is tighter than a cell-height error but not bit-exact.
            val hit = assertNotNull(
                world.raycast(Vec3f(-4f, 40f, 5f), Vec3f(0f, -1f, 0f), maxDistance = 50f),
            )
            assertEquals(21f, hit.point.y, absoluteTolerance = 0.1f)
        } finally {
            world.destroy()
        }
    }

    @Test
    fun heightfieldBodiesCanBeRepeatedlyCreatedAndDestroyed() = runTest {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            repeat(32) {
                val handle = world.createBody(
                    shape = HeightFieldShape(FloatArray(16), sampleCount = 4, scale = Vec3f(1f, 1f, 1f)),
                    position = Vec3f(0f, 0f, 0f),
                    rotation = Quat.IDENTITY,
                    motionType = MotionType.STATIC,
                )
                world.destroyBody(handle)
            }

            assertEquals(
                null,
                world.raycast(Vec3f(1f, 10f, 1f), Vec3f(0f, -1f, 0f), maxDistance = 20f),
            )
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aBoxLandsOnAHeightfieldRatherThanFallingThroughIt() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            // Flat and level, so where the box comes to rest is known: samples of 2 at a y scale
            // of 1 put the surface at y = 2.
            val heights = FloatArray(SAMPLES * SAMPLES) { 2f }
            world.createBody(
                HeightFieldShape(heights, SAMPLES, Vec3f(1f, 1f, 1f)),
                Vec3f(0f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            val box = world.createBody(
                BoxShape(Vec3f(0.5f, 0.5f, 0.5f)),
                Vec3f(0f, 8f, 0f),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
            )

            // The last pose reported, not the pose after the last step: a box that lands settles,
            // and a settled body stops being awake, so it stops being visited at all. Reading it
            // afterwards throws. A game does not notice because its Transform still holds the
            // last value written -- which is what this keeps.
            var y = 8f
            repeat(180) {
                world.step(1f / 60f)
                world.forEachBodyTransform { handle, position, _ ->
                    if (handle == box) y = position.y
                }
            }

            // The assertion no raycast can make. Winding decides which side of a triangle is
            // solid, and a surface wound the wrong way is still hit by rays from both sides while
            // bodies drop straight through it -- so a heightfield built as a mesh, as iOS builds
            // it, is only proven by something resting on it.
            assertTrue(y > 2f, "the box fell through the terrain: y=$y")
            assertTrue(y < 3f, "the box did not settle on the surface at y=2: y=$y")
        } finally {
            world.destroy()
        }
    }

    private companion object {
        const val FIXED_STEP = 1f / 60f

        /** Long enough for a sleeping body to settle and be written back, which is where a shift
         * that forgot to activate would visibly lose one. */
        const val STEPS_AFTER_SHIFT = 30

        /** Jolt wants at least two blocks per edge, and a flat field needs no more than that. */
        const val SAMPLES = 4
    }
}
