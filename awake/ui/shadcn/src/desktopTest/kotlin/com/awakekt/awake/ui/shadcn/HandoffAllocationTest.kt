/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.ui.shadcn.components.Handoff
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A [Handoff] costs nothing for a host that does not skip unchanged composition (#1512): offering and
 * taking allocate no tracking state outside a pass that tracks reads.
 */
class HandoffAllocationTest {
    @Test
    fun offeringAndTakingAllocateNothingWithoutATrackingPass() {
        val handoff = Handoff<Int>()
        repeat(WARMUP) { handoff.offer(it); handoff.take() }
        val before = allocatedBytes()
        var taken = 0
        repeat(COUNT) {
            handoff.offer(1)
            taken += handoff.take() ?: 0
        }
        val bytes = allocatedBytes() - before
        assertEquals(COUNT, taken)
        assertTrue(bytes <= SLACK_BYTES, "offer and take allocated $bytes B")
    }

    @Test
    fun aValueIsTakenOnce() {
        val handoff = Handoff<String>()
        handoff.offer("a")
        assertEquals("a", handoff.take())
        assertNull(handoff.take())
    }

    private fun allocatedBytes(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val COUNT = 20_000
        const val WARMUP = 200_000
        const val SLACK_BYTES = 2_000
    }
}
