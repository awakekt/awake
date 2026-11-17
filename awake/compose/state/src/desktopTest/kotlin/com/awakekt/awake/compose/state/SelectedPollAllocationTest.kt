/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.state

import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A skipping host's check of 100 selected parts allocates nothing while the flow stands still (#1512). */
class SelectedPollAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun aStillFlowIsCheckedWithoutAllocating() {
        val workload = SelectedPollWorkload(PARTS)
        repeat(WARMUP) { assertFalse(workload.poll()) }
        val windows = LongArray(WINDOWS) {
            val before = threads.currentThreadAllocatedBytes
            repeat(POLLS) { workload.poll() }
            threads.currentThreadAllocatedBytes - before
        }.sorted()
        println("SelectedPoll: $PARTS parts, fewest ${windows[0]} B over $POLLS checks")
        assertEquals(0L, windows[0], "fewest bytes over $POLLS checks of $PARTS parts")
        workload.flow.value = workload.flow.value.copy(name = "Guild Hall")
        assertFalse(workload.poll(), "a part nobody selected changed")
        workload.flow.value = workload.flow.value.copy(gold = 7L)
        assertTrue(workload.poll(), "a selected part changed")
    }

    private companion object {
        const val PARTS = 100
        const val WARMUP = 2_000
        const val POLLS = 200
        const val WINDOWS = 9
    }
}
