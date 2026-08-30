/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.runtime

/**
 * Which of a node's two child lists a declaration belongs to.
 *
 * [Layers] exists from the start rather than being added later: a popup or dialog is laid out
 * against the viewport, not against its parent's constraints, so it cannot sit in the list the
 * parent's measure policy iterates. `docs/reference/compose-engine/07-overlay-layering.md` is
 * explicit that retrofitting this reworks the tree.
 *
 * A layer still belongs to the node that declared it -- that is what an anchored popup anchors to --
 * so it is a slot on the parent, not a flat list on the root.
 */
enum class Slot { Children, Layers }

/**
 * How [Composer] manipulates whatever tree it is building.
 *
 * The reason `:awake:compose:runtime` is a peer of `:awake:ui` rather than nested inside it:
 * reconciliation is "match this pass's declarations to last pass's nodes", which has nothing to do
 * with layout. The one tree-specific part -- creating and reordering nodes -- lives behind this,
 * exactly as `androidx.compose.runtime`'s `Applier` does.
 *
 * Nodes are `Any` rather than a type parameter so [Composer] stays non-generic and a composable's
 * signature is `context(_: Composer)`, not `context(_: Composer<LayoutNode>)`. The single cast that
 * buys lives in the one implementation.
 *
 * Every method addresses the node most recently entered by [down], or the root before any.
 */
interface Applier {
    fun childCount(slot: Slot): Int

    /** What the child declared itself as, used to decide reuse. */
    fun typeAt(slot: Slot, index: Int): Any?

    /** The explicit `key(...)` the child was created under, or null if it was positional. */
    fun keyAt(slot: Slot, index: Int): Any?

    fun nodeAt(slot: Slot, index: Int): Any

    fun createAt(slot: Slot, index: Int, type: Any, key: Any?): Any

    /** Moves an existing child into position -- how a keyed list survives reordering. */
    fun moveTo(slot: Slot, from: Int, to: Int)

    /** Drops every child from [index] onward: what this pass no longer declares. */
    fun truncateFrom(slot: Slot, index: Int)

    /** Descends into a child so subsequent calls address it. Paired with [up]. */
    fun down(slot: Slot, index: Int)

    fun up()
}
