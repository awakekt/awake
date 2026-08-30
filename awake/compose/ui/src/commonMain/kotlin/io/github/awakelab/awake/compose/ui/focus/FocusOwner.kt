/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.focus

import io.github.awakelab.awake.compose.ui.node.FocusTargetNode
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.node.activeModalLayer
import kotlin.math.abs

/**
 * Which way [FocusOwner.moveFocus] navigates through focusable items.
 *
 * Sequential navigation uses [Next] and [Previous] (Tab / Shift+Tab).
 * Spatial navigation uses [Left], [Right], [Up], and [Down] (gamepad D-pad / arrow keys).
 */
enum class FocusDirection {
    Next,
    Previous,
    Left,
    Right,
    Up,
    Down,
}

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
        val canTake = node.canTakeFocus() && node.isInTree(scope)
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
     * Moves focus in the given [direction].
     *
     * [FocusDirection.Next] and [FocusDirection.Previous] step sequentially through the ring,
     * wrapping at the ends.
     *
     * [FocusDirection.Left], [FocusDirection.Right], [FocusDirection.Up], and [FocusDirection.Down]
     * perform a 2D geometric beam search from the currently focused node to the nearest focusable
     * target in that direction.
     */
    fun moveFocus(root: LayoutNode, direction: FocusDirection): Boolean {
        focusedNode?.explicitTarget(direction)?.let { target ->
            if (requestFocus(root, target)) return true
        }
        ring.clear()
        // Bounded by an open modal: focus cycles/navigates inside a dialog instead of walking out
        // into the page behind it, which is the whole point of the thing being modal.
        collectFocusable(activeModal(root) ?: root, ring)
        if (ring.isEmpty()) {
            clearFocus()
            return false
        }
        return when (direction) {
            FocusDirection.Next, FocusDirection.Previous -> moveSequential(root, direction)
            FocusDirection.Left, FocusDirection.Right, FocusDirection.Up, FocusDirection.Down ->
                moveSpatial(root, direction)
        }
    }

    private fun moveSequential(root: LayoutNode, direction: FocusDirection): Boolean {
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

    private fun moveSpatial(root: LayoutNode, direction: FocusDirection): Boolean {
        val current = focusedNode
        if (current == null) {
            val fallback = when (direction) {
                FocusDirection.Down, FocusDirection.Right -> ring.first()
                FocusDirection.Up, FocusDirection.Left -> ring.last()
                else -> ring.first()
            }
            return requestFocus(root, fallback)
        }

        val curX = current.absoluteX
        val curY = current.absoluteY
        val curW = current.width
        val curH = current.height
        val curRight = curX + curW
        val curBottom = curY + curH
        val curCenterX = curX + curW / 2
        val curCenterY = curY + curH / 2

        var bestNode: LayoutNode? = null
        var minCost = Int.MAX_VALUE

        for (i in ring.indices) {
            val cand = ring[i]
            if (cand === current) continue

            val candX = cand.absoluteX
            val candY = cand.absoluteY
            val candW = cand.width
            val candH = cand.height
            val candRight = candX + candW
            val candBottom = candY + candH
            val candCenterX = candX + candW / 2
            val candCenterY = candY + candH / 2

            val inDirection = when (direction) {
                FocusDirection.Up -> candCenterY < curCenterY
                FocusDirection.Down -> candCenterY > curCenterY
                FocusDirection.Left -> candCenterX < curCenterX
                FocusDirection.Right -> candCenterX > curCenterX
                else -> false
            }
            if (!inDirection) continue

            val primaryDist = when (direction) {
                FocusDirection.Up -> curCenterY - candCenterY
                FocusDirection.Down -> candCenterY - curCenterY
                FocusDirection.Left -> curCenterX - candCenterX
                FocusDirection.Right -> candCenterX - curCenterX
                else -> 0
            }

            val orthoDist = when (direction) {
                FocusDirection.Up, FocusDirection.Down -> abs(candCenterX - curCenterX)
                FocusDirection.Left, FocusDirection.Right -> abs(candCenterY - curCenterY)
                else -> 0
            }

            val overlap = when (direction) {
                FocusDirection.Up, FocusDirection.Down -> candX < curRight && candRight > curX
                FocusDirection.Left, FocusDirection.Right -> candY < curBottom && candBottom > curY
                else -> false
            }

            val cost = if (overlap) {
                primaryDist * 2 + orthoDist
            } else {
                primaryDist * 2 + orthoDist * 4 + 1000
            }

            if (cost < minCost) {
                minCost = cost
                bestNode = cand
            }
        }

        return bestNode?.let { requestFocus(root, it) } ?: false
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
        if (node.canTakeFocus()) into.add(node)
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

private fun LayoutNode.canTakeFocus(): Boolean {
    val enabled = focusTargets.any { it.canFocus }
    val override = focusProperties.firstNotNullOfOrNull { it.canFocusOverride }
    return override ?: enabled
}

private fun LayoutNode.explicitTarget(direction: FocusDirection): LayoutNode? {
    val requester = focusProperties.firstNotNullOfOrNull {
        when (direction) {
            FocusDirection.Next -> it.nextRequester
            FocusDirection.Previous -> it.previousRequester
            FocusDirection.Up -> it.upRequester
            FocusDirection.Down -> it.downRequester
            FocusDirection.Left -> it.leftRequester
            FocusDirection.Right -> it.rightRequester
        }
    } as? FocusRequester
    return requester?.node
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
