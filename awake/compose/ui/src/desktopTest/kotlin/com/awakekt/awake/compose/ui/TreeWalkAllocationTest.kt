/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Walking a laid-out tree allocates nothing per node (#1512).
 *
 * `NodeChildren.indices` builds an `IntRange` on every read, and the walks over `children` and
 * `layers` read it for every node: painting did so twice per node for each of the 4 layer kinds,
 * about 192 B a node a frame whether or not the tree had a layer. These tests count the bytes of a
 * paint and of a position resolve over [NODES] empty nodes, which draw nothing, so whatever they
 * allocate is the walk. Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native
 * equivalent; the module's test task turns escape analysis off so short-lived objects count.
 */
class TreeWalkAllocationTest {
    private val painter = Painter()
    private val root = LayoutNode(com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy())

    private fun laidOutTree(): LayoutNode {
        composeInto(root) { Box { repeat(NODES) { Box() } } }
        root.layoutTree(Constraints.fixed(WIDTH, HEIGHT))
        return root
    }

    @Test
    fun paintingAllocatesNothingPerNode() {
        val tree = laidOutTree()
        assertTrue(bytesPerFrame { painter.paint(tree) } <= CEILING, "paint allocates per node")
    }

    @Test
    fun resolvingPositionsAllocatesNothingPerNode() {
        val tree = laidOutTree()
        assertTrue(bytesPerFrame { tree.resolveAbsolutePositions() } <= CEILING, "resolveAbsolutePositions allocates per node")
    }

    private inline fun bytesPerFrame(frame: () -> Unit): Long {
        repeat(WARMUP) { frame() }
        val before = allocated()
        repeat(FRAMES) { frame() }
        val perFrame = (allocated() - before) / FRAMES
        println("TreeWalkAllocationTest: $perFrame B/frame over ${NODES + 2} nodes")
        return perFrame
    }

    private fun allocated(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val NODES = 200
        const val WIDTH = 800
        const val HEIGHT = 600
        const val WARMUP = 300
        const val FRAMES = 200

        // A node costs 192 B (8 IntRange of 24 B) with the bug; the ceiling leaves room for a little
        // fixed per-frame work but is far below that, so a walk that allocates per node fails it.
        const val CEILING = 2_000L
    }
}
