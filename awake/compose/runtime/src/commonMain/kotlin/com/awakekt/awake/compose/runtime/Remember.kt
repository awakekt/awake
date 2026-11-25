/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

/**
 * A node that can carry `remember` slots across passes.
 *
 * The store lives on the node rather than in a side table keyed by id. That is the difference from
 * `ui-core`'s `WidgetState`, whose string keys could collide silently and whose entries outlived the
 * widget that wrote them: when this node goes, everything it remembered goes with it.
 */
interface RememberHolder {
    /**
     * This node's remembered values, in the order its content called `remember`. Back it with a
     * `MutableList`: the composer appends a slot the first time a call reaches it and trims the ones
     * a pass no longer declares.
     */
    val rememberSlots: List<Any?>
}

@Suppress("UNCHECKED_CAST")
internal fun RememberHolder.mutableSlots(): MutableList<Any?> =
    (rememberSlots as? MutableList<Any?>)
        ?: error("RememberHolder implementation must back rememberSlots with a MutableList")

context(composer: Composer)
/**
 * Computes [calculate] on the first pass and returns that same value on every pass after.
 *
 * **Slots are positional within the enclosing node**, in call order, so a call that comes or goes
 * partway through a node shifts the slots of every call after it. Two guards keep that from handing
 * one call another's value:
 *
 * - A slot holding a value that is not a [T] is recomputed rather than cast, so a shift between
 *   calls of different types costs that state instead of throwing a `ClassCastException`.
 * - [key] gives its block a slot group of its own, found by key, which takes one slot of the node
 *   whatever it remembers. Wrap a branch that remembers, or each item of a list, in `key(...)`, and
 *   its state follows the key, and the calls after it keep their slots.
 *
 * A shift between calls of the same type still hands the value over silently, so a branch or a
 * variable-length sequence that remembers must be keyed. A component that remembers wraps its body
 * in [rememberGroup], so its state does not depend on what its caller draws around it.
 *
 * [calculate] is inlined at the call site, so a steady-state cache hit allocates neither a closure
 * nor a type-check lambda.
 */
inline fun <reified T> remember(calculate: () -> T): T {
    val index = composer.claimRememberIndex()
    val cached = composer.readRemember(index, Unkeyed, Unkeyed, Unkeyed)
    if (cached === RememberMiss || cached !is T) {
        val fresh = calculate()
        composer.writeRemember(index, Unkeyed, Unkeyed, Unkeyed, fresh)
        return fresh
    }
    return cached
}

context(composer: Composer)
/**
 * Recomputes [calculate] whenever [key] changes, by `==`.
 *
 * Deliberately not a vararg: `remember(a, b)` would allocate an array on every pass, and per-frame
 * allocation is the cost this engine exists to remove. Fixed-arity overloads cover up to three keys.
 * [calculate] is inlined, so an unchanged pass allocates no lambda.
 */
inline fun <reified T> remember(key: Any?, calculate: () -> T): T {
    val index = composer.claimRememberIndex()
    val cached = composer.readRemember(index, key, Unkeyed, Unkeyed)
    if (cached === RememberMiss || cached !is T) {
        val fresh = calculate()
        composer.writeRemember(index, key, Unkeyed, Unkeyed, fresh)
        return fresh
    }
    return cached
}

context(composer: Composer)
/**
 * Recomputes [calculate] whenever either key changes, by `==`.
 *
 * The overload the single-key doc predicted. A shadcn style is the case that needed it: it reads the
 * theme *and* switches on a variant, so keying on either alone goes stale -- a variant change with a
 * fixed theme, or a theme change with a fixed variant, would each return the other's last value.
 *
 * Still not a vararg, for the reason stated above: the keys are stored side by side in the slot,
 * and [calculate] is inlined so an unchanged pass allocates nothing.
 */
inline fun <reified T> remember(key1: Any?, key2: Any?, calculate: () -> T): T {
    val index = composer.claimRememberIndex()
    val cached = composer.readRemember(index, key1, key2, Unkeyed)
    if (cached === RememberMiss || cached !is T) {
        val fresh = calculate()
        composer.writeRemember(index, key1, key2, Unkeyed, fresh)
        return fresh
    }
    return cached
}

context(composer: Composer)
/**
 * Recomputes [calculate] whenever any of [key1], [key2], or [key3] changes, by `==`.
 *
 * Mirrors `androidx.compose.runtime.remember(key1, key2, key3, calculation)` without allocating a
 * vararg array or a [calculate] closure on steady-state passes.
 */
inline fun <reified T> remember(key1: Any?, key2: Any?, key3: Any?, calculate: () -> T): T {
    val index = composer.claimRememberIndex()
    val cached = composer.readRemember(index, key1, key2, key3)
    if (cached === RememberMiss || cached !is T) {
        val fresh = calculate()
        composer.writeRemember(index, key1, key2, key3, fresh)
        return fresh
    }
    return cached
}

context(composer: Composer)
/**
 * Gives the `remember` calls inside [block] a slot group of their own, keyed by [key], without
 * changing which node anything inside reuses.
 *
 * For a component's own state: wrapped, it takes one slot of its caller's node however many values it
 * remembers, and the group is found by [key], so a component that comes or goes beside it leaves its
 * state where it was. Use a key private to the component. [key] is the call for a caller's branch or
 * list, since it keys the nodes too; a component must not use it, or it would replace the key its
 * caller gave the node.
 */
inline fun <T> rememberGroup(key: Any, block: () -> T): T {
    composer.startGroup(key)
    try {
        return block()
    } finally {
        composer.endGroup()
    }
}

/** Sentinel for unused key positions -- never equal to anything a caller can pass. */
@PublishedApi
internal object Unkeyed

/** Sentinel returned by [Composer.readRemember] on a cache miss, distinguishing misses from a cached `null`. */
@PublishedApi
internal object RememberMiss

internal class RememberedSlot(var key: Any?, var key2: Any?, var key3: Any?, var value: Any?)

/** A [key] or [rememberGroup] block's own slots, held as one slot of the node or group around it. */
internal class RememberedGroup(val key: Any) {
    val slots: MutableList<Any?> = ArrayList()
}

/**
 * The group keyed [key] for slot [index] of [slots]: there already, moved back from later in the
 * list, or inserted new. The slots it passes over stay where they are for later calls to claim.
 */
internal fun claimGroup(slots: MutableList<Any?>, index: Int, key: Any): RememberedGroup {
    for (i in index until slots.size) {
        val group = slots[i] as? RememberedGroup ?: continue
        if (group.key == key) {
            if (i != index) slots.add(index, slots.removeAt(i))
            return group
        }
    }
    return RememberedGroup(key).also { slots.add(index, it) }
}

/**
 * The plain slot for [index] when a group sits there -- one whose block did not run this pass, or
 * not yet: the next plain slot after it, moved back to [index], or null for a new one. The groups
 * it passes over stay for a later [claimGroup], so a branch that remembers and comes back finds them.
 */
internal fun claimSlot(slots: MutableList<Any?>, index: Int): RememberedSlot? {
    for (i in index + 1 until slots.size) {
        val slot = slots[i] as? RememberedSlot ?: continue
        slots.add(index, slots.removeAt(i))
        return slot
    }
    return null
}

/**
 * Drops slots this pass stopped declaring, the way [Applier.truncateFrom] drops children, telling
 * each value it was forgotten, last first.
 */
internal fun MutableList<Any?>.trimTo(count: Int) {
    while (size > count) forgetSlot(removeAt(lastIndex))
}
