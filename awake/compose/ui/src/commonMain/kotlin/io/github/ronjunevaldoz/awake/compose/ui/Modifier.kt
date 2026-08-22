// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui

/**
 * An ordered chain of decorations applied to a layout node.
 *
 * A chain, not `ui-core`'s flat data class of nullable fields: each concern is a node that wraps
 * measurement, so ordering follows the chain instead of a hand-written sequence inside one
 * function, and a container never inspects a modifier to choose a strategy.
 *
 * Order is meaningful and nothing warns -- inherent to the model. See
 * `docs/reference/compose-engine/02-modifier.md` for the diagnostics that catch the known-wrong
 * orderings.
 */
interface Modifier {

    /** Walks the chain outermost-first. */
    fun <R> foldIn(initial: R, operation: (R, Element) -> R): R

    infix fun then(other: Modifier): Modifier =
        if (other === Modifier) this else CombinedModifier(this, other)

    /** A single link. */
    interface Element : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R = operation(initial, this)
    }

    /** The empty chain, and the receiver every `Modifier.foo()` builder extends. */
    companion object : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R = initial
        override infix fun then(other: Modifier): Modifier = other
        override fun toString(): String = "Modifier"
    }
}

private class CombinedModifier(
    private val outer: Modifier,
    private val inner: Modifier,
) : Modifier {
    override fun <R> foldIn(initial: R, operation: (R, Modifier.Element) -> R): R =
        inner.foldIn(outer.foldIn(initial, operation), operation)

    override fun toString(): String =
        "[" + foldIn("") { acc, element -> if (acc.isEmpty()) "$element" else "$acc, $element" } + "]"
}
