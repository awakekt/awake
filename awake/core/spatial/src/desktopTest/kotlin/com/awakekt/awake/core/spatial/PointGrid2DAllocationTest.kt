/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.spatial

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent. */
class PointGrid2DAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

    @Test
    fun rebuildingAndQueryingAWarmGridAllocatesNothing() {
        val grid = PointGrid2D(cellSize = 4f)
        val xs = FloatArray(POINTS) { (it * 37 % 400).toFloat() - 200f }
        val zs = FloatArray(POINTS) { (it * 91 % 400).toFloat() - 200f }
        var sink = 0

        fun frame() {
            grid.clear()
            for (id in xs.indices) grid.add(id, xs[id], zs[id])
            for (id in 0 until QUERIES) {
                grid.forEachNear(xs[id], zs[id], RADIUS) { sink += it }
                if (grid.anyNear(xs[id], zs[id], RADIUS) { it == id }) sink++
            }
        }

        repeat(WARMUP_FRAMES) { frame() }
        val windows = LongArray(MEASURED_WINDOWS)
        for (window in windows.indices) {
            val before = threads.currentThreadAllocatedBytes
            repeat(FRAMES_PER_WINDOW) { frame() }
            windows[window] = threads.currentThreadAllocatedBytes - before
        }
        windows.sort()
        assertEquals(0L, windows[windows.size / 2], "bytes allocated per window: ${windows.toList()} ($sink)")
    }

    private companion object {
        const val POINTS = 2_000
        const val QUERIES = 200
        const val RADIUS = 10f
        const val WARMUP_FRAMES = 200
        const val MEASURED_WINDOWS = 15
        const val FRAMES_PER_WINDOW = 20
    }
}
