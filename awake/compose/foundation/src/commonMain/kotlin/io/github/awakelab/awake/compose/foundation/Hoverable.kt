/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.interaction.Interaction
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEvent
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventPass
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventType
import io.github.awakelab.awake.compose.ui.node.PointerInputNode

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
 *
 * [hitMarginPx] matches [io.github.awakelab.awake.compose.foundation.gestures.draggable]'s:
 * a control that can be grabbed from beside itself has to light up from there too, or the pointer
 * sits in the grab margin looking at something that does not appear grabbable.
 */
fun Modifier.hoverable(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    hitMarginPx: Int = 0,
): Modifier = if (enabled) this then HoverableElement(interactionSource, hitMarginPx) else this

private class HoverableElement(
    private val source: InteractionSource,
    private val hitMarginPx: Int,
) : ModifierNodeElement<HoverableNode>() {
    override fun create(): HoverableNode = HoverableNode(hitMarginPx)

    override fun update(node: HoverableNode) {
        node.source = source
        node.hitMarginPx = hitMarginPx
    }
    override fun toString(): String = "hoverable(hitMargin=$hitMarginPx)"
}

private class HoverableNode(override var hitMarginPx: Int) : Modifier.Node(), PointerInputNode {
    lateinit var source: InteractionSource

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

    override fun toString(): String = "hoverable(hitMargin=$hitMarginPx)"
}
