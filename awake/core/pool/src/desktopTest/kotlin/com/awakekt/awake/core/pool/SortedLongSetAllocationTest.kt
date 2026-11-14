/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.pool

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** Once grown, refilling, sorting, searching, copying and diffing a tick's ids allocates nothing. */
class SortedLongSetAllocationTest {
    @Test
    fun steadyStateTicksAllocateZeroBytes() {
        val random = Random(7)
        // The ids each tick sees, made ahead so the measurement counts only the set's own work.
        val ticks = Array(TICKS) { LongArray(ID_COUNT) { random.nextLong(0L, ID_RANGE) } }
        val previous = SortedLongSet()
        val current = SortedLongSet()
        var changes = 0L
        val tick = { ids: LongArray ->
            current.clear()
            for (id in ids) current.addUnsorted(id)
            current.sort()
            if (ids[0] in current) changes++
            SortedLongSet.diff(previous, current, { changes += it }, { changes -= it })
            previous.copyFrom(current)
        }
        repeat(WARMUP_ROUNDS) { ticks.forEach(tick) }

        val before = threadBean.currentThreadAllocatedBytes
        repeat(MEASURED_ROUNDS) { ticks.forEach(tick) }
        val after = threadBean.currentThreadAllocatedBytes

        assertEquals(0L, (after - before) / (MEASURED_ROUNDS * TICKS), "steady-state ticks must allocate 0 bytes (checksum $changes)")
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    private companion object {
        const val ID_COUNT = 1000
        const val ID_RANGE = 5_000L
        const val TICKS = 8
        const val WARMUP_ROUNDS = 50
        const val MEASURED_ROUNDS = 100
    }
}
