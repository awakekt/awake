/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShowcaseBenchmarkTest {
    @Test
    fun percentilesAreNearestRank() {
        val sorted = FloatArray(100) { it + 1f }
        assertEquals(50f, percentile(sorted, 50f))
        assertEquals(95f, percentile(sorted, 95f))
        assertEquals(99f, percentile(sorted, 99f))
        assertEquals(100f, percentile(sorted, 100f))
        assertEquals(1f, percentile(floatArrayOf(1f), 99f))
        assertEquals(0f, percentile(FloatArray(0), 50f))
    }

    @Test
    fun theWarmUpIsLeftOutAndTheRunStopsWhenFull() {
        val bench = FrameTimeBenchmark(warmupMs = 3000f, measuredMs = 100f, minFrames = 4)
        repeat(3) { bench.record(frameMs = 1000f, uiMs = 1000f, cpuMs = 1000f) }
        assertEquals(0, bench.recorded, "the warm-up's three seconds are not recorded")
        listOf(10f, 20f, 30f).forEach { bench.record(it, it / 10f, it / 2f) }
        assertFalse(bench.done, "60 ms of a 100 ms run")
        bench.record(40f, 4f, 20f)
        assertTrue(bench.done)
        bench.record(5000f, 5000f, 5000f)

        val summary = bench.summary()
        assertEquals(4, summary.frames)
        assertEquals(25f, summary.averageMs)
        assertEquals(20f, summary.p50Ms)
        assertEquals(40f, summary.p99Ms)
        assertEquals(40f, summary.maxMs, "a frame after the run is left out")
        assertEquals(4f, summary.uiP95Ms)
        assertEquals(10f, summary.cpuP50Ms)
        assertTrue(summary.pairs().startsWith("frames=4 avg=25.0 p50=20.0"))
    }

    @Test
    fun aFastRunStillRecordsItsLeastFrames() {
        val bench = FrameTimeBenchmark(warmupMs = 0f, measuredMs = 10f, minFrames = 50)
        repeat(49) { bench.record(1f, 0f, 0f) }
        assertFalse(bench.done, "time is up, but not enough frames are in")
        bench.record(1f, 0f, 0f)
        assertTrue(bench.done)
    }

    @Test
    fun launchArgumentsPickTheShowcaseAndItsOptions() {
        val arguments = mapOf("showcase" to "many-followers", "benchmark" to "true", "followers" to "250", "vsync" to "nope")
        val options = showcaseLaunchOptions(arguments)

        assertEquals("many-followers", showcaseIdOf(arguments))
        assertTrue(options.benchmark)
        assertEquals(250, options.followers)
        assertTrue(options.vsync, "an unreadable value keeps its default")
        assertNull(showcaseIdOf(mapOf("showcase" to "no-such-demo")))
        assertFalse(showcaseLaunchOptions(emptyMap()).benchmark)
    }
}
