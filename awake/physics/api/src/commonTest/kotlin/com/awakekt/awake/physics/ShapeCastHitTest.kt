/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShapeCastHitTest {
    private fun hit(point: Vec3f = Vec3f(0f, 0f, 0f), normal: Vec3f = Vec3f(0f, 1f, 0f), fraction: Float = 0.5f) =
        ShapeCastHit(BodyHandle(1), point, normal, fraction)

    @Test
    fun finiteHitsAcceptBothFractionEndpoints() {
        assertEquals(0f, hit(fraction = 0f).fraction)
        assertEquals(1f, hit(fraction = 1f).fraction)
    }

    @Test
    fun nonFinitePointOrNormalIsRejectedInEveryAxis() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for (vector in listOf(Vec3f(value, 1f, 0f), Vec3f(0f, value, 0f), Vec3f(0f, 1f, value))) {
                assertTrue(assertFailsWith<IllegalArgumentException> { hit(point = vector) }.message!!.contains("point"))
                assertTrue(assertFailsWith<IllegalArgumentException> { hit(normal = vector) }.message!!.contains("normal"))
            }
        }
        assertFailsWith<IllegalArgumentException> { hit(normal = Vec3f(0f, 0f, 0f)) }
    }

    @Test
    fun invalidFractionAndCopiesAreRejected() {
        for (fraction in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.1f, 1.1f)) {
            assertTrue(assertFailsWith<IllegalArgumentException> { hit(fraction = fraction) }.message!!.contains("fraction"))
            assertFailsWith<IllegalArgumentException> { hit().copy(fraction = fraction) }
        }
    }
}
