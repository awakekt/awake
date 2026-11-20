/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.testing.allocation

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

/**
 * What a piece of work allocates on the calling thread, read the way an allocation gate must read it
 * on a loaded machine (#1592): a warm-up window, then [windowBytes] of several windows, and the
 * median of them.
 *
 * One window can catch an allocation of the test thread's that is not the work's, which once failed
 * gates in CI's loaded suite step. An allocation the work makes every unit is in every window, so
 * the median keeps it and drops the one-off. [perUnit] divides the median window by the units each
 * window ran, as a gate's own `bytes / frames` did.
 *
 * Every module's desktop tests compile this file (the library plugin adds it), so a gate calls
 * [readAllocation] instead of reading `ThreadMXBean` itself; `verifyAllocationGates` holds that.
 */
internal class AllocationReading(val windowBytes: LongArray, val unitsPerWindow: Long) {
    /** The median window's bytes. */
    val medianBytes: Long = windowBytes.sorted()[windowBytes.size / 2]

    /** The median window's bytes per unit of work, rounded down. */
    val perUnit: Long get() = medianBytes / unitsPerWindow

    override fun toString(): String =
        "$perUnit B per unit (median of ${windowBytes.size} windows of $unitsPerWindow units: ${windowBytes.joinToString()} B)"
}

/**
 * Runs [window] once to warm up, then [windows] more times, reading the calling thread's allocated
 * bytes around each. [window] is one window's work, [unitsPerWindow] units of it (frames, calls).
 */
internal inline fun readAllocation(unitsPerWindow: Long, windows: Int = DEFAULT_WINDOWS, window: () -> Unit): AllocationReading {
    val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
    window()
    val bytes = LongArray(windows)
    for (index in 0 until windows) {
        val before = threads.currentThreadAllocatedBytes
        window()
        bytes[index] = threads.currentThreadAllocatedBytes - before
    }
    return AllocationReading(bytes, unitsPerWindow)
}

/** [readAllocation] for a window of [unitsPerWindow] units, an `Int` count. */
internal inline fun readAllocation(unitsPerWindow: Int, windows: Int = DEFAULT_WINDOWS, window: () -> Unit): AllocationReading =
    readAllocation(unitsPerWindow.toLong(), windows, window)

/** Fails unless [reading] allocates nothing per unit. */
internal fun assertAllocatesNothing(reading: AllocationReading, what: String) {
    if (reading.perUnit != 0L) throw AssertionError("$what allocates: $reading")
}

/** Fails unless [reading] allocates at most [ceiling] bytes per unit. */
internal fun assertAllocatesAtMost(reading: AllocationReading, ceiling: Long, what: String) {
    if (reading.perUnit > ceiling) throw AssertionError("$what allocates over $ceiling B per unit: $reading")
}

/** Five windows: a one-off in two of them still leaves the median clean. */
internal const val DEFAULT_WINDOWS = 5
