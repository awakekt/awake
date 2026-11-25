/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

/**
 * Matches one pass's declarations against the nodes the previous pass left behind.
 *
 * Identity is positional: a declaration is the same node as last pass if it sits at the same index
 * under the same parent and declares the same type. That is what the Compose compiler plugin
 * generates keys for, done with a counter instead -- see the README's calling-convention section
 * for why this engine has no plugin.
 *
 * Positional identity breaks the same way React's index keys do: reorder a list and state follows
 * the slot rather than the item. [key] is the same escape hatch, and it is a runtime function in
 * Compose too, not a compiler feature. It keys the nodes its block declares and gives the block's
 * `remember` calls their own slot group, so both follow the key rather than the position.
 */
class Composer internal constructor(
    private val applier: Applier,
    root: Any? = null,
    parentLocals: ProvidedLocals? = null,
) {
    /** Nodes declared by every pass so far, reused or new: a host reads it before and after a pass. */
    var emittedNodes: Int = 0
        private set

    // One cursor per depth, per slot. Grown once and reused, so composing allocates nothing here.
    private var childCursors = IntArray(INITIAL_DEPTH)
    private var layerCursors = IntArray(INITIAL_DEPTH)
    private var depth = 0
    private var pendingKey: Any? = null

    // Where `remember` stores, innermost last: the node whose content is executing, or a slot group
    // open inside it, each with its cursor. The applier's own cursor cannot serve: an accessor there
    // would put the tree back into the reconciler's interface, which is what keeps `:runtime` free of
    // layout. An owner that cannot remember is held as null.
    private val slotOwners = ArrayList<Any?>().apply { add(root) }
    private var slotCursors = IntArray(INITIAL_DEPTH)

    @PublishedApi
    internal val locals: ProvidedLocals = parentLocals?.snapshot() ?: ProvidedLocals()

    /**
     * A copy of the composition locals open at this point in the pass, for a composition that starts
     * here -- a subcomposition measured later still reads the theme its parent provided.
     */
    val localsSnapshot: ProvidedLocals get() = locals.snapshot()

    // The node [startNode] opened, until [endNode] closes it. One at a time: a node's content is declared inside
    // [endNode], so a caller never opens the next node before ending this one.
    private var openSlot: Slot = Slot.Children
    private var openIndex = -1
    private var openExisting = -1
    private var openInstance: Any? = null

    /**
     * Declares a node, reusing the one at this position if it matches.
     *
     * [update] runs against the node every pass -- reused or fresh -- because a modifier or policy
     * can change without the node's identity changing.
     */
    fun node(
        type: Any,
        slot: Slot = Slot.Children,
        update: (Any) -> Unit = {},
        content: (() -> Unit)? = null,
    ) {
        update(startNode(type, slot))
        endNode(content)
    }

    /**
     * [node] in two halves, as Compose's own `Composer` declares a node: this finds or creates the node at this
     * position and returns it, the caller sets what changed on it, then calls [endNode] with its content. A caller
     * that sets the node's properties itself builds no `update` lambda, which [node] needs a capturing one for on
     * every call of every pass (#1813).
     */
    fun startNode(type: Any, slot: Slot): Any {
        val index = cursor(slot)
        // Not consumed: a `key(x) { }` block scopes every sibling declared inside it, the way
        // Compose's key group does -- not just the first one.
        val key = pendingKey

        emittedNodes++
        val existing = findReusable(slot, index, type, key)
        when {
            existing < 0 -> applier.createAt(slot, index, type, key)
            existing != index -> applier.moveTo(slot, existing, index)
        }
        val instance = applier.nodeAt(slot, index)
        openSlot = slot
        openIndex = index
        openExisting = existing
        openInstance = instance
        return instance
    }

    /**
     * Descends into the node [startNode] opened so callers can compose children directly without
     * allocating a wrapper lambda, then close it with [exitNode].
     */
    fun enterNode(): Any? {
        val instance = checkNotNull(openInstance) { "enterNode without a startNode" }
        val slot = openSlot
        val index = openIndex
        openInstance = null
        applier.down(slot, index)
        openSlots(instance)
        pushDepth()
        // Descendants get their own identity from their own position. An enclosing key applies
        // to the siblings it wraps, not to everything underneath them.
        val enclosingKey = pendingKey
        pendingKey = null
        return enclosingKey
    }

    /** Closes the node [enterNode] opened, dropping any children or slots this pass stopped declaring. */
    fun exitNode(slot: Slot, enclosingKey: Any?) {
        pendingKey = enclosingKey
        // Whatever this pass stopped declaring is gone; anything left is stale.
        dropFrom(Slot.Children, cursor(Slot.Children))
        dropFrom(Slot.Layers, cursor(Slot.Layers))
        closeSlots()
        popDepth()
        applier.up()
        advance(slot)
    }

    /** Ends a childless node [startNode] opened, dropping any stale children, layers, and slots it held. */
    fun endEmptyNode() {
        val instance = checkNotNull(openInstance) { "endEmptyNode without a startNode" }
        val slot = openSlot
        val index = openIndex
        val existing = openExisting
        openInstance = null
        if (existing >= 0) {
            applier.down(slot, index)
            try {
                dropFrom(Slot.Children, 0)
                dropFrom(Slot.Layers, 0)
            } finally {
                applier.up()
            }
            slotsOf(instance)?.trimTo(0)
        }
        advance(slot)
    }

    /** Ends the node [startNode] opened, composing [content] into it, or emptying it when there is none. */
    fun endNode(content: (() -> Unit)?) {
        if (content != null) {
            val slot = openSlot
            val enclosingKey = enterNode()
            content()
            exitNode(slot, enclosingKey)
        } else {
            endEmptyNode()
        }
    }

    /**
     * Gives everything declared inside [block] an identity tied to [value] rather than its position:
     * the nodes it declares, and the values it remembers, which get a slot group of their own.
     *
     * Needed whenever a list can reorder or a branch can come and go: without it the node at index 3
     * stays the node at index 3, and its state follows the slot instead of the item.
     */
    inline fun <T> key(value: Any, block: () -> T): T {
        val previous = startKey(value)
        try {
            return block()
        } finally {
            endKey(previous)
        }
    }

    @PublishedApi
    internal fun startKey(value: Any): Any? {
        val previous = pendingKey
        pendingKey = value
        startGroup(value)
        return previous
    }

    @PublishedApi
    internal fun endKey(previous: Any?) {
        endGroup()
        pendingKey = previous
    }

    /**
     * Opens a slot group keyed by [key] at this point of the current node's `remember` slots.
     *
     * The group takes one slot of its owner whatever it remembers, and is found by key: a group that
     * moved is moved back, and one not found is inserted, so the slots after it keep lining up.
     */
    @PublishedApi
    internal fun startGroup(key: Any) {
        val top = slotOwners.lastIndex
        val slots = slotsOf(slotOwners[top])
        var group: RememberedGroup? = null
        if (slots != null) {
            group = claimGroup(slots, slotCursors[top], key)
            slotCursors[top]++
        }
        openSlots(group)
    }

    /** Closes the group [startGroup] opened, dropping what it stopped remembering. */
    @PublishedApi
    internal fun endGroup() = closeSlots()

    /**
     * The index of a reusable node, or -1 to create one.
     *
     * Keyed declarations scan forward so a moved item is found and moved back into place rather
     * than rebuilt. Unkeyed ones only ever match the exact slot -- scanning would let an unrelated
     * node of the same type be adopted, which is worse than recreating.
     */
    private fun findReusable(slot: Slot, index: Int, type: Any, key: Any?): Int {
        val count = applier.childCount(slot)
        var found = -1
        if (key == null) {
            val matches = index < count &&
                applier.typeAt(slot, index) == type &&
                applier.keyAt(slot, index) == null
            if (matches) found = index
        } else {
            var i = index
            while (i < count && found < 0) {
                if (applier.keyAt(slot, i) == key && applier.typeAt(slot, i) == type) found = i
                i++
            }
        }
        return found
    }

    @PublishedApi
    internal fun claimRememberIndex(): Int {
        val top = slotOwners.lastIndex
        val slots = slotsOf(slotOwners[top])
            ?: error("remember() needs an enclosing node that implements RememberHolder")
        val index = slotCursors[top]
        slotCursors[top] = index + 1
        val existing = slots.getOrNull(index) as? RememberedSlot ?: claimSlot(slots, index)
        if (existing == null) {
            slots.add(index, RememberedSlot(Unkeyed, Unkeyed, Unkeyed, RememberMiss))
        }
        return index
    }

    @PublishedApi
    internal fun readRemember(index: Int, key1: Any?, key2: Any?, key3: Any?): Any? {
        val slots = slotsOf(slotOwners[slotOwners.lastIndex])!!
        val slot = slots[index] as RememberedSlot
        val value = slot.value
        val keysMatch = sameKey(slot.key, key1) && sameKey(slot.key2, key2) && sameKey(slot.key3, key3)
        return if (value === RememberMiss || !keysMatch) RememberMiss else value
    }

    @PublishedApi
    internal fun writeRemember(index: Int, key1: Any?, key2: Any?, key3: Any?, value: Any?) {
        val slots = slotsOf(slotOwners[slotOwners.lastIndex])!!
        val slot = slots[index] as RememberedSlot
        val previous = slot.value
        slot.key = key1
        slot.key2 = key2
        slot.key3 = key3
        slot.value = value
        if (previous !== RememberMiss && previous !== value) forgetValue(previous)
    }

    /**
     * Binary-compatible entry point for callers compiled against earlier inline `remember` overloads.
     * Current inline `remember` calls [claimRememberIndex], [readRemember], and [writeRemember] so
     * steady-state hits allocate neither a [calculate] closure nor an [isT] predicate.
     */
    @PublishedApi
    internal fun <T> remember(key1: Any?, key2: Any?, isT: (Any?) -> Boolean, calculate: () -> T): T {
        val index = claimRememberIndex()
        val cached = readRemember(index, key1, key2, Unkeyed)
        if (cached === RememberMiss || !isT(cached)) {
            val fresh = calculate()
            writeRemember(index, key1, key2, Unkeyed, fresh)
            return fresh
        }
        @Suppress("UNCHECKED_CAST")
        return cached as T
    }

    private fun sameKey(a: Any?, b: Any?): Boolean = a === b || a == b

    internal fun finishRoot() {
        dropFrom(Slot.Children, cursor(Slot.Children))
        dropFrom(Slot.Layers, cursor(Slot.Layers))
        slotsOf(slotOwners[0])?.trimTo(slotCursors[0])
    }

    /** Drops every node and remembered value, telling each [RememberObserver] it was forgotten. */
    internal fun disposeRoot() {
        beginRoot()
        finishRoot()
    }

    /**
     * Truncates the current node's children in [slot] from [from], after telling what they and their
     * descendants remember that it was forgotten. The children go even if a value throws.
     */
    private fun dropFrom(slot: Slot, from: Int) {
        try {
            applier.forgetChildrenFrom(slot, from)
        } finally {
            applier.truncateFrom(slot, from)
        }
    }

    /** Prepares this retained composer for its next root pass. */
    internal fun beginRoot() {
        depth = 0
        childCursors[0] = 0
        layerCursors[0] = 0
        while (slotOwners.size > 1) slotOwners.removeAt(slotOwners.lastIndex)
        slotCursors[0] = 0
        pendingKey = null
    }

    private fun openSlots(owner: Any?) {
        slotOwners.add(owner)
        val top = slotOwners.lastIndex
        if (top == slotCursors.size) slotCursors = slotCursors.copyOf(top * 2)
        slotCursors[top] = 0
    }

    /** Pops the innermost owner, dropping the slots this pass no longer reached. */
    private fun closeSlots() {
        val top = slotOwners.lastIndex
        slotsOf(slotOwners[top])?.trimTo(slotCursors[top])
        slotOwners.removeAt(top)
    }

    private fun slotsOf(owner: Any?): MutableList<Any?>? = when (owner) {
        is RememberedGroup -> owner.slots
        is RememberHolder -> owner.mutableSlots()
        else -> null
    }

    private fun cursor(slot: Slot): Int =
        if (slot == Slot.Children) childCursors[depth] else layerCursors[depth]

    private fun advance(slot: Slot) {
        if (slot == Slot.Children) childCursors[depth]++ else layerCursors[depth]++
    }

    private fun pushDepth() {
        depth++
        if (depth == childCursors.size) {
            childCursors = childCursors.copyOf(depth * 2)
            layerCursors = layerCursors.copyOf(depth * 2)
        }
        childCursors[depth] = 0
        layerCursors[depth] = 0
    }

    private fun popDepth() {
        depth--
    }

    private companion object {
        const val INITIAL_DEPTH = 16
    }
}

