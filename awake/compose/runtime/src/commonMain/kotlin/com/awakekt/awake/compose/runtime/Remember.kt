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
    val rememberSlots: MutableList<Any?>
}

/**
 * Computes [calculate] on the first pass and returns that same value on every pass after.
 *
 * **Slots are positional within the enclosing node**, and `key(...)` is *not* the escape hatch --
 * a key fixes which node an item reuses, and this value does not live in that node, it lives in the
 * nearest enclosing one, indexed by call order. The two are separate mechanisms.
 *
 * So: nothing may `remember` after a variable-length sequence in the same node. Add or remove one
 * item and every slot after it shifts by one -- silently handing the wrong value over when the
 * types match, and throwing a `ClassCastException` from the slot table when they do not. Give each
 * repeated item its own node (a `Column`, a `Box`) so its slots are its own, *and* key that node so
 * an inserted sibling does not adopt it by position.
 *
 * The same applies to a branch: a subtree that remembers must not be conditionally composed. Draw
 * the control always and disable it, or hoist the state above the branch. A conditional subtree
 * that remembers nothing is fine -- it is the slot that moves, not the node.
 */
context(composer: Composer)
fun <T> remember(calculate: () -> T): T = composer.remember(Unkeyed, calculate)

/**
 * Recomputes [calculate] whenever [key] changes, by `==`.
 *
 * Deliberately not a vararg: `remember(a, b)` would allocate an array on every pass, and per-frame
 * allocation is the cost this engine exists to remove. Add a two-key overload when something needs
 * one.
 */
context(composer: Composer)
fun <T> remember(key: Any?, calculate: () -> T): T = composer.remember(key, calculate)

/**
 * Recomputes [calculate] whenever either key changes, by `==`.
 *
 * The overload the single-key doc predicted. A shadcn style is the case that needed it: it reads the
 * theme *and* switches on a variant, so keying on either alone goes stale -- a variant change with a
 * fixed theme, or a theme change with a fixed variant, would each return the other's last value.
 *
 * Still not a vararg, for the reason stated above: the two keys are stored side by side in the slot,
 * so this allocates nothing. A vararg array -- or a `Pair` to combine them -- would, per call per pass.
 */
context(composer: Composer)
fun <T> remember(key1: Any?, key2: Any?, calculate: () -> T): T =
    composer.remember(key1, key2, calculate)

/** Sentinel for the keyless overload -- never equal to anything a caller can pass. */
internal object Unkeyed

internal class RememberedSlot(var key: Any?, var key2: Any?, var value: Any?)

/** Drops slots this pass stopped declaring, the way [Applier.truncateFrom] drops children. */
internal fun RememberHolder.trimRememberedTo(count: Int) {
    while (rememberSlots.size > count) {
        rememberSlots.removeAt(rememberSlots.lastIndex)
    }
}
