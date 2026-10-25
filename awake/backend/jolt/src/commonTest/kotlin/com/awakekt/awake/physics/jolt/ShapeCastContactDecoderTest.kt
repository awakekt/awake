/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogRecord
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.InvalidShapeCastResultException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShapeCastContactDecoderTest {
    private fun hit(axis: Vec3f, fraction: Float = 0.5f, to: Vec3f = Vec3f(2f, 0f, 0f), decoder: ShapeCastContactDecoder = ShapeCastContactDecoder()) =
        decoder.decode(BodyHandle(1), Vec3f(0f, 0f, 0f), axis.x, axis.y, axis.z, fraction, Vec3f(0f, 0f, 0f), to)

    @Test
    fun zeroNearZeroAndNonFiniteAxesBlockAtStartAgainstTheCast() {
        for (axis in listOf(Vec3f(0f, 0f, 0f), Vec3f(1e-20f, 0f, 0f), Vec3f(Float.NaN, 0f, 0f), Vec3f(Float.POSITIVE_INFINITY, 0f, 0f))) {
            val contact = hit(axis)
            assertEquals(0f, contact.fraction)
            assertEquals(-1f, contact.normal.x)
            assertEquals(1f, contact.normal.length3(), 1e-6f)
        }
        val downward = hit(Vec3f(0f, 0f, 0f), to = Vec3f(0f, -0.1f, 0f))
        assertEquals(1f, downward.normal.y)
        assertEquals(0f, downward.fraction)
    }

    @Test
    fun largeFiniteAxesNormalizeWithoutOverflowAndOnlyTinyFractionsClamp() {
        val contact = hit(Vec3f(Float.MAX_VALUE, Float.MAX_VALUE, 0f))
        assertTrue(contact.normal.x.isFinite())
        assertEquals(1f, contact.normal.length3(), 1e-6f)
        assertEquals(0.5f, contact.fraction)
        assertEquals(0f, hit(Vec3f(1f, 0f, 0f), -1e-7f).fraction)
        assertEquals(1f, hit(Vec3f(1f, 0f, 0f), 1.0000001f).fraction)
        assertFailsWith<InvalidShapeCastResultException> { hit(Vec3f(1f, 0f, 0f), -0.01f) }
        assertFailsWith<InvalidShapeCastResultException> { hit(Vec3f(1f, 0f, 0f), Float.NaN) }
    }

    @Test
    fun stationaryDegenerateContactIsExplicitAndFallbackDiagnosticsAreBounded() {
        assertFailsWith<InvalidShapeCastResultException> { hit(Vec3f(0f, 0f, 0f), to = Vec3f(0f, 0f, 0f)) }
        val decoder = ShapeCastContactDecoder()
        val records = mutableListOf<LogRecord>()
        val sink = LogSink { records += it }
        Log.install(sink)
        try {
            repeat(2) { hit(Vec3f(0f, 0f, 0f), decoder = decoder) }
            assertEquals(1, records.count { it.tag == "JoltShapeCast" })
        } finally {
            Log.remove(sink)
        }
    }
}