context(composer: Composer)
/** Declares [block]'s nodes under [value]'s identity rather than their position. */
inline fun <T> key(value: Any, block: () -> T): T = composer.key(value, block)

context(composer: Composer)
/**
 * Declares a node, reusing the one at this position if it matches.
 *
 * The free-function form. A [Composer] arrives as a context parameter rather than a receiver, so its
 * members are not in scope inside a composable -- everything callable from one is declared like
 * this.
 */
fun node(
    type: Any,
    slot: Slot = Slot.Children,
    update: (Any) -> Unit = {},
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    update(composer.startNode(type, slot))
    if (content != null) {
        val enclosingKey = composer.enterNode()
        content(composer)
        composer.exitNode(slot, enclosingKey)
    } else {
        composer.endEmptyNode()
    }
}

/**
 * Runs [content] against the tree [applier] addresses, reusing what already matches.
 *
 * The entry point exists because [content] needs a [Composer] that does not exist yet at the call
 * site -- a context parameter in the function type supplies one, which is what makes
 * `reconcile(applier) { App() }` work from ordinary code.
 *
 * [root] is the node [applier] starts at, and only `remember` needs it: pass it and the top level of
 * [content] can remember. An overload without it exists rather than a default, because a defaulted
 * middle parameter would let `reconcile(applier, content)` bind the lambda to [root] and compose
 * nothing.
 */
fun reconcile(
    applier: Applier,
    root: Any?,
    content: context(Composer) () -> Unit,
): Composer {
    val composition = Composition(applier, root)
    val composer = composition.reconcile(content)
    // Nothing keeps a one-shot composition, so the states it read must not keep it either.
    composition.forgetReads()
    return composer
}

/** As [reconcile], for a root that never remembers anything itself. */
fun reconcile(applier: Applier, content: context(Composer) () -> Unit): Composer =
    reconcile(applier, null, content)
