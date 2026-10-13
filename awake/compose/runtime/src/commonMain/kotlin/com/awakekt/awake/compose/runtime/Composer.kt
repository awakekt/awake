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

    internal val locals = parentLocals?.snapshot() ?: ProvidedLocals()

    /**
     * A copy of the composition locals open at this point in the pass, for a composition that starts
     * here -- a subcomposition measured later still reads the theme its parent provided.
     */
    val localsSnapshot: ProvidedLocals get() = locals.snapshot()

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
        val index = cursor(slot)
        // Not consumed: a `key(x) { }` block scopes every sibling declared inside it, the way
        // Compose's key group does -- not just the first one.
        val key = pendingKey

        val existing = findReusable(slot, index, type, key)
        when {
            existing < 0 -> applier.createAt(slot, index, type, key)
            existing != index -> applier.moveTo(slot, existing, index)
        }
        val instance = applier.nodeAt(slot, index)
        update(instance)

        if (content != null) {
            applier.down(slot, index)
            openSlots(instance)
            pushDepth()
            // Descendants get their own identity from their own position. An enclosing key applies
            // to the siblings it wraps, not to everything underneath them.
            val enclosingKey = pendingKey
            pendingKey = null
            content()
            pendingKey = enclosingKey
            // Whatever this pass stopped declaring is gone; anything left is stale.
            applier.truncateFrom(Slot.Children, cursor(Slot.Children))
            applier.truncateFrom(Slot.Layers, cursor(Slot.Layers))
            closeSlots()
            popDepth()
            applier.up()
        }
        advance(slot)
    }

    /**
     * Gives everything declared inside [block] an identity tied to [value] rather than its position:
     * the nodes it declares, and the values it remembers, which get a slot group of their own.
     *
     * Needed whenever a list can reorder or a branch can come and go: without it the node at index 3
     * stays the node at index 3, and its state follows the slot instead of the item.
     */
    fun <T> key(value: Any, block: () -> T): T {
        val previous = pendingKey
        pendingKey = value
        startGroup(value)
        try {
            return block()
        } finally {
            endGroup()
            pendingKey = previous
        }
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

    /**
     * The value held in the next slot of the current node or group, computed on the pass that first
     * reached it, and again whenever a key changes or the slot holds something [isT] refuses.
     *
     * Slots belong to the enclosing node, so they survive exactly as long as it does. A node with no
     * store -- a test double, say -- cannot remember, and says so rather than silently recomputing.
     *
     * Two keys are stored side by side rather than combined into one. Combining them -- a `Pair`, a
     * vararg array -- would allocate per call per pass, and `remember` runs in the per-frame path
     * this engine exists to keep allocation-free. Two fields cost nothing.
     */
    @PublishedApi
    internal fun <T> remember(key1: Any?, key2: Any?, isT: (Any?) -> Boolean, calculate: () -> T): T {
        val top = slotOwners.lastIndex
        val slots = slotsOf(slotOwners[top])
            ?: error("remember() needs an enclosing node that implements RememberHolder")
        val index = slotCursors[top]
        slotCursors[top] = index + 1
        val slot = slots.getOrNull(index) as? RememberedSlot ?: claimSlot(slots, index)
        if (slot == null) {
            val fresh = RememberedSlot(key1, key2, calculate())
            slots.add(index, fresh)
            @Suppress("UNCHECKED_CAST")
            return fresh.value as T
        }
        // A value of another type is another call's: the calls before this one changed. Recompute
        // rather than hand it over and fail with a ClassCastException at the caller.
        if (slot.key != key1 || slot.key2 != key2 || !isT(slot.value)) {
            slot.key = key1
            slot.key2 = key2
            slot.value = calculate()
        }
        @Suppress("UNCHECKED_CAST")
        return slot.value as T
    }

    internal fun finishRoot() {
        applier.truncateFrom(Slot.Children, cursor(Slot.Children))
        applier.truncateFrom(Slot.Layers, cursor(Slot.Layers))
        slotsOf(slotOwners[0])?.trimTo(slotCursors[0])
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
fun <T> key(value: Any, block: () -> T): T = composer.key(value, block)

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
    composer.node(type, slot, update, content?.let { { it(composer) } })
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
): Composer = Composition(applier, root).reconcile(content)

/** As [reconcile], for a root that never remembers anything itself. */
fun reconcile(applier: Applier, content: context(Composer) () -> Unit): Composer =
    reconcile(applier, null, content)

/**
 * Retains `remember` slots and node identity across root passes.
 *
 * The free [reconcile] function stays one-shot, for tests and small callers that build a tree once.
 * A host that composes every frame owns a Composition and calls this method each time.
 */
class Composition(
    applier: Applier,
    root: Any? = null,
    parentLocals: ProvidedLocals? = null,
) {
    private val composer = Composer(applier, root, parentLocals)

    /**
     * Runs one root pass of [content] against the retained tree: reuses what matches, creates what is
     * new, drops what this pass no longer declares, and returns the composer it ran with.
     */
    fun reconcile(content: context(Composer) () -> Unit): Composer {
        composer.beginRoot()
        content(composer)
        composer.finishRoot()
        return composer
    }
}
