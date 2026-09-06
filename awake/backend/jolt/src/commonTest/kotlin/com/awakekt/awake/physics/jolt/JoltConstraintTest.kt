/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.DistanceConstraint
import com.awakekt.awake.physics.HingeConstraint
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That two bodies tied together stay tied, and that untying them is safe in any order.
 *
 * The lifecycle half matters more than the physics half. Jolt does not detach a constraint when a
 * body it references is destroyed -- it crashes on the next step -- so the ordering rule is not a
 * tidiness convention, and a test that destroys a constrained body is the only way to know the
 * backend honours it. With asserts enabled on the simulator that failure is a killed process
 * rather than a red test.
 */
class JoltConstraintTest {

    private fun PhysicsWorld.box(at: Vec3f, motionType: MotionType): BodyHandle = createBody(
        BoxShape(Vec3f(0.5f, 0.5f, 0.5f)),
        at,
        Quat.IDENTITY,
        motionType,
    )

    /**
     * The last pose reported: a body that settles stops being awake and stops being visited.
     *
     * Step counts here are two seconds of simulated time rather than four. That is ample for a rope
     * or a hinge to settle, and it keeps each test inside the browser runner's own per-test budget
     * -- wasmJs is single-threaded, and these are real Jolt worlds.
     */
    private fun PhysicsWorld.settle(body: BodyHandle, steps: Int, from: Vec3f): Vec3f {
        val last = Vec3f(from.x, from.y, from.z)
        repeat(steps) {
            step(1f / 60f)
            forEachBodyTransform { handle, position, _ ->
                if (handle == body) last.set(position.x, position.y, position.z)
            }
        }
        return last
    }

    @Test
    fun aHangingBodyStopsAtTheEndOfItsRope() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            val anchor = world.box(Vec3f(0f, 10f, 0f), MotionType.STATIC)
            val hanging = world.box(Vec3f(0f, 9f, 0f), MotionType.DYNAMIC)
            world.createConstraint(
                DistanceConstraint(
                    bodyA = anchor,
                    bodyB = hanging,
                    pointA = Vec3f(0f, 10f, 0f),
                    pointB = Vec3f(0f, 9f, 0f),
                    maxDistance = 2f,
                ),
            )

            val at = world.settle(hanging, steps = 120, from = Vec3f(0f, 9f, 0f))

            // Two metres of rope from an anchor at 10, so it hangs around 8 and no lower. Without
            // the constraint it simply falls forever.
            assertTrue(at.y > 7f, "the body fell past the end of its rope: y=${at.y}")
            assertTrue(at.y < 9.5f, "the body never fell at all: y=${at.y}")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aHingedBodySwingsAboutItsAxisAndNotAwayFromIt() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            val frame = world.box(Vec3f(0f, 10f, 0f), MotionType.STATIC)
            val door = world.box(Vec3f(1f, 10f, 0f), MotionType.DYNAMIC)
            world.createConstraint(
                HingeConstraint(
                    bodyA = frame,
                    bodyB = door,
                    point = Vec3f(0f, 10f, 0f),
                    axis = Vec3f(0f, 1f, 0f),
                ),
            )

            val at = world.settle(door, steps = 120, from = Vec3f(1f, 10f, 0f))

            // A vertical hinge takes the whole of gravity, so the door stays at its own height and
            // swings only in the horizontal plane. Falling is what an unconstrained body does.
            assertTrue(abs(at.y - 10f) < 0.5f, "the door fell instead of hanging on its hinge: $at")
            val radius = kotlin.math.sqrt(at.x * at.x + at.z * at.z)
            assertTrue(abs(radius - 1f) < 0.5f, "the door left its hinge: $at")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun destroyingAConstrainedBodyDoesNotCrashTheNextStep() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            val anchor = world.box(Vec3f(0f, 10f, 0f), MotionType.STATIC)
            val hanging = world.box(Vec3f(0f, 9f, 0f), MotionType.DYNAMIC)
            world.createConstraint(
                DistanceConstraint(anchor, hanging, Vec3f(0f, 10f, 0f), Vec3f(0f, 9f, 0f), maxDistance = 2f),
            )

            // The rule this whole feature is built around: Jolt does not detach the constraint, so
            // without the backend doing it the step below is a crash rather than a failure.
            world.destroyBody(hanging)
            repeat(10) { world.step(1f / 60f) }

            val survivor = world.box(Vec3f(5f, 5f, 0f), MotionType.DYNAMIC)
            val at = world.settle(survivor, steps = 30, from = Vec3f(5f, 5f, 0f))
            assertTrue(at.y < 5f, "the world stopped simulating after a constrained body was destroyed")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun destroyingAConstraintTwiceIsHarmless() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            val anchor = world.box(Vec3f(0f, 10f, 0f), MotionType.STATIC)
            val hanging = world.box(Vec3f(0f, 9f, 0f), MotionType.DYNAMIC)
            val rope = world.createConstraint(
                DistanceConstraint(anchor, hanging, Vec3f(0f, 10f, 0f), Vec3f(0f, 9f, 0f), maxDistance = 2f),
            )

            world.destroyConstraint(rope)
            // The second call is what a caller tearing down in its own order does, having already
            // destroyed a body that took this constraint with it.
            world.destroyConstraint(rope)
            world.destroyBody(hanging)
            repeat(10) { world.step(1f / 60f) }

            // And with the rope gone the body is free again, which proves the removal did something.
            val free = world.box(Vec3f(0f, 9f, 0f), MotionType.DYNAMIC)
            val at = world.settle(free, steps = 120, from = Vec3f(0f, 9f, 0f))
            assertTrue(at.y < 7f, "the body is still held by a rope that was removed: y=${at.y}")
        } finally {
            world.destroy()
        }
    }

    @Test
    fun aWorldWithLiveConstraintsTearsDownCleanly() = runTest {
        val world = createJoltPhysicsWorld()
        val anchor = world.box(Vec3f(0f, 10f, 0f), MotionType.STATIC)
        val hanging = world.box(Vec3f(0f, 9f, 0f), MotionType.DYNAMIC)
        world.createConstraint(
            DistanceConstraint(anchor, hanging, Vec3f(0f, 10f, 0f), Vec3f(0f, 9f, 0f), maxDistance = 2f),
        )

        // destroy() frees bodies, so it has to free their constraints first for the same reason
        // destroyBody does. Reaching the end of this test is the assertion.
        world.destroy()
    }
}
