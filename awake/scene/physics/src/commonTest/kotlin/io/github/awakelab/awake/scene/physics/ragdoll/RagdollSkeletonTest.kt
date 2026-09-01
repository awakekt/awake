/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics.ragdoll

import io.github.awakelab.awake.core.animation.AnimationPose
import io.github.awakelab.awake.core.animation.Bone
import io.github.awakelab.awake.core.animation.Skeleton
import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.CapsuleShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.jolt.createJoltPhysicsWorld
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That a skeleton driven by a ragdoll ends up where the ragdoll's limbs actually are.
 *
 * The measurement is a round trip. A bone's pose is stored relative to its parent, so the only way
 * to check the conversion is to walk the posed skeleton back out to model space and compare against
 * the limb world poses that went in. Getting the conversion wrong -- writing world poses straight
 * into a bone, or measuring from the wrong parent -- puts the second bone somewhere else entirely,
 * which is what this catches.
 */
class RagdollSkeletonTest {

    /** A two-bone chain: a root, and a child offset a metre below it. */
    private fun chainSkeleton() = Skeleton(
        bones = listOf(
            Bone(Vec3f(0f, 3f, 0f), Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, listOf(1)),
            Bone(Vec3f(0f, -1f, 0f), Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList()),
        ),
        roots = listOf(0),
    )

