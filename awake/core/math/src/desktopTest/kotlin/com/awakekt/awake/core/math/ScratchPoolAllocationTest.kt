/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class ScratchPoolAllocationTest {

    private class ScratchItem(var a: Float = 0f, var b: Float = 0f)

    @Test
    fun steadyStateObtainAndResetAllocatesZeroBytes() {
        val pool = ScratchPool(initialCapacity = 1000) { ScratchItem() }

        // Warmup: allocate up to high-water mark
        repeat(WARMUP_FRAMES) {
            repeat(ITEM_COUNT) {
                pool.obtain()
            }
            pool.reset()
        }

        // Measure steady-state allocation
        val before = threadBean.currentThreadAllocatedBytes
        repeat(MEASURED_FRAMES) {
            repeat(ITEM_COUNT) {
                pool.obtain()
            }
            pool.reset()
        }
        val after = threadBean.currentThreadAllocatedBytes

        val allocatedBytesPerFrame = (after - before) / MEASURED_FRAMES
        assertEquals(
            0L,
            allocatedBytesPerFrame,
            "Steady-state ScratchPool.obtain() and reset() must allocate 0 bytes per frame",
        )
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    private companion object {
        const val ITEM_COUNT = 1000
        const val WARMUP_FRAMES = 50
        const val MEASURED_FRAMES = 100
    }
}
