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
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEvent
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventPass
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventType
import io.github.awakelab.awake.compose.ui.node.PointerInputNode

/**
 * Fires [onClick] when a press and release both land on this node.
 *
 * Consumes on `Main`, not `Initial`: an ancestor that wants the gesture -- a scroll container
 * taking over a drag -- gets first refusal, and a click that an ancestor stole never fires.
 *
 * Pass an [interactionSource] to have the press recorded for styling. Compose's parameter order.
 */
fun Modifier.clickable(
    interactionSource: InteractionSource? = null,
    onClick: () -> Unit,
): Modifier = this then ClickableElement(interactionSource, onClick)

/** A click target that invokes [onLongClick] once after a held press, otherwise [onClick]. */
fun Modifier.combinedClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = this then CombinedClickableElement(onClick, onLongClick)

private class CombinedClickableElement(
    private val onClick: () -> Unit,
    private val onLongClick: (() -> Unit)?,
) : ModifierNodeElement<CombinedClickableNode>() {
    override fun create(): CombinedClickableNode = CombinedClickableNode()

    override fun update(node: CombinedClickableNode) {
        node.onClick = onClick
        node.onLongClick = onLongClick
    }
}

private class CombinedClickableNode : Modifier.Node(), PointerInputNode {
    lateinit var onClick: () -> Unit
    var onLongClick: (() -> Unit)? = null
    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main || event.isConsumed) return
        when (event.type) {
            PointerEventType.Press -> event.consume()
            PointerEventType.LongPress -> if (event.isCaptureHolder && event.isInBounds && onLongClick != null) {
                onLongClick?.invoke()
                event.consume()
            }
            PointerEventType.Release -> if (
                event.isCaptureHolder && event.isInBounds &&
                    (!event.longPressTriggered || onLongClick == null)
            ) {
                onClick.invoke()
                event.consume()
            }
            else -> Unit
        }
    }
}

private class ClickableElement(
    private val source: InteractionSource?,
    private val onClick: () -> Unit,
) : ModifierNodeElement<ClickableNode>() {
    override fun create(): ClickableNode = ClickableNode()

    override fun update(node: ClickableNode) {
        node.source = source
        node.onClick = onClick
    }
    override fun toString(): String = "clickable()"
}

private class ClickableNode : Modifier.Node(), PointerInputNode {
    var source: InteractionSource? = null
    lateinit var onClick: () -> Unit

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main || event.isConsumed) return
        when (event.type) {
            PointerEventType.Press -> {
                source?.tryEmit(Interaction.Press.Press)
                event.consume()
            }
            // `isCaptureHolder` rather than a field: this instance did not exist when the press
            // landed. The chain is rebuilt every pass, so a link that remembered its own press
            // would forget it one frame later and the click would never fire.
            //
            // `isInBounds` is the other half: the capture keeps delivering after the pointer leaves,
            // and releasing out there must not count as a click.
            PointerEventType.Release -> if (event.isCaptureHolder && event.isInBounds) {
                source?.tryEmit(Interaction.Press.Release(Interaction.Press.Press))
                onClick.invoke()
                event.consume()
            }
            // Dragging off un-presses and dragging back re-arms, which is how every button on every
            // platform behaves -- the press is not lost, only suspended.
            PointerEventType.Exit -> if (event.isCaptureHolder) {
                source?.tryEmit(Interaction.Press.Cancel(Interaction.Press.Press))
            }
            PointerEventType.Enter -> if (event.isCaptureHolder) {
                source?.tryEmit(Interaction.Press.Press)
            }
            else -> Unit
        }
    }

    override fun toString(): String = "clickable()"
}
