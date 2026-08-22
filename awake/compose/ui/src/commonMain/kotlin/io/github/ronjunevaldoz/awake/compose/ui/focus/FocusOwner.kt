// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.focus

import io.github.ronjunevaldoz.awake.compose.ui.node.FocusTargetNode
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.node.activeModalLayer

/**
 * Which way [FocusOwner.moveFocus] steps through the ring.
 *
 * Only the two Compose calls `Tab` and `Shift+Tab` map to. Compose also has `Left`/`Right`/`Up`/
 * `Down`/`Enter`/`Exit` for spatial navigation; those are deliberately absent rather than present
 * and doing nothing, which is the failure this repo keeps hitting -- see `11-refinements.md`.
 */
enum class FocusDirection { Next, Previous }

/**
 * Holds which node has focus, and moves it.
 *
 * Focus is a node reference, not a string id. `UiRuntimeCoordinator` keyed it by `focusedId: String?`
 * with `requestFocus(id)`/`isFocused(id)`, which two widgets could collide on and which offered no
 * traversal order because there was no tree to derive one from.
 *
 * The ring is the placed order of focusable nodes -- depth-first, children in declaration order.
 * That is the reading order of the layout, which is what a Tab key is expected to follow.
 */
class FocusOwner {

    private var focusedNode: LayoutNode? = null

    // Refilled per traversal rather than reallocated: moveFocus runs on a keystroke, but the same
    // per-frame allocation rule applies to anything on the input path.
    private val ring = mutableListOf<LayoutNode>()

    /** The node holding focus, or null. */
    val focused: LayoutNode? get() = focusedNode

    fun isFocused(node: LayoutNode): Boolean = focusedNode === node

    /**
     * Gives [node] focus, taking it from whoever held it.
     *
     * Returns false if the node has no focusable link, or if a modal layer is open and the node is
     * outside it, so a caller can fall through to an ancestor rather than assume it worked.
     *
     * [root] is needed to find the open modal. Without it a click could focus a field behind a
     * dialog, which is the same hole as Tab walking out of one.
     */
    fun requestFocus(root: LayoutNode, node: LayoutNode): Boolean {
        val scope = activeModal(root) ?: root
        val canTake = node.focusTargets.any { it.canFocus } && node.isInTree(scope)
        if (canTake && focusedNode !== node) {
            clearFocus()
            focusedNode = node
            notify(node, focused = true)
        }
        return canTake
    }

    // Shared with PointerInputDispatcher: focus and pointer must agree on which modal is open, or a
    // dialog traps Tab while letting a click through to the page behind it.
    private fun activeModal(node: LayoutNode): LayoutNode? = node.activeModalLayer()

    fun clearFocus() {
        val previous = focusedNode ?: return
        focusedNode = null
        notify(previous, focused = false)
    }

    /**
     * Tells every focus link on the node, not only the one that accepted focus.
     *
     * `onFocusChanged` is a link that observes without accepting -- `canFocus` is false so it stays
     * out of the tab ring -- and notifying only the accepting link would leave it silent.
     */
    private fun notify(node: LayoutNode, focused: Boolean) {
        val targets = node.focusTargets
        for (i in targets.indices) {
            targets[i].onFocusChanged(focused)
        }
    }

    /**
     * Moves focus one step around the ring, wrapping at the ends.
     *
     * Wraps rather than stopping: a Tab that silently does nothing at the last field reads as a
     * broken keyboard, and every desktop toolkit wraps.
     */
    fun moveFocus(root: LayoutNode, direction: FocusDirection): Boolean {
        ring.clear()
        // Bounded by an open modal: Tab cycles inside a dialog instead of walking out into the page
        // behind it, which is the whole point of the thing being modal.
        collectFocusable(activeModal(root) ?: root, ring)
        if (ring.isEmpty()) {
            clearFocus()
            return false
        }
        val current = ring.indexOf(focusedNode)
        val step = if (direction == FocusDirection.Next) 1 else -1
        // An unfocused start enters at the first target going forward, the last going back.
        val next = if (current < 0) {
            if (direction == FocusDirection.Next) 0 else ring.lastIndex
        } else {
            (current + step + ring.size) % ring.size
        }
        return requestFocus(root, ring[next])
    }

    /**
     * Called when the tree changes, so focus cannot survive on a node that is gone.
     *
     * Without it a removed text field keeps receiving keys and `isFocused` answers for something no
     * longer laid out -- the same class as a stranded pointer capture.
     */
    fun revalidate(root: LayoutNode) {
        val node = focusedNode ?: return
        val scope = activeModal(root) ?: root
        // Also drops focus that a newly opened modal has just shut out, so the field behind a dialog
        // does not keep receiving keys.
        if (!node.isInTree(scope) || node.focusTargets.none { it.canFocus }) clearFocus()
    }

    private fun collectFocusable(node: LayoutNode, into: MutableList<LayoutNode>) {
        if (node.focusTargets.any { it.canFocus }) into.add(node)
        for (i in node.children.indices) {
            collectFocusable(node.children[i], into)
        }
        // Layers last: a popup's contents come after the anchor that opened them, which is the
        // order a reader sees them in. Modal trapping -- a dialog bounding the ring rather than
        // extending it -- lands with `07-overlay-layering.md`'s modal work.
        for (i in node.layers.indices) {
            collectFocusable(node.layers[i], into)
        }
    }
}

internal fun LayoutNode.isInTree(root: LayoutNode): Boolean {
    var found = this === root
    var i = 0
    while (!found && i < root.children.size) {
        found = isInTree(root.children[i])
        i++
    }
    i = 0
    while (!found && i < root.layers.size) {
        found = isInTree(root.layers[i])
        i++
    }
    return found
}
