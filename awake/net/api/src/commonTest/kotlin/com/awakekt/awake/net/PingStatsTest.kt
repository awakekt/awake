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

class PingStatsTest {
    @Test
    fun emptyStatsReturnSentinelValuesAndRejectOutOfRangeSamples() {
        val stats = PingStats()
        assertEquals(0, stats.count)
        assertEquals(-1, stats.lastMs)
        assertEquals(-1, stats.percentile(0.5))
        assertEquals(-1, stats.jitterMs())

        assertFalse(stats.add(-1))
        assertFalse(stats.add(PingStats.DEFAULT_MAX_MILLIS + 1))
        assertEquals(0, stats.count)
    }

    @Test
    fun fineAndCoarseBucketsReportExpectedPercentileUpperEdges() {
        val stats = PingStats()
        // 18 samples around 42 ms (bucket 40..44 -> upper edge 45), 2 coarse samples at 1_260 ms (upper edge 1_500).
        repeat(18) { assertTrue(stats.add(42)) }
        repeat(2) { assertTrue(stats.add(1_260)) }

        assertEquals(20, stats.count)
        assertEquals(1_260, stats.lastMs)
        assertEquals(45, stats.percentile(0.0))
        assertEquals(45, stats.percentile(0.5))
        assertEquals(45, stats.percentile(0.90))
        assertEquals(1_500, stats.percentile(0.95))
        assertEquals(1_500, stats.percentile(1.0))

        assertTrue(stats.add(PingStats.DEFAULT_MAX_MILLIS))
        assertEquals(PingStats.DEFAULT_MAX_MILLIS, stats.percentile(1.0))
    }

    @Test
    fun rfc3550JitterSmoothsSpikesAndResetClearsAllState() {
        val stats = PingStats()
        assertTrue(stats.add(40))
        assertEquals(-1, stats.jitterMs())

        // |200 - 40| = 160 -> jitter = 160 / 16 = 10 ms.
        assertTrue(stats.add(200))
        assertEquals(10, stats.jitterMs())

        // |40 - 200| = 160 -> jitter = 10 + (160 - 10) / 16 = 19.375 -> rounds to 19 ms.
        assertTrue(stats.add(40))
        assertEquals(19, stats.jitterMs())

        stats.reset()
        assertEquals(0, stats.count)
        assertEquals(-1, stats.lastMs)
        assertEquals(-1, stats.percentile(0.5))
        assertEquals(-1, stats.jitterMs())
    }

    @Test
    fun validatesBucketGeometryParameters() {
        assertFailsWith<IllegalArgumentException> { PingStats(fineMillis = 0) }
        assertFailsWith<IllegalArgumentException> { PingStats(fineMillis = 7, fineLimitMillis = 100) }
        assertFailsWith<IllegalArgumentException> { PingStats(maxMillis = 500, fineLimitMillis = 1_000) }
        assertFailsWith<IllegalArgumentException> { PingStats(coarseMillis = 0) }
    }
}
