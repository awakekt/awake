/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [Skin.jointTransform]: where a joint is under a palette, not how it moves a vertex. The skeleton is
 * a root 2 m along z with a hand 1 m above it, and the skin lists the hand first, so its joint order
 * is not the skeleton's bone order.
 */
class SkinJointTransformTest {
    private val skeleton = Skeleton(
        bones = listOf(
            Bone(Vec3f(0f, 0f, 2f), Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, listOf(1), "root"),
            Bone(Vec3f(0f, 1f, 0f), Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList(), "hand"),
        ),
        roots = listOf(0),
    )
    private val skin = Skin(joints = listOf(1, 0), inverseBindMatrices = listOf(translation(0f, -1f, -2f), translation(0f, 0f, -2f)))

    /** At the bind pose a palette entry has no translation to speak of, but the joint is still 1 m up and 2 m along z. */
    @Test
    fun atTheBindPoseTheJointIsWhereItWasBoundNotAtTheOrigin() {
        val palette = AnimationPose(skeleton).jointPalette(skin)

        val hand = assertNotNull(skin.jointTransform(palette, 0))

        assertEquals(listOf(0f, 0f, 0f), palette.copyOfRange(12, 15).toList(), "the palette entry itself")
        assertEquals(listOf(0f, 1f, 2f), listOf(hand.m03, hand.m13, hand.m23))
    }

    @Test
    fun afterAPoseChangeItIsTheJointsGlobalTransform() {
        val pose = AnimationPose(skeleton)
        val turn = Quat(0f, 0f, sin(QUARTER_TURN / 2f), cos(QUARTER_TURN / 2f))
        pose.setBoneTransform(0, Vec3f(1f, 0f, 2f), turn)

        val hand = assertNotNull(skin.jointTransform(pose.jointPalette(skin), 0))

        val expected = Mat4.multiplyColumnMajor(
            Mat4.fromTrs(Vec3f(1f, 0f, 2f), turn, Vec3f(1f, 1f, 1f)),
            Mat4.fromTrs(Vec3f(0f, 1f, 0f), Quat.IDENTITY, Vec3f(1f, 1f, 1f)),
        )
        val error = hand.data.indices.maxOf { abs(hand.data[it] - expected.data[it]) }
        assertTrue(error < 1e-6f, "max error $error")
        assertEquals(0f, hand.m03, 1e-6f, "turned a quarter about z, the hand is beside the root, not above it")
    }

    @Test
    fun aJointOutsideThePaletteOrWithASingularBindHasNoTransform() {
        val palette = AnimationPose(skeleton).jointPalette(skin)
        val collapsed = Skin(joints = listOf(1, 0), inverseBindMatrices = listOf(Mat4().apply { data.fill(0f) }, Mat4()))

        assertNull(skin.jointTransform(palette, 2))
        assertNull(skin.jointTransform(palette.copyOf(16), 1), "a palette too short for the joint")
        assertNull(collapsed.jointTransform(palette, 0))
    }

    private fun translation(x: Float, y: Float, z: Float) = Mat4().apply {
        m03 = x
        m13 = y
        m23 = z
    }

    private companion object {
        const val QUARTER_TURN = (PI / 2).toFloat()
    }
}
