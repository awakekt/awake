/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [CanvasOverlay] builds a [CanvasTree] every frame from the host's new item list. Its hierarchy,
 * windows and subtree densities were built again each time, a map entry per item and a list per
 * parent, 25 KB a frame for 180 items; with the structure kept they are not. Run with escape
 * analysis off, so allocation the JIT would hide still counts.
 */
class CanvasTreeBuildCostTest {
    private val panels = List(PANELS) { CanvasElement().apply { width = 100f; height = 40f } }
    private val marks = List(MARKS) { CanvasElement().apply { width = 10f; height = 10f } }
    private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

    private fun items(): List<CanvasItem> =
        panels.mapIndexed { i, e -> CanvasItem(i + 1, e) } + marks.mapIndexed { i, e -> CanvasItem(PANELS + i + 1, e, panels[i % PANELS]) }

    @Test
    fun anUnchangedCanvasBuildsItsTreeWithoutRebuildingItsHierarchy() {
        val box = CanvasBoxMetrics(WIDTH, HEIGHT, 1f, DPI)
        val kept = CanvasStructure()
        val frames = List(2) { items() }
        fun frame(index: Int) {
            CanvasTree(frames[index and 1], false, emptyMap(), CanvasResolver.None, structure = kept).measureBox(box, 1f)
        }
        repeat(WARMUP) { frame(it) }
        val start = threads.currentThreadAllocatedBytes
        val began = System.nanoTime()
        repeat(FRAMES) { frame(it) }
        val micros = (System.nanoTime() - began) / 1_000 / FRAMES
        val bytes = (threads.currentThreadAllocatedBytes - start) / FRAMES
        println("PERF canvas tree build: $bytes B and $micros us a frame, ${PANELS + MARKS} items")
        assertTrue(bytes <= CEILING_BYTES, "a frame's tree build allocates $bytes B")
    }

    private companion object {
        const val PANELS = 20
        const val MARKS = 160
        const val WIDTH = 1280f
        const val HEIGHT = 720f
        const val DPI = 160f
        const val WARMUP = 3_000
        const val FRAMES = 1_000

        // The tree object and its own fields, about 430 B; a hierarchy built again is 25 KB.
        const val CEILING_BYTES = 1_000L
    }
}
