/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics.jolt

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.HeightFieldShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.SphereShape
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real, falsifiable proof the jolt-jni step/readback loop actually simulates gravity, not
 * just returning whatever position [io.github.awakelab.awake.physics.PhysicsWorld.createBody]
 * was given back unchanged -- a dynamic body in free fall (no ground, nothing to collide
 * with) must have fallen by roughly the expected kinematic amount after a known number of
 * fixed-timestep updates. Physics under constant gravity with no other forces is
 * deterministic enough for a tolerance-based assertion.
 */
class JoltPhysicsWorldTest {
    @Test
    fun staticHeightfieldRaycastsAtItsBodyPosition() {
        val world = JoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            val position = Vec3f(10f, 2f, -4f)
            val handle = world.createBody(
                shape = HeightFieldShape(
                    heights = FloatArray(16) { 1f },
                    sampleCount = 4,
                    scale = Vec3f(2f, 3f, 2f),
                ),
                position = position,
                rotation = Vec3f(0f, 0f, 0f),
                motionType = MotionType.STATIC,
            )

            val hit = assertNotNull(
                world.raycast(Vec3f(13f, 20f, -1f), Vec3f(0f, -1f, 0f), maxDistance = 30f),
            )
            assertEquals(handle, hit.handle)
            assertEquals(5f, hit.point.y, absoluteTolerance = 0.01f)
        } finally {
            world.destroy()
        }
    }

    @Test
    fun heightfieldMapsAsymmetricRowMajorSamples() {
        val world = JoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
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
                rotation = Vec3f(0f, 0f, 0f),
                motionType = MotionType.STATIC,
            )

            // Sample (2, 1) is index 1 * 4 + 2 = 6, therefore y = 3 + 6 * 3 = 21.
            // Jolt stores heightfield blocks quantized, so its documented lossy mapping needs
            // a tolerance that is tighter than a cell-height error but not bit-exact.
            val hit = assertNotNull(
                world.raycast(Vec3f(-1f, 40f, 11f), Vec3f(0f, -1f, 0f), maxDistance = 50f),
            )
            assertEquals(21f, hit.point.y, absoluteTolerance = 0.1f)
        } finally {
            world.destroy()
        }
    }

    @Test
    fun heightfieldBodiesCanBeRepeatedlyCreatedAndDestroyed() {
        val world = JoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            repeat(32) {
                val handle = world.createBody(
                    shape = HeightFieldShape(FloatArray(16), sampleCount = 4, scale = Vec3f(1f, 1f, 1f)),
                    position = Vec3f(0f, 0f, 0f),
                    rotation = Vec3f(0f, 0f, 0f),
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
    fun dynamicBodyFallsUnderGravity() {
        val gravity = -9.81f
        val world = JoltPhysicsWorld(gravity = Vec3f(0f, gravity, 0f))
        try {
            val startY = 10f
            val handle = world.createBody(
                shape = SphereShape(radius = 0.5f),
                position = Vec3f(0f, startY, 0f),
                rotation = Vec3f(0f, 0f, 0f),
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
}
