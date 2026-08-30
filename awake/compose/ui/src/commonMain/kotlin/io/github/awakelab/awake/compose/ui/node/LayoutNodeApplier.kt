/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.node

import io.github.awakelab.awake.compose.runtime.Applier
import io.github.awakelab.awake.compose.runtime.Slot
import io.github.awakelab.awake.compose.ui.layout.Measurable
import io.github.awakelab.awake.compose.ui.layout.MeasurePolicy
import io.github.awakelab.awake.compose.ui.layout.MeasureResult
import io.github.awakelab.awake.compose.ui.layout.MeasureScope
import io.github.awakelab.awake.compose.ui.unit.Constraints

/**
 * Applies the reconciler's decisions to a [LayoutNode] tree.
 *
 * The one place that knows the node type, which is what keeps `Composer` non-generic and a
 * composable's signature `context(_: Composer)`.
 */
class LayoutNodeApplier(root: LayoutNode) : Applier {

    private val stack = mutableListOf<LayoutNode>()
    private var current: LayoutNode = root

    private fun slotOf(slot: Slot): NodeChildren =
        if (slot == Slot.Children) current.children else current.layers

    override fun childCount(slot: Slot): Int = slotOf(slot).size

    override fun typeAt(slot: Slot, index: Int): Any? = slotOf(slot)[index].nodeType

    override fun keyAt(slot: Slot, index: Int): Any? = slotOf(slot)[index].nodeKey

    override fun nodeAt(slot: Slot, index: Int): Any = slotOf(slot)[index]

    override fun createAt(slot: Slot, index: Int, type: Any, key: Any?): Any {
        // The policy is a placeholder: the reconciler's update callback assigns the real one on
        // this same pass, before anything measures.
        val node = LayoutNode(EmptyMeasurePolicy, current.density, current.fontScale)
        node.nodeType = type
        node.nodeKey = key
        slotOf(slot).insertAt(index, node)
        return node
    }

    override fun moveTo(slot: Slot, from: Int, to: Int) {
        slotOf(slot).move(from, to)
    }

    override fun truncateFrom(slot: Slot, index: Int) {
        slotOf(slot).truncateFrom(index)
    }

    override fun down(slot: Slot, index: Int) {
        stack += current
        current = slotOf(slot)[index]
    }

    override fun up() {
        current = stack.removeAt(stack.lastIndex)
    }
}

/** Sizes to the smallest the constraints allow and places nothing. Replaced during reconcile. */
internal object EmptyMeasurePolicy : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult = layout(constraints.minWidth, constraints.minHeight) {}
}
