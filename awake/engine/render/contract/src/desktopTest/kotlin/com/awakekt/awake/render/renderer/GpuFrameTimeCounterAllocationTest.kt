/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies steady-state timed frames allocate nothing in [GpuFrameTimeCounter] bookkeeping (#1709). */
class GpuFrameTimeCounterAllocationTest {

    @Test
    fun steadyTimedFramesAllocateZeroBytesInBookkeeping() {
        val counter = GpuFrameTimeCounter()
        val tickets = LongArray(FRAMES_IN_FLIGHT) { NO_TICKET }
        var slot = 0

        fun step(ms: Double) {
            val prev = tickets[slot]
            if (prev != NO_TICKET) {
                counter.completeSubmission(prev, ms)
            }
            tickets[slot] = counter.beginSubmission()
            counter.publish()
            slot = (slot + 1) % FRAMES_IN_FLIGHT
        }

        repeat(WARMUP) { i -> step(1.5 + (i % 7) * 0.1) }

        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(id)
        repeat(FRAMES) { i -> step(2.0 + (i % 13) * 0.1) }
        val bytesPerFrame = (threads.getThreadAllocatedBytes(id) - before) / FRAMES
        assertEquals(0L, bytesPerFrame, "GpuFrameTimeCounter steady-state bookkeeping must allocate 0 B/frame")
    }

    private companion object {
        const val FRAMES_IN_FLIGHT = 2
        const val NO_TICKET = -1L
        const val WARMUP = 200
        const val FRAMES = 500
    }
}
