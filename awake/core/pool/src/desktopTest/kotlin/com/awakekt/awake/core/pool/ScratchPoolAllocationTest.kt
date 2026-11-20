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

/**
 * A warm [ScratchPool] obtains and resets without allocating. Read as this thread's allocated bytes
 * in [ROUNDS] windows of [MEASURED_FRAMES] frames, and the median window must be 0: one window once
 * caught an allocation of the test thread's that was not the pool's (CI's loaded suite step, #1592),
 * while an allocation per obtain or per reset is in every window.
 */
class ScratchPoolAllocationTest {

    private class ScratchItem(var a: Float = 0f, var b: Float = 0f)

    @Test
    fun steadyStateObtainAndResetAllocatesZeroBytes() {
        val pool = ScratchPool(initialCapacity = 1000) { ScratchItem() }
        fun frame() {
            repeat(ITEM_COUNT) { pool.obtain() }
            pool.reset()
        }

        // Warmup: allocate up to high-water mark
        repeat(WARMUP_FRAMES) { frame() }

        val windows = LongArray(ROUNDS) {
            val before = threadBean.currentThreadAllocatedBytes
            repeat(MEASURED_FRAMES) { frame() }
            threadBean.currentThreadAllocatedBytes - before
        }
        assertEquals(
            0L,
            windows.sorted()[ROUNDS / 2],
            "Steady-state ScratchPool.obtain() and reset() must allocate 0 bytes; bytes by window: ${windows.joinToString()}",
        )
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    private companion object {
        const val ITEM_COUNT = 1000
        const val WARMUP_FRAMES = 50
        const val MEASURED_FRAMES = 100
        const val ROUNDS = 5
    }
}
