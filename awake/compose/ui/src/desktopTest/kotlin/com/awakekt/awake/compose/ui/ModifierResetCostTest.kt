/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.draw.zIndex
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Re-setting a node's modifier with one of the same shape, as a mark that moves does every frame,
 * rebuilt the chain: sixteen lists cleared and refilled and the fold's locals boxed, per node. It now
 * updates the retained nodes in place. Run with escape analysis off, so the boxes still count.
 */
class ModifierResetCostTest {
    private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

    @Test
    fun reSettingAModifierOfTheSameShapeAllocatesNothing() {
        val nodes = List(NODES) { LayoutNode(ColumnMeasurePolicy()) }
        val modifiers = List(2) { pass -> List(NODES) { Modifier.offset(x = (it + pass).dp).size(8.dp).zIndex(pass.toFloat()) } }
        var pass = 0
        fun frame() {
            pass = pass xor 1
            val next = modifiers[pass]
            for (i in nodes.indices) nodes[i].modifier = next[i]
        }
        repeat(WARMUP) { frame() }
        val bytes = threads.currentThreadAllocatedBytes
        val start = System.nanoTime()
        repeat(FRAMES) { frame() }
        val ns = (System.nanoTime() - start) / (FRAMES * NODES)
        val perSet = (threads.currentThreadAllocatedBytes - bytes) / (FRAMES * NODES)
        println("PERF modifier re-set: $perSet B and $ns ns a node")
        assertTrue(perSet == 0L, "re-setting a modifier of the same shape allocates $perSet B a node")
    }

    @Test
    fun aHudOfMovingMarks() {
        val host = ComposeHost()
        var tick = 0
        fun frame() {
            tick += 1
            host.frame(FrameInput(WIDTH, HEIGHT)) {
                Box(Modifier.fillMaxSize()) {
                    for (i in 0 until NODES) {
                        Box(Modifier.offset(x = ((i * 7 + tick) % WIDTH).dp, y = (i * 3 % HEIGHT).dp).size(8.dp).zIndex(1f))
                    }
                }
            }
        }
        repeat(WARMUP) { frame() }
        val bytes = threads.currentThreadAllocatedBytes
        val start = System.nanoTime()
        repeat(FRAMES) { frame() }
        val us = (System.nanoTime() - start) / 1_000 / FRAMES
        println("PERF moving marks: ${(threads.currentThreadAllocatedBytes - bytes) / FRAMES} B and $us us a frame, $NODES marks")
    }

    private companion object {
        const val NODES = 300
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val WARMUP = 500
        const val FRAMES = 500
    }
}
