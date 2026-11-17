/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.TimeSource

/**
 * Counting changes costs a HUD nothing (#1512): a frame of writes to [CanvasRevisionWorkload.ELEMENTS]
 * elements, changed and unchanged, and a read of each one's [CanvasElementBase.contentRevision],
 * allocates no byte once warm. Escape analysis is off for this module's tests, so a boxed primitive
 * would be counted as Kotlin/Wasm pays for it.
 *
 * The quietest of several windows must be 0 bytes, as in [CanvasComponentReadAllocationProbeTest].
 * The time a frame's revision reads take is printed: the O(n) poll a skipping host pays.
 */
class CanvasRevisionAllocationProbeTest {
    @Test
    fun writingAndPollingRevisionsAllocatesNothing() {
        val workload = CanvasRevisionWorkload()
        var frame = 0
        repeat(WARMUP_FRAMES) { workload.frame(frame++) }

        val windows = LongArray(MEASURED_WINDOWS)
        repeat(MEASURED_WINDOWS) { window ->
            val start = allocated()
            repeat(MEASURED_FRAMES) { workload.frame(frame++) }
            windows[window] = allocated() - start
        }
        windows.sort()
        println("CanvasRevisionAllocation: ${windows.joinToString()} bytes per $MEASURED_FRAMES frames of ${CanvasRevisionWorkload.ELEMENTS} elements")
        assertEquals(0L, windows[0], "fewest bytes over $MEASURED_FRAMES frames")
    }

    @Test
    fun pollingEveryElementsRevisionIsTimed() {
        val workload = CanvasRevisionWorkload()
        val elements = workload.elements
        var sum = 0
        repeat(WARMUP_FRAMES) { for (element in elements) sum += element.contentRevision }
        val samples = LongArray(MEASURED_WINDOWS) {
            val mark = TimeSource.Monotonic.markNow()
            repeat(MEASURED_FRAMES) { for (element in elements) sum += element.contentRevision }
            mark.elapsedNow().inWholeNanoseconds / MEASURED_FRAMES
        }
        samples.sort()
        println("CanvasRevisionPoll: ${samples[MEASURED_WINDOWS / 2] / NANOS_PER_MICRO} us a frame for ${elements.size} elements (sum $sum)")
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        const val WARMUP_FRAMES = 3_000
        const val MEASURED_FRAMES = 200
        const val MEASURED_WINDOWS = 5
        const val NANOS_PER_MICRO = 1_000.0
    }
}