    /** Two capsules joined at a ball socket, matching the chain above. */
    private suspend fun chainRagdoll(): Pair<PhysicsWorld, Ragdoll> {
        val world = createJoltPhysicsWorld()
        world.createBody(
            BoxShape(Vec3f(10f, 0.5f, 10f)),
            Vec3f(0f, -0.5f, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
        )
        // Both limbs start rotated, and the chain is set tumbling below. A parent bone whose
        // rotation stays near identity makes the parent-relative conversion invisible: writing a
        // model-space pose straight into a bone would look correct, because there would be nothing
        // to undo. Verified by mutation -- with the limbs merely falling, breaking the conversion
        // did not fail this test.
        val tilt = Quat.fromAxisAngle(Vec3f(0f, 0f, 1f), (PI / 3).toFloat())
        val limbs = listOf(
            RagdollLimb(CapsuleShape(halfHeight = 0.3f, radius = 0.15f), Vec3f(0f, 3f, 0f), tilt),
            RagdollLimb(CapsuleShape(halfHeight = 0.3f, radius = 0.15f), Vec3f(0f, 2f, 0f), tilt),
        )
        val joints = listOf(RagdollJoint(parent = 0, child = 1, anchor = Vec3f(0f, 2.5f, 0f)))
        val doll = Ragdoll(world, limbs, joints)
        doll.bodies.forEach { world.setAngularVelocity(it, Vec3f(3f, 2f, 1f)) }
        return world to doll
    }

    /**
     * Where [bone] ends up in model space, by composing it with its parent the way playback does.
     *
     * Deliberately not the production walk: this reproduces the composition from the pose's own
     * stored values, so a conversion that is self-consistently wrong still fails here.
     */
    private fun modelSpaceOf(pose: AnimationPose, parents: List<Int>, bone: Int): Pair<Vec3f, Quat> {
        var position = Vec3f(0f, 0f, 0f)
        var rotation = Quat.IDENTITY
        val chain = generateSequence(bone) { parents[it].takeIf { parent -> parent >= 0 } }.toList().reversed()
        chain.forEach { index ->
            val local = pose.boneTranslation(index)
            position = position.add(rotation.rotate(local))
            rotation = pose.boneRotation(index) * rotation
        }
        return position to rotation
    }

    private fun assertNear(expected: Vec3f, actual: Vec3f, what: String) {
        val slack = 0.001f
        assertTrue(
            abs(expected.x - actual.x) < slack &&
                abs(expected.y - actual.y) < slack &&
                abs(expected.z - actual.z) < slack,
            "$what: expected $expected but the posed skeleton put it at $actual",
        )
    }

    @Test
    fun everyBoundBoneLandsWhereItsLimbIs() = runTest {
        val (world, doll) = chainRagdoll()
        try {
            val skeleton = chainSkeleton()
            val pose = AnimationPose(skeleton)
            val binding = RagdollSkeleton(skeleton, intArrayOf(0, 1))
            // Long enough that the limbs have fallen and rotated, so the poses being compared are
            // not the ones both sides started from.
            repeat(120) { world.step(1f / 60f) }

            binding.apply(doll, pose)

            val limbs = MutableList(2) { Vec3f(0f, 0f, 0f) }
            doll.forEachLimb { index, position, _ -> limbs[index].set(position) }
            val parents = listOf(-1, 0)
            assertNear(limbs[0], modelSpaceOf(pose, parents, 0).first, "the root bone")
            assertNear(limbs[1], modelSpaceOf(pose, parents, 1).first, "the child bone")
        } finally {
            doll.dispose()
            world.destroy()
        }
    }

    @Test
    fun aModelTransformIsUndoneRatherThanAppliedTwice() = runTest {
        val (world, doll) = chainRagdoll()
        try {
            val skeleton = chainSkeleton()
            val pose = AnimationPose(skeleton)
            val binding = RagdollSkeleton(skeleton, intArrayOf(0, 1))
            repeat(120) { world.step(1f / 60f) }
            // A character standing away from the origin and facing sideways -- the case where
            // forgetting the model transform, or applying it the wrong way, is visible.
            val modelPosition = Vec3f(5f, 0f, -2f)
            val modelRotation = Quat.fromAxisAngle(Vec3f(0f, 1f, 0f), (PI / 3).toFloat())

            binding.apply(doll, pose, modelPosition, modelRotation)

            val limbs = MutableList(2) { Vec3f(0f, 0f, 0f) }
            doll.forEachLimb { index, position, _ -> limbs[index].set(position) }
            val parents = listOf(-1, 0)
            // The bones hold model-space poses, so drawing them puts the model transform back on.
            // That round trip has to land on the limb's world pose again.
            listOf(0, 1).forEach { bone ->
                val (local, _) = modelSpaceOf(pose, parents, bone)
                // Not modelPosition.add(...): Vec3f.add mutates its receiver, so that would
                // corrupt modelPosition for the next bone rather than compute this one's.
                val rotated = modelRotation.rotate(local)
                val drawn = Vec3f(
                    modelPosition.x + rotated.x,
                    modelPosition.y + rotated.y,
                    modelPosition.z + rotated.z,
                )
                assertNear(limbs[bone], drawn, "bone $bone drawn under the model transform")
            }
        } finally {
            doll.dispose()
            world.destroy()
        }
    }

    @Test
    fun anUnboundBoneKeepsWhateverThePoseHad() = runTest {
        val (world, doll) = chainRagdoll()
        try {
            val skeleton = chainSkeleton()
            val pose = AnimationPose(skeleton)
            // Only the root is driven, so the child stands for every finger and facial bone a
            // ragdoll has no opinion about.
            val binding = RagdollSkeleton(skeleton, intArrayOf(0, -1))
            // Copied, not the pose's own reference: comparing that against itself would pass even
            // if apply had overwritten it in place.
            val stored = pose.boneTranslation(1)
            val before = Vec3f(stored.x, stored.y, stored.z)
            repeat(120) { world.step(1f / 60f) }

            binding.apply(doll, pose)

            assertNear(before, pose.boneTranslation(1), "an unbound bone")
        } finally {
            doll.dispose()
            world.destroy()
        }
    }

    @Test
    fun aLimbBoundOutsideTheSkeletonIsRejected() {
        val skeleton = chainSkeleton()

        // Cheap to get wrong and expensive to debug: a stale mapping against a skeleton that has
        // since changed writes into whatever bone happens to sit at that index, or none.
        val failure = runCatching { RagdollSkeleton(skeleton, intArrayOf(0, 7)) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException, "binding a limb to bone 7 of 2 was accepted")
    }
}
