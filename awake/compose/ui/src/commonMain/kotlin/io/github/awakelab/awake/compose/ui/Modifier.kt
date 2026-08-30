/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui

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

    /** Walks the chain innermost-first. */
    fun <R> foldOut(initial: R, operation: (Element, R) -> R): R

    /** Whether any link in this chain satisfies [predicate]. */
    fun any(predicate: (Element) -> Boolean): Boolean

    /** Whether every link in this chain satisfies [predicate]. */
    fun all(predicate: (Element) -> Boolean): Boolean

    infix fun then(other: Modifier): Modifier =
        if (other === Modifier) this else CombinedModifier(this, other)

    /** A single link. */
    interface Element : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R = operation(initial, this)
        override fun <R> foldOut(initial: R, operation: (Element, R) -> R): R = operation(this, initial)
        override fun any(predicate: (Element) -> Boolean): Boolean = predicate(this)
        override fun all(predicate: (Element) -> Boolean): Boolean = predicate(this)
    }

    /**
     * Retained implementation state owned by a [ModifierNodeElement].
     *
     * Do not add a node to a chain directly. A new element is produced on each reconciliation
     * pass, while its matching node survives until the element is removed or replaced at that
     * chain position.
     */
    abstract class Node {
        internal var layoutNode: io.github.awakelab.awake.compose.ui.node.LayoutNode? = null
            private set

        /** Called once after this node becomes part of a [layoutNode]. */
        open fun onAttach() = Unit

        /** Called once before this node stops belonging to its [layoutNode]. */
        open fun onDetach() = Unit

        internal fun attachTo(owner: io.github.awakelab.awake.compose.ui.node.LayoutNode) {
            check(layoutNode == null) { "Modifier.Node is already attached" }
            layoutNode = owner
            onAttach()
        }

        internal fun detach() {
            if (layoutNode == null) return
            onDetach()
            layoutNode = null
        }
    }

    /** The empty chain, and the receiver every `Modifier.foo()` builder extends. */
    companion object : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R = initial
        override fun <R> foldOut(initial: R, operation: (Element, R) -> R): R = initial
        override fun any(predicate: (Element) -> Boolean): Boolean = false
        override fun all(predicate: (Element) -> Boolean): Boolean = true
        override infix fun then(other: Modifier): Modifier = other
        override fun toString(): String = "Modifier"
    }
}

/**
 * Per-pass description of a retained [Modifier.Node].
 *
 * The reconciler retains a node when the element class at the same chain position is unchanged,
 * calls [update] with the new parameters, and otherwise detaches the old node before attaching a
 * freshly [create]d replacement.
 */
abstract class ModifierNodeElement<N : Modifier.Node> : Modifier.Element {
    abstract fun create(): N

    abstract fun update(node: N)

    @Suppress("UNCHECKED_CAST")
    internal fun updateUnchecked(node: Modifier.Node) = update(node as N)
}

private class CombinedModifier(
    private val outer: Modifier,
    private val inner: Modifier,
) : Modifier {
    override fun <R> foldIn(initial: R, operation: (R, Modifier.Element) -> R): R =
        inner.foldIn(outer.foldIn(initial, operation), operation)

    override fun <R> foldOut(initial: R, operation: (Modifier.Element, R) -> R): R =
        outer.foldOut(inner.foldOut(initial, operation), operation)

    override fun any(predicate: (Modifier.Element) -> Boolean): Boolean =
        outer.any(predicate) || inner.any(predicate)

    override fun all(predicate: (Modifier.Element) -> Boolean): Boolean =
        outer.all(predicate) && inner.all(predicate)

    override fun toString(): String =
        "[" + foldIn("") { acc, element -> if (acc.isEmpty()) "$element" else "$acc, $element" } + "]"
}
