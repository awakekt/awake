/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.runtime

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
 * Slots are positional within the enclosing node, exactly as node identity is positional within its
 * parent -- so the same caveat applies, and `key(...)` is the same escape hatch. Declaring a
 * remember inside a branch that can swap will hand the other branch the first one's value.
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
