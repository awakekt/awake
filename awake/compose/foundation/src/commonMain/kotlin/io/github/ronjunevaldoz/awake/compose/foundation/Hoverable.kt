// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.foundation.interaction.Interaction
import io.github.ronjunevaldoz.awake.compose.foundation.interaction.InteractionSource
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.runtime.remember
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEvent
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventPass
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventType
import io.github.ronjunevaldoz.awake.compose.ui.node.PointerInputNode

/**
 * An [InteractionSource] that survives the next pass, for a component to hand to its own modifiers.
 *
 * The reason interaction state is caller-owned rather than read off the node: a modifier is built
 * *before* the node it decorates exists, so `Modifier.background(if (hovered) a else b)` needs
 * somewhere to read from that outlives the frame. Compose solves it the same way.
 */
context(_: Composer)
fun rememberInteractionSource(): InteractionSource = remember { InteractionSource() }

/**
 * Records whether the pointer is over this node into [interactionSource].
 *
 * Ancestors count as hovered, like CSS `:hover` -- a card containing a hovered button is itself
 * hovered, which is what a styling layer expects.
 *
 * Never consumes: hovering is an observation, and swallowing the event would stop a sibling
 * modifier or an ancestor from seeing the same pointer.
 */
fun Modifier.hoverable(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
): Modifier = if (enabled) this then HoverableNode(interactionSource) else this

private class HoverableNode(private val source: InteractionSource) : PointerInputNode {

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main) return
        // No "already entered" guard: the dispatcher sends exactly one Enter per transition, and a
        // guard held on this instance would be lost the next time the chain is rebuilt.
        when (event.type) {
            PointerEventType.Enter -> source.tryEmit(Interaction.Hover.Enter)
            PointerEventType.Exit -> source.tryEmit(Interaction.Hover.Exit(Interaction.Hover.Enter))
            else -> Unit
        }
    }

    override fun toString(): String = "hoverable()"
}
