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

/**
 * A subcomposition takes its parent's current locals before every pass (#1774), which happens on
 * every measure of a `SubcomposeLayout`. When they are unchanged that must copy nothing: this hands a
 * composition a fresh but equal set of locals before each pass, as `SubcomposeLayout` does, and asks
 * that the passes allocate nothing, with escape analysis off as for this module's tests.
 */
class ParentLocalsAllocationTest {
    private val root = FakeNode()
    private val parent = ProvidedLocals()
    private val composition = Composition(FakeApplier(root), root, parent)
    private var sink = 0

    private val content: context(Composer)
    () -> Unit = { sink += Local.current }

    @Test
    fun unchangedParentLocalsCopyNothing() {
        // Equal entries, a different object each time: what a parent's `localsSnapshot` hands over.
        val equal = List(LOCALS_SETS) { snapshotOf(VALUE) }
        repeat(WARM_UP_PASSES) { pass(equal[it % LOCALS_SETS]) }
        val windows = List(WINDOWS) { measure { repeat(PASSES) { pass(equal[it % LOCALS_SETS]) } } }
        println("parent locals: bytes per window of $PASSES passes: $windows")
        assertEquals(0L, windows.min(), "an unchanged parent's locals were copied, minimum of $WINDOWS windows")
        assertEquals(VALUE * (WARM_UP_PASSES + WINDOWS * PASSES), sink, "every pass read the provided value")
    }

    @Test
    fun changedParentLocalsReachTheNextPass() {
        pass(snapshotOf(1))
        val before = sink
        pass(snapshotOf(2))
        assertEquals(2, sink - before, "the pass after the change read the new value")
    }

    private fun snapshotOf(value: Int): ProvidedLocals {
        var taken: ProvidedLocals? = null
        parent.provide(Local, value) { taken = parent.snapshot() }
        return checkNotNull(taken)
    }

    private fun pass(locals: ProvidedLocals) {
        composition.updateParentLocals(locals)
        composition.reconcile(content)
    }

    private fun measure(block: () -> Unit): Long {
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val before = bean.currentThreadAllocatedBytes
        block()
        return bean.currentThreadAllocatedBytes - before
    }

    private companion object {
        val Local = compositionLocalOf { 0 }
        const val VALUE = 3
        const val LOCALS_SETS = 4
        const val WARM_UP_PASSES = 20_000
        const val PASSES = 10_000
        const val WINDOWS = 7
    }
}
