/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.pool

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** Once grown to its working size, allocating, releasing and visiting ids allocates nothing. */
class ReusableIdSetAllocationTest {
    @Test
    fun steadyStateAllocateReleaseAndForEachAllocateZeroBytes() {
        val ids = ReusableIdSet(initialCapacity = 1)
        var sum = 0L
        val frame = {
            repeat(ID_COUNT) { ids.allocate() }
            ids.forEach { sum += it }
            // Evens then odds, so later allocations scan back over freed words. Plain loops: a
            // stepped range would allocate its progression.
            var id = 0
            while (id < ID_COUNT) {
                ids.release(id)
                id += 2
            }
            id = 1
            while (id < ID_COUNT) {
                ids.release(id)
                id += 2
            }
        }
        repeat(WARMUP_FRAMES) { frame() }

        val before = threadBean.currentThreadAllocatedBytes
        repeat(MEASURED_FRAMES) { frame() }
        val after = threadBean.currentThreadAllocatedBytes

        assertEquals(0L, (after - before) / MEASURED_FRAMES, "steady-state use must allocate 0 bytes per frame (sum $sum)")
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    private companion object {
        const val ID_COUNT = 1000
        const val WARMUP_FRAMES = 50
        const val MEASURED_FRAMES = 100
    }
}
