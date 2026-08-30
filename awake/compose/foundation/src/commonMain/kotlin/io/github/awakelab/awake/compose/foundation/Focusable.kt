/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.interaction.Interaction
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.node.FocusTargetNode

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
): Modifier = this then FocusableElement(enabled, interactionSource)

private class FocusableElement(
    private val enabled: Boolean,
    private val source: InteractionSource?,
) : ModifierNodeElement<FocusableNode>() {
    override fun create(): FocusableNode = FocusableNode()

    override fun update(node: FocusableNode) {
        node.enabled = enabled
        node.source = source
    }

    override fun toString(): String = "focusable(enabled=$enabled)"
}

private class FocusableNode : Modifier.Node(), FocusTargetNode {
    var enabled: Boolean = true
    var source: InteractionSource? = null

    override val canFocus: Boolean get() = enabled

    override fun onFocusChanged(focused: Boolean) {
        if (focused) {
            source?.tryEmit(Interaction.Focus.Focus)
        } else {
            source?.tryEmit(Interaction.Focus.Unfocus(Interaction.Focus.Focus))
        }
    }

    override fun toString(): String = "focusable(enabled=$enabled)"
}
