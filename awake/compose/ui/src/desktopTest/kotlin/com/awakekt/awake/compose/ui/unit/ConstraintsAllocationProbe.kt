/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.unit

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Settles Int-vs-Float for layout arithmetic, per docs/reference/compose-engine/01-layout.md.
 *
 * Four Int fields pack into one `Long`, so [Constraints] can be a `value class` and a measure pass
 * allocates nothing. Four Floats need 128 bits, cannot pack, and so must be a real object -- one
 * allocation per constraint built. This measures the gap on the operations a measure pass actually
 * performs: `offset` (every padding-shaped `LayoutModifierNode`) and `copy` (every Row/Column
 * measure).
 *
 * Desktop-only for the same reason `UiFrameAllocationProbe` is: `currentThreadAllocatedBytes` has
 * no wasm or Native equivalent.
 */
class ConstraintsAllocationProbe {

    @Test
    fun intPackedConstraintsAllocateNothing() {
        warmUp()
        val bytes = measure { sink += intOps() }
        println("Int (packed value class): $bytes bytes / $OPS ops")
        assertEquals(0L, bytes, "packed Constraints must not allocate; a value class that boxes is the bug")
    }

    @Test
    fun floatConstraintsAllocatePerOperation() {
        warmUp()
        val bytes = measure { sink += floatOps() }
        val perOp = bytes.toDouble() / OPS
        println("Float (object): $bytes bytes / $OPS ops = ${perOp.format()} B/op")
        // Not a ceiling to defend -- this test exists to keep the comparison honest if someone
        // later claims the two are equivalent.
        assertTrue(bytes > 0L, "a 4-Float object cannot be allocation-free; measurement is wrong")
    }

    private fun intOps(): Int {
        var c = Constraints.of(0, 1920, 0, Constraints.Infinity)
        var acc = 0
        repeat(OPS) {
            c = c.offset(dx = -PADDING, dy = -PADDING)
                .copy(minWidth = 0, maxWidth = if (c.hasBoundedWidth) c.maxWidth else 1920)
            acc += c.maxWidth
            if (c.maxWidth < RESET_BELOW) c = Constraints.of(0, 1920, 0, Constraints.Infinity)
        }
        return acc
    }

    private fun floatOps(): Int {
        var c = FloatConstraints(0f, 1920f, 0f, Float.POSITIVE_INFINITY)
        var acc = 0
        repeat(OPS) {
            c = c.offset(-PADDING.toFloat(), -PADDING.toFloat())
                .copy(minWidth = 0f, maxWidth = if (c.hasBoundedWidth) c.maxWidth else 1920f)
            acc += c.maxWidth.toInt()
            if (c.maxWidth < RESET_BELOW) c = FloatConstraints(0f, 1920f, 0f, Float.POSITIVE_INFINITY)
        }
        return acc
    }

    /** Escapes so the JIT cannot eliminate the allocations -- a real measure pass hands
     * constraints to a virtual `Measurable.measure`, which defeats escape analysis the same way. */
    private var sink: Int = 0

    private fun warmUp() {
        repeat(WARM_UP_ROUNDS) {
            sink += intOps()
            sink += floatOps()
        }
    }

    private inline fun measure(block: () -> Unit): Long {
        val before = allocatedBytes()
        block()
        return allocatedBytes() - before
    }

    private fun Double.format(): String = ((this * 100).toInt() / 100.0).toString()

    private class FloatConstraints(
        val minWidth: Float,
        val maxWidth: Float,
        val minHeight: Float,
        val maxHeight: Float,
    ) {
        val hasBoundedWidth: Boolean get() = maxWidth != Float.POSITIVE_INFINITY

        fun offset(dx: Float, dy: Float) = FloatConstraints(
            (minWidth + dx).coerceAtLeast(0f),
            if (hasBoundedWidth) (maxWidth + dx).coerceAtLeast(0f) else Float.POSITIVE_INFINITY,
            (minHeight + dy).coerceAtLeast(0f),
            if (maxHeight != Float.POSITIVE_INFINITY) (maxHeight + dy).coerceAtLeast(0f) else Float.POSITIVE_INFINITY,
        )

        fun copy(minWidth: Float, maxWidth: Float) =
            FloatConstraints(minWidth, maxWidth, minHeight, maxHeight)
    }

    private companion object {
        const val OPS = 100_000
        const val WARM_UP_ROUNDS = 20
        const val PADDING = 16
        const val RESET_BELOW = 64

        private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

        fun allocatedBytes(): Long = threads.currentThreadAllocatedBytes
    }
}
