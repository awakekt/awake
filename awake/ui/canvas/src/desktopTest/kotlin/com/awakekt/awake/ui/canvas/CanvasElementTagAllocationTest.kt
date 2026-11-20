/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What one more element adds to a composed canvas frame's garbage (#1742): the slope between a
 * frame of [FEW] elements and one of [MANY], so the frame's fixed cost drops out. A host that
 * composes every frame, as one with skipping off does, composes each element every frame.
 *
 * Each element's `canvas-element-<id>` test tag was built every time it was composed: the string,
 * and `testTag`'s semantics configuration with its map, per element per frame. 4,359 B for each
 * further element; an item builds its tag modifier once, 4,047 B. The rest is the composed frame
 * itself: the semantics tree rebuilt and each background's outline (#1742). This module's test task
 * turns escape analysis off, so what Android pays is counted.
 */
class CanvasElementTagAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

    @Test
    fun anotherElementAddsNoTagToAComposedFrame() {
        val few = perFrame(items(FEW))
        val many = perFrame(items(MANY))
        val perElement = (many - few) / (MANY - FEW)
        println("CanvasElementTagAllocation: $FEW elements $few B a frame, $MANY elements $many B a frame, $perElement B for each further element")
        assertTrue(perElement < PER_ELEMENT_BUDGET, "each further element allocated $perElement B a composed frame")
    }

    private fun items(count: Int): List<CanvasItem> = List(count) { index ->
        CanvasItem(
            ID_BASE + index,
            CanvasElement().apply {
                width = 8f
                height = 8f
                offsetX = (index % 40) * 10f
                offsetY = (index / 40) * 10f
            },
        )
    }

    private fun perFrame(items: List<CanvasItem>): Long {
        val host = ComposeHost()
        repeat(WARM_UP) { host.frame(INPUT) { CanvasOverlay(items) } }
        val before = threads.currentThreadAllocatedBytes
        repeat(FRAMES) { host.frame(INPUT) { CanvasOverlay(items) } }
        return (threads.currentThreadAllocatedBytes - before) / FRAMES
    }

    private companion object {
        const val FEW = 100
        const val MANY = 400
        const val WARM_UP = 100
        const val FRAMES = 100
        const val ID_BASE = 100_000
        const val PER_ELEMENT_BUDGET = 4_200L
        val INPUT = FrameInput(1280, 720)
    }
}
