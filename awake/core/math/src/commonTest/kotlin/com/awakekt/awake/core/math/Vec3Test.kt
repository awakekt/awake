/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Numeric behaviour of [Vec3f]. The mutate-vs-allocate *contract* (which methods return `this`,
 * which allocate) is already pinned by `Vec3MutabilityTest` -- this class only asserts the
 * arithmetic those methods perform.
 */
class Vec3Test {

    @Test
    fun dotProductOfKnownVectors() {
        assertEquals(32f, Vec3f(1f, 2f, 3f).dot(Vec3f(4f, 5f, 6f)), TOLERANCE)
    }

    @Test
    fun dotProductOfPerpendicularVectorsIsZero() {
        assertEquals(0f, Vec3f(1f, 0f, 0f).dot(Vec3f(0f, 1f, 0f)), TOLERANCE)
        assertEquals(0f, Vec3f(0f, 3f, 0f).dot(Vec3f(0f, 0f, 7f)), TOLERANCE)
    }

    @Test
    fun dotProductIsCommutative() {
        val a = Vec3f(1.5f, -2f, 0.25f)
        val b = Vec3f(-4f, 0.5f, 8f)

        assertEquals(a.dot(b), b.dot(a), TOLERANCE)
    }

    @Test
    fun dotOfAVectorWithItselfIsItsSquaredLength() {
        val v = Vec3f(3f, 4f, 12f)

        assertEquals(v.length3() * v.length3(), v.dot(v), TOLERANCE)
    }

    @Test
    fun crossProductFollowsTheStandardBasisIdentities() {
        val x = Vec3f(1f, 0f, 0f)
        val y = Vec3f(0f, 1f, 0f)
        val z = Vec3f(0f, 0f, 1f)

        assertVec3(z, x.cross(y))
        assertVec3(x, y.cross(z))
        assertVec3(y, z.cross(x))
    }

    @Test
    fun crossProductIsAntiCommutative() {
        val a = Vec3f(1f, 2f, 3f)
        val b = Vec3f(4f, 5f, 6f)

        val ab = a.cross(b)
        val ba = b.cross(a)

        assertVec3(Vec3f(-3f, 6f, -3f), ab)
        assertVec3(Vec3f(-ab.x, -ab.y, -ab.z), ba)
    }

    @Test
    fun crossProductOfParallelVectorsIsZero() {
        val a = Vec3f(2f, -4f, 6f)
        val parallel = Vec3f(1f, -2f, 3f)

        assertVec3(Vec3f(0f, 0f, 0f), a.cross(parallel))
        assertVec3(Vec3f(0f, 0f, 0f), a.cross(a))
    }

    @Test
    fun crossProductIsPerpendicularToBothInputs() {
        val a = Vec3f(1f, 2f, 3f)
        val b = Vec3f(-4f, 5f, 0.5f)

        val n = a.cross(b)

        assertEquals(0f, n.dot(a), TOLERANCE)
        assertEquals(0f, n.dot(b), TOLERANCE)
    }

    @Test
    fun length3OfAPythagoreanTriple() {
        assertEquals(5f, Vec3f(3f, 4f, 0f).length3(), TOLERANCE)
        assertEquals(13f, Vec3f(3f, 4f, 12f).length3(), TOLERANCE)
        assertEquals(0f, Vec3f(0f, 0f, 0f).length3(), TOLERANCE)
    }

    @Test
    fun length3IgnoresSign() {
        assertEquals(Vec3f(3f, 4f, 12f).length3(), Vec3f(-3f, -4f, -12f).length3(), TOLERANCE)
    }

    @Test
    fun plusMinusAndTimesProduceComponentwiseResults() {
        val a = Vec3f(1f, 2f, 3f)
        val b = Vec3f(0.5f, -1f, 10f)

        assertVec3(Vec3f(1.5f, 1f, 13f), a + b)
        assertVec3(Vec3f(0.5f, 3f, -7f), a - b)
        assertVec3(Vec3f(2f, 4f, 6f), a * 2f)
        assertVec3(Vec3f(-1f, -2f, -3f), a * -1f)
    }

