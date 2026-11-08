/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StatsSeriesTest {
    @Test
    fun computesAverageMedianP95AndMaxWithinTrailingWindow() {
        val series = StatsSeries(capacity = 32)
        for (i in 1..20) {
            series.add(nowSeconds = i.toDouble(), value = i * 10.0)
        }

        val full = checkNotNull(series.summary(sinceSeconds = 1.0))
        assertEquals(StatsSummary(avg = 105.0, p50 = 100.0, p95 = 190.0, max = 200.0), full)

        val recent = checkNotNull(series.summary(sinceSeconds = 16.0))
        assertEquals(StatsSummary(avg = 180.0, p50 = 180.0, p95 = 200.0, max = 200.0), recent)
        assertNull(series.summary(sinceSeconds = 21.0))
    }

    @Test
    fun wrapsAroundCapacityAndIgnoresNonFiniteReadings() {
        val series = StatsSeries(capacity = 4)
        for (i in 1..6) {
            series.add(nowSeconds = i.toDouble(), value = i.toDouble())
        }
        series.add(nowSeconds = 7.0, value = Double.NaN)
        series.add(nowSeconds = 8.0, value = Double.POSITIVE_INFINITY)
        assertEquals(4, series.size)

        val summary = checkNotNull(series.summary(sinceSeconds = 0.0, roundStep = 0.0))
        assertEquals(StatsSummary(avg = 4.5, p50 = 4.0, p95 = 6.0, max = 6.0), summary)

        series.clear()
        assertEquals(0, series.size)
        assertNull(series.summary(sinceSeconds = 0.0))
    }

    @Test
    fun transformsReadingsAndSkipsNonFiniteTransformedValues() {
        val frameMs = StatsSeries(capacity = 16)
        frameMs.add(nowSeconds = 1.0, value = 20.0)
        frameMs.add(nowSeconds = 2.0, value = 40.0)
        frameMs.add(nowSeconds = 3.0, value = 0.0)

        val fps = checkNotNull(
            frameMs.summary(sinceSeconds = 0.0) { ms ->
                if (ms > 0.0) 1_000.0 / ms else Double.NaN
            },
        )
        assertEquals(StatsSummary(avg = 37.5, p50 = 25.0, p95 = 50.0, max = 50.0), fps)
    }

    @Test
    fun validatesArguments() {
        assertFailsWith<IllegalArgumentException> { StatsSeries(capacity = 0) }
        val series = StatsSeries()
        assertFailsWith<IllegalArgumentException> { series.add(Double.NaN, 1.0) }
        assertFailsWith<IllegalArgumentException> { series.summary(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { series.summary(0.0, roundStep = -0.1) }
    }
}
