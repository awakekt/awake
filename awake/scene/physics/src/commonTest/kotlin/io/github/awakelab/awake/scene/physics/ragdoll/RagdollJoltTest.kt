/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics.ragdoll

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BallSocketConstraint
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.jolt.createJoltPhysicsWorld
import kotlin.math.sqrt
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That a ragdoll collapses and stays in one piece.
 *
 * Those two are the whole claim, and they fail in opposite directions. A ragdoll whose joints do
 * not hold comes apart into a spray of limbs; one whose joints hold too well lands as a statue.
 * Both look like physics is broken, and neither is visible from a single frame.
 *
 * Built out of bodies and constraints rather than Jolt's own `Ragdoll` -- JoltC exposes none -- so
 * this runs against a real Jolt world to prove the composition actually behaves like one.
 */
class RagdollJoltTest {

    private var physics: PhysicsWorld? = null
    private var ragdoll: Ragdoll? = null

    @AfterTest
    fun tearDown() {
        ragdoll?.dispose()
        ragdoll = null
        physics?.destroy()
        physics = null
    }

    private suspend fun droppedRagdoll(height: Float = 1.8f): Pair<PhysicsWorld, Ragdoll> {
        val world = createJoltPhysicsWorld()
        physics = world
        world.createBody(
            BoxShape(Vec3f(10f, 0.5f, 10f)),
            Vec3f(0f, -0.5f, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
        )
        val (limbs, joints) = humanoidRagdoll(height = height, origin = Vec3f(0f, 1f, 0f))
        val doll = Ragdoll(world, limbs, joints).also { ragdoll = it }
        return world to doll
    }

    /** The last pose each limb reported: a settled limb stops being awake and stops being visited. */
    private fun Ragdoll.settle(world: PhysicsWorld, steps: Int): MutableList<Vec3f> {
        val poses = MutableList(bodies.size) { Vec3f(0f, 0f, 0f) }
        forEachLimb { index, position, _ -> poses[index].set(position) }
        repeat(steps) {
            world.step(1f / 60f)
            forEachLimb { index, position, _ -> poses[index].set(position) }
        }
        return poses
    }

    private companion object {
        /**
         * Long enough for the slowest backend, which is three times the fastest.
         *
         * Measured, all eleven limbs asleep by: desktop 300 steps, wasm 600, the iOS simulator 900.
         * They run three different Jolt builds against the same scene and a chain of limited
         * six-DOF joints converges at visibly different rates in each, so a threshold set from
         * whichever one is to hand is a test that fails on somebody else's machine.
         */
        const val SETTLE_STEPS = 1200
    }

    private fun distance(a: Vec3f, b: Vec3f): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    @Test
    fun aDroppedRagdollCollapsesInsteadOfLandingAsAStatue() = runTest {
        val (world, doll) = droppedRagdoll()
        val start = MutableList(doll.bodies.size) { Vec3f(0f, 0f, 0f) }
        doll.forEachLimb { index, position, _ -> start[index].set(position) }

        val ended = doll.settle(world, steps = 300)

        // A rigid figure lands and keeps its shape, so every limb falls the same distance. A
        // collapsing one does not: the head and the feet end up at very different heights.
        val headDrop = start[HumanoidLimb.Head.index].y - ended[HumanoidLimb.Head.index].y
        val shinDrop = start[HumanoidLimb.LeftShin.index].y - ended[HumanoidLimb.LeftShin.index].y
        assertTrue(headDrop > shinDrop + 0.2f, "the ragdoll landed rigid: head fell $headDrop, shin $shinDrop")
    }

    @Test
    fun theLimbsStayAttachedToEachOther() = runTest {
        val (world, doll) = droppedRagdoll()
        val start = MutableList(doll.bodies.size) { Vec3f(0f, 0f, 0f) }
        doll.forEachLimb { index, position, _ -> start[index].set(position) }
        val startSpread = distance(start[HumanoidLimb.Head.index], start[HumanoidLimb.LeftShin.index])

        val ended = doll.settle(world, steps = 300)

        // The opposite failure: joints that do not hold let the figure come apart, and the pieces
        // scatter. Head to shin is the longest chain in the body, so it is where that shows first.
        val endSpread = distance(ended[HumanoidLimb.Head.index], ended[HumanoidLimb.LeftShin.index])
        assertTrue(
            endSpread < startSpread * 1.5f,
            "the ragdoll came apart: head-to-shin went from $startSpread to $endSpread",
        )
    }

    @Test
    fun itComesToRestRatherThanTwitchingForever() = runTest {
        val (world, doll) = droppedRagdoll()

        doll.settle(world, steps = SETTLE_STEPS)

        // Joints that fight each other can feed energy back and forth and never settle, which reads
        // as a corpse that will not stop moving.
        assertTrue(doll.isAtRest, "the ragdoll never settled")
    }

    @Test
    fun everyLimbEndsAboveTheFloorItLandedOn() = runTest {
        val (world, doll) = droppedRagdoll()

        val ended = doll.settle(world, steps = 300)

        // Nothing tunnelled or got squeezed through the ground by its own joints, which is the
        // failure a chain of constraints produces when it is over-constrained.
        ended.forEachIndexed { index, position ->
            assertTrue(position.y > -0.5f, "limb $index ended below the floor at ${position.y}")
        }
    }

    @Test
    fun everyBallJointInTheHumanoidIsLimited() = runTest {
        val (_, joints) = humanoidRagdoll()

        // No physics here on purpose: that a swing cone holds is proven against all four backends
        // in the backend's own JoltBallSocketTest, so all this has to pin is that the figure asks
        // for one. An unlimited shoulder is the difference between a corpse and a bag of parts, and
        // it is a one-word regression -- a default reinstated by a refactor looks like nothing.
        val ballJoints = joints.filter { it.hingeAxis == null }
        assertTrue(ballJoints.isNotEmpty(), "the humanoid has no ball joints, so this checks nothing")
        ballJoints.forEach { joint ->
            assertTrue(
                joint.swingLimit < BallSocketConstraint.FREE_SWING,
                "the joint from limb ${joint.parent} to ${joint.child} swings without limit",
            )
            assertTrue(
                joint.twistLimit != null,
                "the joint from limb ${joint.parent} to ${joint.child} twists without limit",
            )
        }
    }

    @Test
    fun disposingTakesEveryBodyWithIt() = runTest {
        val (world, doll) = droppedRagdoll()
        doll.settle(world, steps = 60)

        doll.dispose()
        ragdoll = null
        repeat(30) { world.step(1f / 60f) }

        // Constraints are destroyed before the bodies they join, so this both leaves nothing behind
        // and does not crash the next step -- Jolt does not detach a constraint on its own.
        var remaining = 0
        world.forEachBodyTransform { handle, _, _ -> if (handle in doll.bodies) remaining++ }
        assertTrue(remaining == 0, "$remaining limbs outlived the ragdoll")
    }
}
