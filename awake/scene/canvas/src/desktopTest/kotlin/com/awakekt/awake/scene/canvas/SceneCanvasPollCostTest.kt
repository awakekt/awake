/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.TimeSource

/**
 * What the check [SceneCanvas] gives a host that skips unchanged frames costs each frame (#1512), at
 * game-scale element counts: it allocates nothing on a still canvas, and its time is printed, a walk
 * of every element's revision, the element list and each element's path through the hierarchy.
 */
class SceneCanvasPollCostTest {
    @Test
    fun aStillCanvasIsCheckedWithoutAllocating() {
        val lines = SIZES.map { size ->
            val workload = SceneCanvasPollWorkload(size)
            val first = workload.read()
            repeat(WARMUP) { assertSame(first, workload.read(), "a still canvas answered a change") }
            val windows = LongArray(WINDOWS) {
                val start = allocated()
                repeat(READS) { workload.read() }
                allocated() - start
            }.sorted()
            val micros = DoubleArray(WINDOWS) {
                val mark = TimeSource.Monotonic.markNow()
                repeat(READS) { workload.read() }
                mark.elapsedNow().inWholeNanoseconds / READS / NANOS_PER_MICRO
            }.sorted()
            assertEquals(0L, windows[0], "fewest bytes over $READS checks of $size elements")
            "$size elements ${micros[WINDOWS / 2]} us, ${windows[0]} B"
        }
        println("SceneCanvasPoll (desktop): ${lines.joinToString("; ")}")
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        val SIZES = intArrayOf(300, 1_000, 3_000).toList()
        const val WARMUP = 2_000
        const val READS = 200
        const val WINDOWS = 5
        const val NANOS_PER_MICRO = 1_000.0
    }
}
