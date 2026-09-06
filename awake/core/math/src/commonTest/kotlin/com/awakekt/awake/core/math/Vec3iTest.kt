/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class Vec3iTest {
    @Test
    fun mutatingContractReturnsSameInstance() {
        val v = Vec3i(1, 2, 3)
        val chained = v.set(2, 3, 4)
            .add(Vec3i(1, 1, 1))
            .sub(Vec3i(1, 2, 1))
            .scale(3)

        assertSame(v, chained)
        assertEquals(6, v.x)
        assertEquals(6, v.y)
        assertEquals(12, v.z)
    }

    @Test
    fun allocatingOperators() {
        val a = Vec3i(10, 20, 30)
        val b = Vec3i(5, 5, 5)

        val sum = a + b
        val diff = a - b
        val scaled = a * 2

        assertNotSame(a, sum)
        assertNotSame(a, diff)
        assertNotSame(a, scaled)

        assertEquals(15, sum.x)
        assertEquals(5, diff.x)
        assertEquals(20, scaled.x)
    }

    @Test
    fun manhattanAndSquaredDistances() {
        val a = Vec3i(0, 0, 0)
        val b = Vec3i(3, 4, 12)

        assertEquals(19, a.manhattanDistanceTo(b))
        assertEquals(169L, a.squaredDistanceTo(b))
    }

    @Test
    fun conversionsRoundTrip() {
        val v = Vec3i(10, 20, 30)
        val vf = v.toVec3()
        val vd = v.toVec3d()

        assertEquals(10f, vf.x)
        assertEquals(20.0, vd.y)

        assertEquals(v, vf.toVec3i())
        assertEquals(v, vd.toVec3i())
    }
}
