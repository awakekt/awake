/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics.jolt

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BallSocketConstraint
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.SphereShape
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That a ball-and-socket joint pins a point and limits how far the joint bends and twists.
 *
 * Built as a pendulum: an anchor overhead and a weight hanging off to one side, so gravity alone
 * swings it. Where the weight ends up is the measurement, and it distinguishes the three things
 * that can be wrong -- a joint that does not hold lets the weight fall away, a joint with no limit
 * lets it hang straight down, and a working limit catches it partway.
 */
class JoltBallSocketTest {

    /** How far the weight starts from the anchor, along +X. */
    private val armLength = 1f

    private val anchorPoint = Vec3f(0f, 4f, 0f)

    /**
     * An anchor and a weight, joined at the anchor with the given limits.
     *
     * The twist axis points along the arm, from the anchor out to the weight, which is what it
     * would be for a limb: down the bone, away from the joint.
     */
    private suspend fun pendulum(
        swingLimit: Float = BallSocketConstraint.FREE_SWING,
        twistLimit: ClosedFloatingPointRange<Float>? = null,
    ): Triple<PhysicsWorld, BodyHandle, BodyHandle> {
        val world = createJoltPhysicsWorld()
        val anchor = world.createBody(
            BoxShape(Vec3f(0.1f, 0.1f, 0.1f)),
            anchorPoint,
            Quat.IDENTITY,
            MotionType.STATIC,
        )
        val weight = world.createBody(
            SphereShape(0.2f),
            Vec3f(anchorPoint.x + armLength, anchorPoint.y, anchorPoint.z),
            Quat.IDENTITY,
            MotionType.DYNAMIC,
        )
        world.createConstraint(
            BallSocketConstraint(
                bodyA = anchor,
                bodyB = weight,
                point = anchorPoint,
                twistAxis = Vec3f(1f, 0f, 0f),
                swingLimit = swingLimit,
                twistLimit = twistLimit,
            ),
        )
        return Triple(world, anchor, weight)
    }

    /** Where a body ended up, and its orientation, after [steps] of simulation. */
    private fun PhysicsWorld.settle(body: BodyHandle, steps: Int): Pair<Vec3f, Quat> {
        var position = Vec3f(0f, 0f, 0f)
        var rotation = Quat.IDENTITY
        repeat(steps) {
            step(1f / 60f)
            forEachBodyTransform { handle, at, facing ->
                if (handle == body) {
                    position = Vec3f(at.x, at.y, at.z)
                    rotation = Quat(facing.x, facing.y, facing.z, facing.w)
                }
            }
        }
        return position to rotation
    }

    private fun distanceFromAnchor(position: Vec3f): Float {
        val dx = position.x - anchorPoint.x
        val dy = position.y - anchorPoint.y
        val dz = position.z - anchorPoint.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    @Test
    fun anUnlimitedJointLetsTheWeightHangStraightDown() = runTest {
        val (world, _, weight) = pendulum()
        try {
            val (position, _) = world.settle(weight, steps = 300)

            // A free swing is half a turn in every direction, so nothing stops the arm rotating
            // until the weight is directly below the anchor.
            assertTrue(
                position.y < anchorPoint.y - armLength * 0.9f,
                "an unlimited joint should have swung to hanging, ended at ${position.y}",
            )
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aSwingLimitCatchesTheWeightPartway() = runTest {
        // A fifth of a right angle: far enough from both zero and from hanging that neither a
        // joint that ignores the limit nor one that locks solid could pass this by accident.
        val limit = (PI / 10).toFloat()
        val (world, _, weight) = pendulum(swingLimit = limit)
        try {
            val (position, _) = world.settle(weight, steps = 300)

            // The arm can bend by the cone half-angle and no further, so the weight ends up that
            // far around a circle of radius armLength rather than at the bottom of it.
            val drop = anchorPoint.y - position.y
            val allowed = armLength * kotlin.math.sin(limit)
            assertTrue(
                drop < allowed + 0.1f,
                "the swing limit did not hold: dropped $drop where the cone allows $allowed",
            )
            assertTrue(drop > 0f, "the joint locked solid instead of swinging to its limit")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun theJointPinsThePointRatherThanJustLimitingDistance() = runTest {
        val (world, _, weight) = pendulum()
        try {
            val (position, _) = world.settle(weight, steps = 300)

            // The whole reason this is a six-DOF constraint with translations locked rather than a
            // distance constraint: a socket holds the arm's length exactly, in both directions,
            // where a distance constraint only catches it at the far end.
            val distance = distanceFromAnchor(position)
            assertTrue(
                abs(distance - armLength) < 0.05f,
                "the arm changed length: started at $armLength, ended at $distance",
            )
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aTwistLimitStopsTheWeightSpinningAboutTheArm() = runTest {
        // Locked hard against twist, and free to swing, so the only thing under test is the twist.
        val limit = (PI / 20).toFloat()
        val (world, _, weight) = pendulum(twistLimit = -limit..limit)
        try {
            // Spun about the arm itself. Gravity produces swing and never twist, so without this
            // the twist limit is never approached and the test would pass on an ignored setting.
            world.setAngularVelocity(weight, Vec3f(20f, 0f, 0f))
            val (_, rotation) = world.settle(weight, steps = 300)

            // The twist axis is X, so twist shows up as rotation about X: for a quaternion that is
            // the x component, which reaches sin(angle / 2) at the limit.
            val twist = abs(rotation.x)
            val allowed = kotlin.math.sin(limit / 2f)
            assertTrue(
                twist < allowed + 0.05f,
                "the twist limit did not hold: reached $twist where the limit allows $allowed",
            )
        } finally {
            world.destroy()
        }
    }

    @Test
    fun anUnlimitedTwistLetsItSpinFreely() = runTest {
        val (world, _, weight) = pendulum()
        try {
            world.setAngularVelocity(weight, Vec3f(20f, 0f, 0f))
            // Short, so it is caught mid-spin rather than after damping has stopped it.
            val (_, rotation) = world.settle(weight, steps = 6)

            // The control for the test above: the same spin against no twist limit goes far past
            // where that one is pinned, so that one's assertion is measuring the limit and not
            // simply the fact that a constrained body does not spin much.
            val twist = abs(rotation.x)
            assertTrue(twist > 0.2f, "an unlimited joint barely twisted at all: $twist")
        } finally {
            world.destroy()
        }
    }
}
