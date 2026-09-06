/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.ui.platform.keyEvents
import com.awakekt.awake.compose.ui.platform.toFrameInput
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the poll-to-frame adapter costs, measured rather than argued.
 *
 * The worry it answers: turning a polled [InputSnapshot] into an event-shaped `FrameInput` looks
 * like it trades polling's zero-allocation for an object per frame. Two of the three costs turn out
 * not to be new, and the third is zero on almost every frame.
 *
 * Desktop-only, like `ComposeFrameProbe`: `currentThreadAllocatedBytes` has no wasm or Native
 * equivalent.
 */
class InputAdapterAllocationProbe {

    private fun snapshot(
        down: Set<Key> = emptySet(),
        pressed: Set<Key> = emptySet(),
        released: Set<Key> = emptySet(),
    ) = InputSnapshot(
        pointerX = 10f,
        pointerY = 10f,
        pointerDown = false,
        scrollDeltaX = 0f,
        scrollDeltaY = 0f,
        keysDown = down,
        keysPressed = pressed,
        keysReleased = released,
        typedText = "",
        editActions = emptyList(),
    )

    @Test
    fun aQuietFrameAllocatesNothingForKeys() {
        // The overwhelmingly common frame. A fast typist produces well under one transition per
        // frame at 60 fps, and *holding* a key produces none at all after the first.
        val quiet = snapshot(down = setOf(Key.W))

        val bytes = measure { quiet.keyEvents() }

        assertEquals(0L, bytes, "a frame with no key transition allocated")
    }

    @Test
    fun theSharedEmptyListIsReturnedNotANewOne() {
        // Why the number above is exactly zero rather than merely small.
        val quiet = snapshot(down = setOf(Key.W))

        assertTrue(quiet.keyEvents() === quiet.keyEvents(), "a fresh list per quiet frame")
    }

    @Test
    fun aTransitionFrameCostsLessThanOnePercentOfTheFrameBudget() {
        // The allocating case, bounded by how many keys a hand moves at once. Measured against the
        // engine's own 39,000 B/frame ceiling so the number means something.
        val typing = snapshot(down = setOf(Key.Shift), pressed = setOf(Key.S))

        val bytes = measure { typing.keyEvents() }

        println("InputAdapterAllocationProbe: $bytes B on a key-transition frame")
        assertTrue(bytes < FRAME_CEILING_BYTES / 100, "a keystroke cost $bytes B")
    }

    @Test
    fun theFrameInputItselfIsNotANewCost() {
        // The other half of the worry. `toFrameInput` does allocate a FrameInput -- but every caller
        // already constructs one per frame by hand, so this moves that allocation rather than adding
        // it. Pinned by measuring the adapter against a hand-written FrameInput of the same shape.
        val quiet = snapshot()

        val viaAdapter = measure { quiet.toFrameInput(800, 600) }
        val byHand = measure {
            com.awakekt.awake.compose.ui.platform.FrameInput(
                viewportWidth = 800,
                viewportHeight = 600,
                pointerX = 10,
                pointerY = 10,
            )
        }

        println("InputAdapterAllocationProbe: adapter $viaAdapter B, by hand $byHand B")
        assertTrue(
            viaAdapter <= byHand + 1,
            "the adapter cost more than writing the same FrameInput",
        )
    }

    /** Allocation of [block] per iteration, after a warm-up that settles the JIT. */
    private fun measure(block: () -> Any?): Long {
        var sink: Any? = null
        repeat(WARMUP) { sink = block() }
        val before = allocatedBytes()
        repeat(ITERATIONS) { sink = block() }
        val bytes = allocatedBytes() - before
        check(sink != null || sink == null)
        return bytes / ITERATIONS
    }

    private fun allocatedBytes(): Long {
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        return bean.getCurrentThreadAllocatedBytes()
    }

    private companion object {
        const val WARMUP = 10_000
        const val ITERATIONS = 100_000
        const val FRAME_CEILING_BYTES = 35_000
    }
}
