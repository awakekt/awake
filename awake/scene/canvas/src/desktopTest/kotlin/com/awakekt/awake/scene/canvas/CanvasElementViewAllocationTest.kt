/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.document.SceneNode
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reading an unchanged node's `canvas_element` view again builds nothing (#1621).
 *
 * A node in the component form has its view folded from its components, a `copy()` of the
 * element's 49 fields each. A HUD reading it every frame for every slot built about 130 KB a frame.
 * Each node keeps its own view, so any number of nodes read in turn build nothing after their first
 * read, and a written (so new) node reads its own.
 */
class CanvasElementViewAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private fun bar(value: Float) = SceneNode(
        name = "health",
        components = listOf(
            SceneCanvasRect(width = 120f, height = 12f, elementId = "health"),
            SceneCanvasAppearance(color = "#c0392b"),
            SceneCanvasText(text = "HP"),
            SceneCanvasInput(pressable = true),
            SceneCanvasBar(value = value),
        ),
    )

    @Test
    fun readingAnUnchangedNodeAgainAllocatesNothing() {
        val node = bar(0.5f)
        val first = checkNotNull(node.canvasElementView())
        val thread = Thread.currentThread().id
        // Warm the read path itself, so the measurement counts what a read makes, not class loading.
        repeat(WARM_UP) { node.canvasElementView() }
        val before = threads.getThreadAllocatedBytes(thread)
        var same = 0
        repeat(READS) { if (node.canvasElementView() === first) same++ }
        val allocated = threads.getThreadAllocatedBytes(thread) - before

        println("canvasElementView: $allocated B over $READS reads of an unchanged node")
        // The thread's byte count moves by about a kilobyte on its own; a read that built anything
        // would add hundreds of bytes each, millions over these reads.
        assertTrue(allocated < NOISE, "$READS reads of an unchanged node allocated $allocated B, over $NOISE B")
        assertEquals(READS, same, "every read hands back the first view")
    }

    @Test
    fun manyNodesReadInRotationAllocateNothingOnceEachHasBeenRead() {
        // More nodes than any fixed table would hold, read in turn, as a HUD with many slots reads them.
        val nodes = List(NODES) { bar(it / NODES.toFloat()) }
        val views = nodes.map { checkNotNull(it.canvasElementView()) }
        val thread = Thread.currentThread().id
        repeat(WARM_UP / NODES + 1) { nodes.forEach { it.canvasElementView() } }
        val before = threads.getThreadAllocatedBytes(thread)
        var same = 0
        repeat(ROUNDS) {
            for (index in 0 until NODES) if (nodes[index].canvasElementView() === views[index]) same++
        }
        val allocated = threads.getThreadAllocatedBytes(thread) - before

        println("canvasElementView: $allocated B over $ROUNDS rounds of $NODES distinct nodes")
        assertTrue(allocated < NOISE, "$ROUNDS rounds of $NODES nodes allocated $allocated B, over $NOISE B")
        assertEquals(ROUNDS * NODES, same, "every node hands back its own first view")
    }

    @Test
    fun aChangedNodeReadsItsNewView() {
        val full = bar(1f)
        assertEquals(1f, full.canvasElementView()?.value)
        val half = full.copy(components = full.components.map { if (it is SceneCanvasBar) it.copy(value = 0.5f) else it })
        assertEquals(0.5f, half.canvasElementView()?.value, "a write makes a new node, which reads its own view")
        assertEquals(1f, full.canvasElementView()?.value, "and the old node keeps its own")
        assertTrue(SceneNode(name = "plain").canvasElementView() == null, "a node with no canvas component has no view")
    }

    private companion object {
        const val WARM_UP = 1_000
        const val READS = 10_000
        const val NOISE = 4_096L
        const val NODES = 500
        const val ROUNDS = 20
    }
}
