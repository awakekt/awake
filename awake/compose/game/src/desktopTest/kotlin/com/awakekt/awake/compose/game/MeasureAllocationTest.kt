/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Measuring a HUD-shaped tree again when nothing changed allocates no more than Row and Column's own
 * per-measure scratch (#1621).
 *
 * The consumer's still HUD measures every composed frame, and `ui:measure` was its largest allocator
 * (842 KB a frame on 0.52.0). This composes [ROWS] stat rows once (padded row, icon box, label,
 * value), then counts the bytes of a full layout pass over the unchanged tree, per node. With escape
 * analysis off, as in this module's test task, so what wasm pays is counted.
 */
class MeasureAllocationTest {
    private val root = LayoutNode(ColumnMeasurePolicy())

    @Test
    fun measuringAnUnchangedHudAllocatesNothingPerNode() {
        composeInto(root) { hud() }
        val nodes = count(root)
        val viewport = Constraints.fixed(WIDTH, HEIGHT)
        val perPass = quietest { root.layoutTree(viewport) }
        val perNode = perPass.toDouble() / nodes
        println("MeasureAllocationTest: $perPass B a pass over $nodes nodes, ${"%.1f".format(perNode)} B a node")
        assertTrue(perNode <= CEILING_PER_NODE, "an unchanged measure allocates $perPass B a pass ($perNode B a node)")
    }

    context(_: Composer)
    private fun hud() {
        Column(Modifier.padding(8.dp)) {
            repeat(ROWS) {
                Row(
                    Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(16.dp))
                    Text("Health")
                    Text("100")
                }
            }
        }
    }

    private fun count(node: LayoutNode): Int {
        var total = 1
        for (i in 0 until node.children.size) total += count(node.children[i])
        return total
    }

    /** The fewest bytes a pass took over [WINDOWS] windows, so a JIT event in one cannot fail it. */
    private inline fun quietest(pass: () -> Unit): Long {
        repeat(WARMUP) { pass() }
        var least = Long.MAX_VALUE
        repeat(WINDOWS) {
            val before = allocated()
            repeat(PASSES) { pass() }
            least = minOf(least, (allocated() - before) / PASSES)
        }
        return least
    }

    private fun allocated(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val ROWS = 40
        const val WIDTH = 800
        const val HEIGHT = 1200
        const val WARMUP = 300
        const val WINDOWS = 5
        const val PASSES = 100

        // 77.7 B a node before #1621's fixes: a placement lambda per modifier node, `Int?` and
        // `(Int, Int) -> Int` boxing, a captured `var`, and an array and lambda per leaf Box. 21.6
        // after: what is left is Row and Column's `Placeable[]` and placement block, which need
        // somewhere per node to keep them. Lower this as that goes.
        const val CEILING_PER_NODE = 24.0
    }
}
