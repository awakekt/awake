/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PingMeterTest {
    @Test
    fun matchesNoncesComputesEwmaAndIgnoresUnknownDuplicateOrEvictedPongs() {
        val meter = PingMeter()
        assertTrue(meter.smoothedMs.isNaN())
        assertTrue(meter.lastMs.isNaN())

        val n0 = meter.send(0.0)
        assertEquals(0L, n0)
        assertTrue(meter.receive(n0, 0.040))
        assertEquals(40.0, meter.lastMs, 1e-6)
        assertEquals(40.0, meter.smoothedMs, 1e-6)

        val n1 = meter.send(1.0)
        assertTrue(meter.receive(n1, 1.060))
        val n2 = meter.send(2.0)
        assertTrue(meter.receive(n2, 2.200))
        assertEquals(200.0, meter.lastMs, 1e-6)
        assertEquals(40.0 + 0.3 * 20.0 + 0.3 * (200.0 - 46.0), meter.smoothedMs, 1e-6)
        assertEquals(3, meter.samples)

        // Duplicate, negative, never-sent, and ring-evicted nonces return false.
        assertFalse(meter.receive(n2, 2.250))
        assertFalse(meter.receive(-1L, 2.250))
        assertFalse(meter.receive(99L, 2.250))

        val evicted = meter.send(3.0)
        repeat(PingMeter.DEFAULT_KEPT) { step -> meter.send(4.0 + step) }
        assertFalse(meter.receive(evicted, 9.0))
    }

    @Test
    fun outOfOrderPongWithinKeptWindowResolvesAndStalledPingEscalatesReading() {
        val meter = PingMeter()
        val slow = meter.send(0.0)
        val next = meter.send(1.0)
        assertTrue(meter.receive(slow, 1.25))
        assertEquals(1_250.0, meter.lastMs, 1e-6)
        assertTrue(meter.receive(next, 1.05))
        assertEquals(50.0, meter.lastMs, 1e-6)

        meter.reset()
        assertTrue(meter.readingMs(5.0).isNaN())
        meter.receive(meter.send(0.0), 0.04)
        meter.send(1.0)
        assertEquals(40.0, meter.readingMs(1.5), 1e-6)
        assertEquals(2_000.0, meter.readingMs(3.0), 1e-6)
    }

    @Test
    fun rejectsInvalidMeterConfiguration() {
        assertFailsWith<IllegalArgumentException> { PingMeter(smoothing = 0.0) }
        assertFailsWith<IllegalArgumentException> { PingMeter(smoothing = 1.5) }
        assertFailsWith<IllegalArgumentException> { PingMeter(kept = 0) }
        assertFailsWith<IllegalArgumentException> { PingMeter(staleSeconds = 0.0) }
    }
}
