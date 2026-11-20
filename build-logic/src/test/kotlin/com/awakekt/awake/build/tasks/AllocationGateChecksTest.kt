/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AllocationGateChecksTest {
    @Test
    fun aTestReadingTheCounterItselfIsFound() {
        assertTrue(readsAllocationCounter("val before = threads.currentThreadAllocatedBytes"))
        assertTrue(readsAllocationCounter("val b = bean.getThreadAllocatedBytes(thread)"))
        assertTrue(readsAllocationCounter("return bean.getCurrentThreadAllocatedBytes()"))
    }

    @Test
    fun aCommentOrTheHelperIsNot() {
        assertFalse(readsAllocationCounter("/** Desktop-only: `currentThreadAllocatedBytes` has no wasm equivalent. */"))
        assertFalse(readsAllocationCounter("// reads currentThreadAllocatedBytes in windows\nval r = readAllocation(10) { work() }"))
    }

    @Test
    fun aNewReaderFailsAndARecordedOneThatStoppedLeavesTheRecord() {
        val failures = allocationGateFailures(readers = setOf("a/NewGate.kt", "a/Old.kt"), allowed = setOf("a/Old.kt", "a/Converted.kt"))
        assertEquals(2, failures.size)
        assertTrue(failures[0].startsWith("a/NewGate.kt reads"))
        assertTrue(failures[1].startsWith("a/Converted.kt no longer reads"))
        assertEquals(emptyList(), allocationGateFailures(setOf("a/Old.kt"), setOf("a/Old.kt")))
    }
}
