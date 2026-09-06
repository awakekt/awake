/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

/** A tree of nothing in particular -- no layout, no UI. */
internal class FakeNode(val type: Any = "root", val key: Any? = null) : RememberHolder {
    val children = mutableListOf<FakeNode>()
    val layers = mutableListOf<FakeNode>()
    override val rememberSlots: MutableList<Any?> = mutableListOf()
    var updates = 0
    var value: Any? = null
}

internal class FakeApplier(val root: FakeNode) : Applier {
    private val stack = mutableListOf<FakeNode>()
    private var current = root

    private fun slotOf(slot: Slot) = if (slot == Slot.Children) current.children else current.layers

    override fun childCount(slot: Slot) = slotOf(slot).size
    override fun typeAt(slot: Slot, index: Int): Any = slotOf(slot)[index].type
    override fun keyAt(slot: Slot, index: Int): Any? = slotOf(slot)[index].key
    override fun nodeAt(slot: Slot, index: Int): Any = slotOf(slot)[index]

    override fun createAt(slot: Slot, index: Int, type: Any, key: Any?): Any =
        FakeNode(type, key).also { slotOf(slot).add(index.coerceAtMost(slotOf(slot).size), it) }

    override fun moveTo(slot: Slot, from: Int, to: Int) {
        val list = slotOf(slot)
        list.add(to, list.removeAt(from))
    }

    override fun truncateFrom(slot: Slot, index: Int) {
        val list = slotOf(slot)
        while (list.size > index) list.removeAt(list.size - 1)
    }

    override fun down(slot: Slot, index: Int) {
        stack += current
        current = slotOf(slot)[index]
    }

    override fun up() {
        current = stack.removeAt(stack.lastIndex)
    }
}