    @Test
    fun minusOfAVectorWithItselfIsZero() {
        val a = Vec3f(7f, -8f, 9f)

        assertVec3(Vec3f(0f, 0f, 0f), a - a)
    }

    @Test
    fun lerpAtZeroLeavesTheReceiverWhereItWas() {
        val v = Vec3f(1f, 2f, 3f)

        v.lerp(Vec3f(11f, 22f, 33f), 0f)

        assertVec3(Vec3f(1f, 2f, 3f), v)
    }

    @Test
    fun lerpAtOneLandsExactlyOnTheTarget() {
        val v = Vec3f(1f, 2f, 3f)

        v.lerp(Vec3f(11f, 22f, 33f), 1f)

        assertVec3(Vec3f(11f, 22f, 33f), v)
    }

    @Test
    fun lerpAtOneHalfLandsOnTheMidpoint() {
        val v = Vec3f(0f, 0f, 0f)

        v.lerp(Vec3f(10f, -4f, 3f), 0.5f)

        assertVec3(Vec3f(5f, -2f, 1.5f), v)
    }

    @Test
    fun repeatedLerpConvergesTowardTheTargetWithoutOvershooting() {
        val v = Vec3f(0f, 0f, 0f)
        val target = Vec3f(10f, 0f, 0f)

        repeat(4) { v.lerp(target, 0.5f) }

        // 0 -> 5 -> 7.5 -> 8.75 -> 9.375, always short of the target.
        assertEquals(9.375f, v.x, TOLERANCE)
        assertTrue(v.x < target.x, "lerp must never overshoot with factor <= 1")
    }

    @Test
    fun setReplacesEveryComponent() {
        val v = Vec3f(1f, 2f, 3f)

        v.set(-4f, 0f, 0.5f)

        assertVec3(Vec3f(-4f, 0f, 0.5f), v)
    }

    @Test
    fun setFromAnotherVectorCopiesValuesRatherThanAliasing() {
        val source = Vec3f(1f, 2f, 3f)
        val target = Vec3f(0f, 0f, 0f).set(source)

        source.set(9f, 9f, 9f)

        assertVec3(Vec3f(1f, 2f, 3f), target)
    }

    @Test
    fun addSubAndScaleMatchTheirAllocatingOperators() {
        val a = Vec3f(1f, 2f, 3f)
        val b = Vec3f(0.5f, -1f, 10f)

        assertVec3(a + b, Vec3f(1f, 2f, 3f).add(b))
        assertVec3(a - b, Vec3f(1f, 2f, 3f).sub(b))
        assertVec3(a * 3f, Vec3f(1f, 2f, 3f).scale(3f))
    }

    @Test
    fun scaleByZeroCollapsesToTheOrigin() {
        assertVec3(Vec3f(0f, 0f, 0f), Vec3f(4f, -5f, 6f).scale(0f))
    }

    @Test
    fun normalizedPreservesDirectionAndProducesUnitLength() {
        val v = Vec3f(0f, 0f, -7f)

        val unit = v.normalized()

        assertVec3(Vec3f(0f, 0f, -1f), unit)
        assertEquals(1f, unit.length3(), TOLERANCE)
    }

    @Test
    fun upIsTheWorldUpAxis() {
        assertVec3(Vec3f(0f, 1f, 0f), Vec3f.UP)
    }

    private fun assertVec3(
        expected: com.awakekt.awake.core.math.Vec3f,
        actual: com.awakekt.awake.core.math.Vec3f,
    ) {
        assertEquals(expected.x, actual.x, TOLERANCE, "x")
        assertEquals(expected.y, actual.y, TOLERANCE, "y")
        assertEquals(expected.z, actual.z, TOLERANCE, "z")
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
