// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.foundation.interaction.Interaction
import io.github.ronjunevaldoz.awake.compose.foundation.interaction.InteractionSource
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.node.FocusTargetNode

/**
 * Puts this node in the focus ring, and records focus into [interactionSource].
 *
 * Compose's parameter order: `enabled` first, source second. Passing `enabled = false` still leaves
 * the modifier in the chain but out of the ring, so a disabled control does not shift the tab order
 * of everything after it as it enables and disables.
 */
fun Modifier.focusable(
    enabled: Boolean = true,
    interactionSource: InteractionSource? = null,
): Modifier = this then FocusableNode(enabled, interactionSource)

private class FocusableNode(
    override val canFocus: Boolean,
    private val source: InteractionSource?,
) : FocusTargetNode {

    // No per-instance guard: `FocusOwner` notifies only on a real change, and a guard on this
    // instance would not survive the next chain rebuild anyway.
    override fun onFocusChanged(focused: Boolean) {
        if (focused) {
            source?.tryEmit(Interaction.Focus.Focus)
        } else {
            source?.tryEmit(Interaction.Focus.Unfocus(Interaction.Focus.Focus))
        }
    }

    override fun toString(): String = "focusable(enabled=$canFocus)"
}
