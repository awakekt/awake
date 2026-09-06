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

class Vec3dTest {
    @Test
    fun mutatingContractReturnsSameInstance() {
        val v = Vec3d(1.0, 2.0, 3.0)
        val chained = v.set(2.0, 3.0, 4.0)
            .add(Vec3d(1.0, 1.0, 1.0))
            .sub(Vec3d(0.5, 0.5, 0.5))
            .scale(2.0)
            .lerp(Vec3d(10.0, 10.0, 10.0), 0.5)

        assertSame(v, chained)
        assertEquals(7.5, v.x, 1e-9)
        assertEquals(8.5, v.y, 1e-9)
        assertEquals(9.5, v.z, 1e-9)
    }

    @Test
    fun allocatingContractProducesNewInstances() {
        val v1 = Vec3d(1.0, 2.0, 3.0)
        val v2 = Vec3d(4.0, 5.0, 6.0)

        val sum = v1 + v2
        val diff = v1 - v2
        val scaled = v1 * 2.0
        val norm = v1.normalized()

        assertNotSame(v1, sum)
        assertNotSame(v1, diff)
        assertNotSame(v1, scaled)
        assertNotSame(v1, norm)

        // v1 remains untouched
        assertEquals(1.0, v1.x)
        assertEquals(2.0, v1.y)
        assertEquals(3.0, v1.z)
    }

    @Test
    fun normalizeHandlesZeroLengthGracefully() {
        val zero = Vec3d(0.0, 0.0, 0.0)
        zero.normalize()
        assertEquals(0.0, zero.x)
        assertEquals(0.0, zero.y)
        assertEquals(0.0, zero.z)
    }

    @Test
    fun precisionRetentionForLargeCoordinates() {
        // High coordinates (> 10 km from origin in mm: > 10,000,000)
        val bigX = 15_000_000.0001
        val v = Vec3d(bigX, 0.0, 0.0)
        v.add(Vec3d(0.0002, 0.0, 0.0))
        assertEquals(15_000_000.0003, v.x, 1e-9)
    }

    @Test
    fun conversionRoundTrips() {
        val f = Vec3f(1.5f, 2.5f, 3.5f)
        val d = f.toVec3d()
        assertEquals(1.5, d.x, 1e-6)
        assertEquals(2.5, d.y, 1e-6)
        assertEquals(3.5, d.z, 1e-6)

        val back = d.toVec3()
        assertEquals(f.x, back.x)
        assertEquals(f.y, back.y)
        assertEquals(f.z, back.z)
    }

    @Test
    fun dotAndCrossProducts() {
        val a = Vec3d(1.0, 0.0, 0.0)
        val b = Vec3d(0.0, 1.0, 0.0)
        val c = a.cross(b)

        assertEquals(0.0, a.dot(b))
        assertEquals(0.0, c.x, 1e-9)
        assertEquals(0.0, c.y, 1e-9)
        assertEquals(1.0, c.z, 1e-9)

        val out = Vec3d()
        a.cross(b, out)
        assertSame(out, a.cross(b, out))
        assertEquals(1.0, out.z, 1e-9)
    }
}
