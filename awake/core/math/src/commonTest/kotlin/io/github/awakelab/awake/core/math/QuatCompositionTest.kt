/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Which way round to multiply quaternions when composing a bone hierarchy.
 *
 * [Quat.times] is the Hamilton product with its operands swapped, so `a * b` reads "a then b" --
 * which means composing a child under its parent is `local * parent`, not the `parent * local` that
 * looks right and that every matrix API in this engine is spelled as.
 *
 * Worth a test of its own because getting it backwards is close to undetectable: a codebase that is
 * consistently wrong still round-trips, and the error only appears against something composed the
 * other way. It cost a real bug -- a ragdoll posed a skinned character through the swapped order,
 * and both its tests passed because they recomposed with the same swap.
 */
class QuatCompositionTest {

    private fun rotate(matrix: Mat4, point: Vec3f) = Vec3f(
        matrix.m00 * point.x + matrix.m01 * point.y + matrix.m02 * point.z,
        matrix.m10 * point.x + matrix.m11 * point.y + matrix.m12 * point.z,
        matrix.m20 * point.x + matrix.m21 * point.y + matrix.m22 * point.z,
    )

    private fun assertNear(expected: Vec3f, actual: Vec3f, what: String) {
        assertTrue(
            abs(expected.x - actual.x) < 1e-4f &&
                abs(expected.y - actual.y) < 1e-4f &&
                abs(expected.z - actual.z) < 1e-4f,
            "$what: expected $expected but got $actual",
        )
    }

    @Test
    fun composingAHierarchyIsLocalTimesParent() {
        val parent = Quat.fromAxisAngle(Vec3f(1f, 0f, 0f), (-PI / 2).toFloat())
        val local = Quat.fromAxisAngle(Vec3f(0f, 0f, 1f), (PI / 3).toFloat())
        val point = Vec3f(0.3f, 0.5f, -0.2f)

        // The answer playback itself computes, through the matrix path a joint palette uses.
        val truth = rotate(Mat4.multiplyColumnMajor(parent.toMat4(), local.toMat4()), point)

        assertNear(truth, (local * parent).rotate(point), "local * parent")
    }

    @Test
    fun theOrderThatLooksRightIsTheWrongOne() {
        val parent = Quat.fromAxisAngle(Vec3f(1f, 0f, 0f), (-PI / 2).toFloat())
        val local = Quat.fromAxisAngle(Vec3f(0f, 0f, 1f), (PI / 3).toFloat())
        val point = Vec3f(0.3f, 0.5f, -0.2f)
        val truth = rotate(Mat4.multiplyColumnMajor(parent.toMat4(), local.toMat4()), point)

        // The control, and the whole reason the test above is worth writing down: these two
        // rotations do not commute, so the wrong order is a real difference rather than a rounding
        // one -- and it is the order anybody reading `parent * local` would expect to be correct.
        val wrong = (parent * local).rotate(point)
        assertTrue(
            abs(truth.x - wrong.x) > 0.1f || abs(truth.y - wrong.y) > 0.1f || abs(truth.z - wrong.z) > 0.1f,
            "the two orders agreed, so this pins nothing: $truth vs $wrong",
        )
    }
}
