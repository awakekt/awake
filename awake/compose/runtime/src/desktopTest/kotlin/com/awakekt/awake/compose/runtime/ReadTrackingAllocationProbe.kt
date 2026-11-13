/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

private class PolledText(var source: String) : PolledState<String>() {
    override fun read(): String = source
}

/**
 * Read tracking runs on every read of every pass a frame-driven host makes, so in steady state it
 * must allocate nothing: recording a read reuses the arrays the first pass grew.
 *
 * Desktop-only, like `UiFrameAllocationProbe`: `currentThreadAllocatedBytes` has no wasm or Native
 * equivalent.
 */
class ReadTrackingAllocationProbe {

    private val root = FakeNode()
    private val composition = Composition(FakeApplier(root), root)
    private val first = mutableStateOf("a")
    private val second = mutableStateOf("b")
    private val polled = PolledText("c")
    private var sink = 0

    private val content: context(Composer)
    () -> Unit = {
        repeat(READS_PER_PASS) {
            sink += first.value.length + second.value.length + polled.value.length
        }
    }

    @Test
    fun steadyStateReadsAndInvalidationChecksAllocateNothing() {
        repeat(WARM_UP_PASSES) { pass() }

        // The claim is that the code path allocates nothing, so one clean window proves it: a path
        // that allocates does so in every window, while a one-off (a late JIT deopt, a lazily
        // initialised JDK structure) lands in only some. The minimum ignores the one-offs and still
        // fails a real per-read allocation, which every window repeats.
        val windows = List(WINDOWS) { measure { repeat(PASSES) { pass() } } }

        println("read tracking: bytes per window of $PASSES passes x ${READS_PER_PASS * 3} reads: $windows")
        assertEquals(0L, windows.min(), "a tracked read or an isInvalid check allocated, minimum of $WINDOWS windows")
    }

    private fun pass() {
        composition.reconcile(content)
        if (composition.isInvalid) sink++
        first.value = if (first.value == "a") "A" else "a"
    }

    private fun measure(block: () -> Unit): Long {
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val before = bean.currentThreadAllocatedBytes
        block()
        return bean.currentThreadAllocatedBytes - before
    }

    private companion object {
        const val READS_PER_PASS = 16
        const val WARM_UP_PASSES = 20_000
        const val PASSES = 10_000
        const val WINDOWS = 7
    }
}
