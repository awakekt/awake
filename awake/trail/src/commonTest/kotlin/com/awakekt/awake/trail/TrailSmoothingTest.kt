/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.trail

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TrailSmoothingTest {
    @Test
    fun theCurvePassesThroughItsKeysAndIsLinearOnEvenlySpacedOnes() {
        val p = listOf(Vec3f(0f, 0f, 0f), Vec3f(1f, 0f, 0f), Vec3f(2f, 0f, 0f), Vec3f(3f, 0f, 0f))
        val out = Vec3f(0f, 0f, 0f)
        assertNear(Vec3f(1f, 0f, 0f), catmullRom(p[0], p[1], p[2], p[3], 0f, out))
        assertNear(Vec3f(2f, 0f, 0f), catmullRom(p[0], p[1], p[2], p[3], 1f, out))
        assertNear(Vec3f(1.25f, 0f, 0f), catmullRom(p[0], p[1], p[2], p[3], 0.25f, out))
    }

    @Test
    fun theCurveBulgesTowardsItsNeighbours() {
        // A corner: from (0,0) up to (1,1) and across to (2,1). Halfway along the middle segment the
        // curve sits above the chord, pulled out towards the corner.
        val out = catmullRom(Vec3f(0f, 0f, 0f), Vec3f(0f, 1f, 0f), Vec3f(1f, 1f, 0f), Vec3f(1f, 0f, 0f), 0.5f, Vec3f(0f, 0f, 0f))
        assertNear(Vec3f(0.5f, 1.125f, 0f), out)
    }

    @Test
    fun keyPairsBecomeStepsBetweenKeysAndEndOnTheLastKey() {
        val strip = TrailStrip(16)
        val base = listOf(Vec3f(0f, 0f, 0f), Vec3f(1f, 0f, 0f), Vec3f(2f, 0f, 0f))
        val tip = listOf(Vec3f(0f, 1f, 0f), Vec3f(1f, 1f, 0f), Vec3f(2f, 1f, 0f))

        val pairs = strip.setSmoothed(base, tip, steps = 4, colorA = Color.White, colorB = Color.White)

        assertEquals(9, pairs)
        assertEquals(smoothedPairCount(3, 4), pairs)
        assertEquals(0, strip.firstChanged)
        assertEquals(9, strip.changedEnd)
        val position = TrailStrip.FORMAT.floatOffsetOf(VertexSemantic.Position)
        val stride = TrailStrip.FORMAT.strideFloats
        for (pair in 0 until pairs) {
            val a = pair * TrailStrip.FLOATS_PER_PAIR + position
            assertTrue(abs(strip.vertices[a] - pair * 0.25f) < EPSILON, "pair $pair's base is a quarter step along")
            assertTrue(abs(strip.vertices[a + stride + 1] - 1f) < EPSILON, "pair $pair's tip stays on the tip line")
        }
    }

    @Test
    fun smoothingThatDoesNotFitIsRefused() {
        val keys = List(5) { Vec3f(it.toFloat(), 0f, 0f) }
        assertFailsWith<IllegalArgumentException> { TrailStrip(8).setSmoothed(keys, keys, 2, Color.White, Color.White) }
        assertFailsWith<IllegalArgumentException> { TrailStrip(8).setSmoothed(keys, keys.drop(1), 1, Color.White, Color.White) }
        assertEquals(0, TrailStrip(8).setSmoothed(emptyList(), emptyList(), 3, Color.White, Color.White))
    }

    private fun assertNear(expected: Vec3f, actual: Vec3f) {
        assertTrue(
            abs(expected.x - actual.x) < EPSILON && abs(expected.y - actual.y) < EPSILON && abs(expected.z - actual.z) < EPSILON,
            "expected $expected, got $actual",
        )
    }

    private companion object {
        const val EPSILON = 1e-5f
    }
}
