/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.core.animation.AnimationPose
import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.showcase.examples.ShowcasePhysics
import com.awakekt.awake.showcase.examples.SkinnedExampleDriver
import com.awakekt.awake.showcase.examples.SkinnedRagdollExampleDriver
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That a ragdoll built from CesiumMan's own rig poses CesiumMan, rather than distorting it.
 *
 * The claim is specific and it is the reason the rig exists: before anything is simulated, the
 * posed skeleton has to match the bind pose it was built from, lifted by the height it was dropped
 * from and nothing else. A ragdoll bound to a rig whose bones point down a different axis than its
 * capsules produces a figure that is inside-out on the very first frame, and this is where that
 * shows.
 */
class SkinnedRagdollShowcaseTest {

    @AfterTest
    fun tearDown() {
        SkinnedRagdollExampleDriver.detach()
        ShowcasePhysics.world?.destroy()
        ShowcasePhysics.world = null
    }

    /**
     * Model-space bone positions, composed exactly the way `AnimationPose.jointPalette` does.
     *
     * Matrices rather than quaternions, and a baked matrix taken verbatim when a bone has one:
     * CesiumMan's root is a baked Z-up matrix, and a walker that drops it puts the whole figure a
     * quarter-turn out -- which looks like the ragdoll being wrong rather than the test.
     */
    private fun modelSpace(skeleton: Skeleton, local: (Int) -> Mat4): List<Vec3f> {
        val positions = MutableList(skeleton.bones.size) { Vec3f(0f, 0f, 0f) }
        fun visit(bone: Int, parent: Mat4) {
            val global = Mat4.multiplyColumnMajor(parent, local(bone))
            positions[bone] = Vec3f(global.m03, global.m13, global.m23)
            skeleton.bones[bone].children.forEach { visit(it, global) }
        }
        skeleton.roots.forEach { visit(it, Mat4()) }
        return positions
    }

    private fun bindPositions(skeleton: Skeleton) = modelSpace(skeleton) { bone ->
        skeleton.bones[bone].localTransform()
    }

    private fun posedPositions(skeleton: Skeleton, pose: AnimationPose) = modelSpace(skeleton) { bone ->
        skeleton.bones[bone].matrix
            ?: Mat4.fromTrs(pose.boneTranslation(bone), pose.boneRotation(bone), Vec3f(1f, 1f, 1f))
    }

    @Test
    fun anUnsimulatedFigureStandsInItsOwnBindPose() = runTest {
        SkinnedExampleDriver.preload()
        ShowcasePhysics.world = createJoltPhysicsWorld()
        SkinnedRagdollExampleDriver.spawnForTest()
        val skeleton = SkinnedExampleDriver.skeleton()

        val pose = assertNotNull(SkinnedRagdollExampleDriver.poseForTest())

        // Every driven bone sits where the bind pose put it, lifted by the drop height. Compared
        // as offsets from the pelvis, so this measures the figure's shape rather than where it was
        // dropped -- which is the part a wrong frame conversion destroys.
        val bind = bindPositions(skeleton)
        val posed = posedPositions(skeleton, pose)
        val pelvis = PELVIS_BONE
        DRIVEN_BONES.forEach { bone ->
            val expected = Vec3f(
                bind[bone].x - bind[pelvis].x,
                bind[bone].y - bind[pelvis].y,
                bind[bone].z - bind[pelvis].z,
            )
            val actual = Vec3f(
                posed[bone].x - posed[pelvis].x,
                posed[bone].y - posed[pelvis].y,
                posed[bone].z - posed[pelvis].z,
            )
            assertTrue(
                abs(expected.x - actual.x) < SLACK &&
                    abs(expected.y - actual.y) < SLACK &&
                    abs(expected.z - actual.z) < SLACK,
                "bone $bone sits at $actual from the pelvis but the bind pose puts it at $expected",
            )
        }
    }

    @Test
    fun theFigureFallsOnceItIsSimulated() = runTest {
        SkinnedExampleDriver.preload()
        ShowcasePhysics.world = createJoltPhysicsWorld()
        SkinnedRagdollExampleDriver.spawnForTest()
        val skeleton = SkinnedExampleDriver.skeleton()
        val before = posedPositions(skeleton, assertNotNull(SkinnedRagdollExampleDriver.poseForTest()))

        // Above the floor to begin with. Without this the test passes on a figure that spawned
        // inside the ground and was shoved out of it, which is motion but not a fall.
        DRIVEN_BONES.forEach { bone ->
            assertTrue(before[bone].y > 0f, "bone $bone spawned at ${before[bone].y}, inside the floor")
        }

        repeat(90) { ShowcasePhysics.world?.step(1f / 60f) }

        val after = posedPositions(skeleton, assertNotNull(SkinnedRagdollExampleDriver.poseForTest()))
        // The head is the highest thing on a standing figure and the lowest thing on a collapsed
        // one, so it is where a ragdoll that never actually moved shows up.
        val bind = bindPositions(skeleton)
        // The head is the highest thing on a standing figure and the lowest thing on a collapsed
        // one, so it is where a ragdoll that never actually moved shows up.
        assertTrue(
            after[NECK_BONE].y < before[NECK_BONE].y - 0.2f,
            "the figure did not collapse: the neck went from ${before[NECK_BONE].y} to ${after[NECK_BONE].y}",
        )
    }

    private companion object {
        const val SLACK = 0.01f
        const val PELVIS_BONE = 3
        const val NECK_BONE = 20
        val DRIVEN_BONES = listOf(3, 12, 13, 20, 14, 15, 17, 18, 4, 5, 8, 9)
    }
}
