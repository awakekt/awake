/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Spline3fTest {

    @Test
    fun passesThroughAllControlPointsAndStartsAndEndsCleanly() {
        val points = listOf(
            Vec3f(0f, 0f, 0f),
            Vec3f(4f, 2f, -3f),
            Vec3f(10f, 1f, -5f),
            Vec3f(14f, 4f, 2f),
        )
        val spline = ArcLengthSpline3f(points)
        val out = Vec3f()

        spline.samplePosition(0f, out)
        assertVecClose(points.first(), out, 1e-4f)

        spline.samplePosition(1f, out)
        assertVecClose(points.last(), out, 1e-4f)

        // Segment endpoints pass through interior control points.
        for (i in 0 until spline.segmentCount) {
            spline.sampleSegment(i, 0f, out)
            assertVecClose(points[i], out, 1e-4f)
            spline.sampleSegment(i, 1f, out)
            assertVecClose(points[i + 1], out, 1e-4f)
        }
    }

    @Test
    fun arcLengthSamplingMaintainsConstantWorldSpeedAcrossUnevenSpacing() {
        // Deliberately uneven control point spacing: 1 unit vs 15 units along X.
        val points = listOf(
            Vec3f(0f, 0f, 0f),
            Vec3f(1f, 0f, 0f),
            Vec3f(16f, 0f, 0f),
            Vec3f(18f, 0f, 0f),
        )
        val spline = ArcLengthSpline3f(points, subdivisionsPerSegment = 32)
        assertEquals(18f, spline.totalLength, 0.05f)

        val prev = Vec3f()
        val curr = Vec3f()
        val steps = 18
        spline.samplePosition(0f, prev)
        val expectedStep = spline.totalLength / steps
        for (i in 1..steps) {
            spline.samplePosition(i.toFloat() / steps, curr)
            val dist = prev.distanceTo(curr)
            assertTrue(
                abs(dist - expectedStep) < 0.05f,
                "Step $i distance $dist drifted from expected constant step $expectedStep",
            )
            prev.set(curr)
        }
    }

    @Test
    fun closedSplineWrapsSmoothlyAndProducesUnitTangents() {
        val loop = listOf(
            Vec3f(10f, 0f, 0f),
            Vec3f(0f, 0f, 10f),
            Vec3f(-10f, 0f, 0f),
            Vec3f(0f, 0f, -10f),
        )
        val spline = ArcLengthSpline3f(loop, closed = true)
        val start = Vec3f()
        val wrapped = Vec3f()
        val tangent = Vec3f()

        spline.samplePosition(0f, start)
        spline.samplePosition(1f, wrapped)
        assertVecClose(start, wrapped, 1e-3f)

        spline.samplePosition(0.25f, start)
        spline.samplePosition(1.25f, wrapped)
        assertVecClose(start, wrapped, 1e-3f)

        spline.sampleTangent(0.5f, tangent)
        assertEquals(1f, tangent.length3(), 1e-3f)
    }

    @Test
    fun rejectsFewerThanTwoPoints() {
        assertFailsWith<IllegalArgumentException> {
            ArcLengthSpline3f(listOf(Vec3f(0f, 0f, 0f)))
        }
    }

    private fun assertVecClose(expected: Vec3f, actual: Vec3f, tol: Float) {
        assertEquals(expected.x, actual.x, tol)
        assertEquals(expected.y, actual.y, tol)
        assertEquals(expected.z, actual.z, tol)
    }
}
