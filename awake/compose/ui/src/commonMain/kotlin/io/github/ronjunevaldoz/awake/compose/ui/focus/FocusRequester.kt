// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.focus

import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.node.FocusTargetNode
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.node.NodeAttachedModifierNode

/**
 * A handle a caller keeps so it can move focus to a node it does not otherwise hold.
 *
 * The dialog case: opening one should focus its first field, and the code doing the opening has no
 * reference to that field's node. `ui-core` solved this with `requestFocus(id)` on a string, which
 * two widgets could collide on; here the requester is an object, so a collision is impossible.
 *
 * Hold it across passes with `remember`, or the node it points at is forgotten every frame.
 */
class FocusRequester {
    internal var node: LayoutNode? = null

    /**
     * True if a focusable node is currently attached and took focus.
     *
     * [root] is threaded through so an open modal can refuse the request -- a requester held by code
     * behind a dialog must not be able to steal focus out of it.
     */
    fun requestFocus(owner: FocusOwner, root: LayoutNode): Boolean {
        val target = node
        return target != null && owner.requestFocus(root, target)
    }
}

/** Points [focusRequester] at this node, so a caller elsewhere can focus it. */
fun Modifier.focusRequester(focusRequester: FocusRequester): Modifier =
    this then FocusRequesterNode(focusRequester)

private class FocusRequesterNode(
    private val requester: FocusRequester,
) : NodeAttachedModifierNode {
    override fun onAttachedTo(node: LayoutNode) {
        requester.node = node
    }

    override fun toString(): String = "focusRequester()"
}

/**
 * Reports focus arriving or leaving this node.
 *
 * Fires only on change, unlike [OnPlacedModifierNode], because focus is edge-triggered by nature --
 * a caller wants to scroll a field into view when it gains focus, not on every pass while it holds
 * it.
 */
fun Modifier.onFocusChanged(onFocusChanged: (Boolean) -> Unit): Modifier =
    this then OnFocusChangedNode(onFocusChanged)

private class OnFocusChangedNode(
    private val callback: (Boolean) -> Unit,
) : FocusTargetNode {
    // False: observing focus is not the same as accepting it. A node that reported focus changes
    // while silently joining the tab ring would put unfocusable containers in the Tab order.
    override val canFocus: Boolean get() = false

    override fun onFocusChanged(focused: Boolean) = callback(focused)

    override fun toString(): String = "onFocusChanged()"
}
