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
import io.github.awakelab.awake.physics.CapsuleShape
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.jolt.createJoltPhysicsWorld
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * That a ragdoll built from a skeleton reproduces that skeleton's bind pose exactly.
 *
 * This is the claim the whole rig exists for. A capsule lies along the bone it stands for, and a
 * rig's bones point down whatever local axis their exporter chose -- so the body frame and the bone
 * frame differ, and applying an unmoved ragdoll has to put every bone back precisely where it was.
 * Anything less shears the mesh the instant a character goes limp.
 *
 * The skeleton here runs its bones down local **X**, like CesiumMan and most glTF rigs, which is
 * exactly the case a ragdoll built on Y-up capsules gets wrong.
 */
class RagdollRigTest {

    /** A three-bone chain running down local X, each bone a quarter-turn from the last. */
    private fun sidewaysSkeleton() = Skeleton(
        bones = listOf(
            Bone(
                Vec3f(0f, 1.5f, 0f),
                // The rig's own convention: the bone's local +Y points along world +X.
                Quat.fromAxisAngle(Vec3f(0f, 0f, 1f), -(PI / 2).toFloat()),
                Vec3f(1f, 1f, 1f),
                null,
                listOf(1),
            ),
            Bone(Vec3f(0.4f, 0f, 0f), Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, listOf(2)),
            Bone(Vec3f(0.4f, 0f, 0f), Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList()),
        ),
        roots = listOf(0),
    )

    private fun assertNear(expected: Vec3f, actual: Vec3f, what: String) {
        val slack = 0.002f
        assertTrue(
            abs(expected.x - actual.x) < slack &&
                abs(expected.y - actual.y) < slack &&
                abs(expected.z - actual.z) < slack,
            "$what: expected $expected but got $actual",
        )
    }

    /** Recomposes the posed skeleton back into model space, the way playback does. */
    private fun modelSpace(pose: AnimationPose, skeleton: Skeleton): Pair<List<Vec3f>, List<Quat>> {
        val positions = MutableList(skeleton.bones.size) { Vec3f(0f, 0f, 0f) }
        val rotations = MutableList(skeleton.bones.size) { Quat.IDENTITY }
        fun visit(bone: Int, parentPosition: Vec3f, parentRotation: Quat) {
            val turned = parentRotation.rotate(pose.boneTranslation(bone))
            positions[bone] = Vec3f(
                parentPosition.x + turned.x,
                parentPosition.y + turned.y,
                parentPosition.z + turned.z,
            )
            rotations[bone] = pose.boneRotation(bone) * parentRotation
            skeleton.bones[bone].children.forEach { visit(it, positions[bone], rotations[bone]) }
        }
        skeleton.roots.forEach { visit(it, Vec3f(0f, 0f, 0f), Quat.IDENTITY) }
        return positions to rotations
    }

    private suspend fun rigged(): Triple<PhysicsWorld, Ragdoll, RagdollRig> {
        val skeleton = sidewaysSkeleton()
        val rig = ragdollFromSkeleton(skeleton, bones = intArrayOf(0, 1, 2))
        val world = createJoltPhysicsWorld()
        return Triple(world, Ragdoll(world, rig.limbs, rig.joints), rig)
    }

    @Test
    fun anUnmovedRagdollReproducesTheBindPose() = runTest {
        val skeleton = sidewaysSkeleton()
        val (world, doll, rig) = rigged()
        try {
            val pose = AnimationPose(skeleton)
            val binding = RagdollSkeleton(skeleton, rig)

            // Not stepped: the bodies are exactly where the rig put them, so the answer is known
            // rather than simulated, and any error is the frame conversion rather than physics.
            binding.apply(doll, pose)

            val bind = skeleton.bindPose()
            val (positions, rotations) = modelSpace(pose, skeleton)
            skeleton.bones.indices.forEach { bone ->
                assertNear(bind.position[bone], positions[bone], "bone $bone")
                // Quaternion sign is not unique, so compare what the rotation does rather than
                // its components: a rotation and its negation are the same rotation.
                val probe = Vec3f(0.3f, 0.5f, -0.2f)
                assertNear(
                    bind.rotation[bone].rotate(probe),
                    rotations[bone].rotate(probe),
                    "bone $bone's orientation",
                )
            }
        } finally {
            doll.dispose()
            world.destroy()
        }
    }

    @Test
    fun withoutTheOffsetsTheSameRigDoesNotReproduceTheBindPose() = runTest {
        val skeleton = sidewaysSkeleton()
        val (world, doll, rig) = rigged()
        try {
            val pose = AnimationPose(skeleton)
            // The control for the test above. Same rig, same bodies, offsets discarded -- which is
            // what binding a generic ragdoll to somebody else's rig amounts to.
            val binding = RagdollSkeleton(skeleton, rig.boneForLimb)

            binding.apply(doll, pose)

            val bind = skeleton.bindPose()
            val (positions, _) = modelSpace(pose, skeleton)
            val worst = skeleton.bones.indices.maxOf { bone ->
                val expected = bind.position[bone]
                val actual = positions[bone]
                maxOf(abs(expected.x - actual.x), abs(expected.y - actual.y), abs(expected.z - actual.z))
            }
            assertTrue(worst > 0.05f, "dropping the offsets changed nothing, so they measure nothing")
        } finally {
            doll.dispose()
            world.destroy()
        }
    }

    @Test
    fun eachCapsuleSpansTheBoneItStandsFor() {
        val skeleton = sidewaysSkeleton()

        val rig = ragdollFromSkeleton(skeleton, bones = intArrayOf(0, 1, 2), radius = 0.05f)

        // The first two bones are 0.4 apart, so their capsules span that; the last has nothing
        // below it and falls back to the stub length.
        val spans = rig.limbs.map { (it.shape as CapsuleShape).let { c -> (c.halfHeight + c.radius) * 2f } }
        assertTrue(abs(spans[0] - 0.4f) < 0.01f, "the first limb should span 0.4 but spans ${spans[0]}")
        assertTrue(abs(spans[1] - 0.4f) < 0.01f, "the second limb should span 0.4 but spans ${spans[1]}")
        assertTrue(spans[2] < 0.3f, "a tip limb should be a stub but spans ${spans[2]}")
    }

    @Test
    fun jointsFollowTheBoneHierarchy() {
        val skeleton = sidewaysSkeleton()

        val rig = ragdollFromSkeleton(skeleton, bones = intArrayOf(0, 1, 2))

        // Two joints for three bones in a chain, each pinning a limb to the one above it.
        assertEquals(2, rig.joints.size)
        assertEquals(0, rig.joints[0].parent)
        assertEquals(1, rig.joints[0].child)
        assertEquals(1, rig.joints[1].parent)
        assertEquals(2, rig.joints[1].child)
    }

    @Test
    fun aBoneOutsideTheSkeletonIsRejected() {
        val skeleton = sidewaysSkeleton()

        assertFailsWith<IllegalArgumentException> {
            ragdollFromSkeleton(skeleton, bones = intArrayOf(0, 9))
        }
    }
}
