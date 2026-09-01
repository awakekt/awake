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
import io.github.awakelab.awake.physics.SphereShape
import io.github.awakelab.awake.physics.syncTransforms
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That a fast body stops at a thin wall instead of crossing it.
 *
 * Both directions are asserted in the same test on purpose. "It stopped" proves nothing on its own
 * -- a bullet that was never fast enough to tunnel would pass that assertion with the feature
 * removed. The control is the same scene without continuous collision, which must tunnel; only the
 * pair shows that the flag is what changed the outcome.
 */
class JoltContinuousCollisionTest {

    /** Thin enough to be crossed in one step, which is the entire point. */
    private val wallHalfThickness = 0.05f

    private val wallX = 5f

    /**
     * Fast, but under Jolt's own 500-unit clamp so the speed asked for is the speed simulated.
     * At 400 a step covers 6.7 units, so the wall is not merely passed but passed by metres.
     */
    private val bulletSpeed = 400f

    private suspend fun firedAtAWall(continuous: Boolean): Float {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        try {
            world.createBody(
                BoxShape(Vec3f(wallHalfThickness, 5f, 5f)),
                Vec3f(wallX, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            val bullet = world.createBody(
                SphereShape(0.1f),
                Vec3f(0f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
            )
            world.setContinuousCollision(bullet, continuous)
            world.setLinearVelocity(bullet, Vec3f(bulletSpeed, 0f, 0f))

            return world.furthestXOver(bullet, steps = 5)
        } finally {
            world.destroy()
        }
    }

    /** How far past the start the body ever got, not where it ended: a bounce must not hide a pass. */
    private fun PhysicsWorld.furthestXOver(body: BodyHandle, steps: Int): Float {
        var furthest = 0f
        repeat(steps) {
            step(1f / 60f)
            val x = syncTransforms().singleOrNull { it.handle == body }?.position?.x ?: furthest
            if (x > furthest) furthest = x
        }
        return furthest
    }

    @Test
    fun aFastBodyTunnelsThroughAThinWallWithoutIt() = runTest {
        val furthest = firedAtAWall(continuous = false)

        // The control. If this ever stops tunnelling -- a slower bullet, a thicker wall, a Jolt
        // default that changed -- the test below stops proving anything and this fails first.
        assertTrue(
            furthest > wallX + 1f,
            "the bullet was stopped without continuous collision, so this scene cannot show it " +
                "working: furthest x was $furthest",
        )
    }

    @Test
    fun theSameBodyIsStoppedByTheWallWithIt() = runTest {
        val furthest = firedAtAWall(continuous = true)

        assertTrue(
            furthest < wallX,
            "the bullet crossed the wall with continuous collision on: furthest x was $furthest",
        )
    }
}
