// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.foundation.interaction.Interaction
import io.github.ronjunevaldoz.awake.compose.foundation.interaction.InteractionSource
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEvent
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventPass
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventType
import io.github.ronjunevaldoz.awake.compose.ui.node.PointerInputNode

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
): Modifier = this then ClickableNode(interactionSource, onClick)

private class ClickableNode(
    private val source: InteractionSource?,
    private val onClick: () -> Unit,
) : PointerInputNode {

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
                onClick()
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
