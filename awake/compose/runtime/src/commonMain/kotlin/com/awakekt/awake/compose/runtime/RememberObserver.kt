/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

/**
 * A value held by `remember` that wants to know when the composition lets it go, so it can release
 * what it owns: cancel its coroutines, close a store, drop a subscription.
 *
 * The composition calls [onForgotten] once, at the point it drops the value:
 *
 * - a key of its `remember` call changed, so the value was recomputed;
 * - its call stopped being made: the pass trimmed the slot, or dropped the `key` / `rememberGroup`
 *   block that held it;
 * - the node that held it left the tree, or a node above it did;
 * - its [Composition] was disposed.
 *
 * A value remembered in two slots is told once per slot. Values in the same node are told in the
 * reverse of the order they were remembered, and a node's descendants before the node itself.
 * Nothing calls it while the value stays remembered, so a pass that keeps every value costs nothing.
 *
 * ```kotlin
 * class Ticker : RememberObserver {
 *     val scope = CoroutineScope(SupervisorJob())
 *     override fun onForgotten() = scope.cancel()
 * }
 *
 * val ticker = remember { Ticker() }
 * ```
 */
fun interface RememberObserver {
    /** Called once when the composition drops this value. Release what it owns here. */
    fun onForgotten()
}

/**
 * Tells everything [slot] holds that it was forgotten: a remembered value, or every value of a slot
 * group, last first.
 */
internal fun forgetSlot(slot: Any?) {
    when (slot) {
        is RememberedSlot -> forgetValue(slot.value)
        is RememberedGroup -> forgetSlots(slot.slots)
    }
}

/** Tells [value] it was forgotten, if it asked to be told. */
internal fun forgetValue(value: Any?) {
    if (value is RememberObserver) value.onForgotten()
}

/** Forgets every slot in [slots], last first, and empties the list. */
internal fun forgetSlots(slots: MutableList<Any?>) {
    while (slots.isNotEmpty()) forgetSlot(slots.removeAt(slots.lastIndex))
}

/**
 * Forgets what the children of the current node in [slot] remember, from [from] to the end, before
 * the caller truncates them: each child's descendants, then the child's own slots.
 *
 * Walks through the [Applier] the composer already drives, so no applier has to know about
 * remembered values. The walk only happens when there is something to drop; a pass that keeps every
 * child pays one [Applier.childCount] call.
 */
internal fun Applier.forgetChildrenFrom(slot: Slot, from: Int) {
    for (index in childCount(slot) - 1 downTo from) {
        val node = nodeAt(slot, index)
        down(slot, index)
        try {
            forgetChildrenFrom(Slot.Children, 0)
            forgetChildrenFrom(Slot.Layers, 0)
        } finally {
            up()
        }
        if (node is RememberHolder) forgetSlots(node.mutableSlots())
    }
}
