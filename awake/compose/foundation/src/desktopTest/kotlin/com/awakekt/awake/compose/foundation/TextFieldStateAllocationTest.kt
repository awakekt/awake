/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.core.input.TextEditAction
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A [TextFieldState] costs what it did before read tracking existed (#1512, stage a).
 *
 * A host that does not skip unchanged composition passes must not pay for tracking: a state is one
 * object of [STATE_BYTES] bytes, and reading it allocates nothing. The tracking state exists only for a
 * pass that asked for it.
 */
class TextFieldStateAllocationTest {
    @Test
    fun creatingStatesAllocatesWhatItDidBeforeTracking() {
        val states = arrayOfNulls<TextFieldState>(COUNT)
        repeat(WARMUP) { TextFieldState() }
        val before = allocatedBytes()
        for (i in 0 until COUNT) states[i] = TextFieldState()
        val perState = (allocatedBytes() - before) / COUNT
        println("TextFieldState: $perState B per state, array slot included")
        assertTrue(perState <= STATE_BYTES, "a state allocated $perState B, over $STATE_BYTES B")
    }

    @Test
    fun readingAllocatesNothingWithoutATrackingPass() {
        val state = TextFieldState("hello")
        repeat(WARMUP) { state.text.length + state.cursor + state.selectionStart + state.selectionEnd }
        val before = allocatedBytes()
        var sum = 0
        repeat(COUNT) { sum += state.text.length + state.cursor + state.selectionStart + state.selectionEnd }
        val bytes = allocatedBytes() - before
        assertEquals(COUNT * 20, sum)
        assertTrue(bytes <= SLACK_BYTES, "reads allocated $bytes B")
    }

    @Test
    fun editingStillWorksWithoutATrackingPass() {
        val state = TextFieldState("ab")
        state.apply(TextEditAction.Backspace)
        state.insert("xy")
        assertEquals("axy", state.text)
        assertEquals(3, state.cursor)
    }

    private fun allocatedBytes(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val COUNT = 20_000
        const val WARMUP = 5_000
        const val SLACK_BYTES = 2_000

        // What main allocates per state, measured with the array slot included.
        const val STATE_BYTES = 56
    }
}
