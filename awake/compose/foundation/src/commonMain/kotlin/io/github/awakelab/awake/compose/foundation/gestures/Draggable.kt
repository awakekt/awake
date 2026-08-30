/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation.gestures

import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEvent
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventPass
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventType
import io.github.awakelab.awake.compose.ui.node.PointerInputNode

/**
 * Reports pointer movement while pressed.
 *
 * [onDrag] receives the delta since the last event, not an absolute position: a divider or slider
 * cares how far the pointer moved, and deltas are what survive the node itself being repositioned
 * mid-drag.
 *
 * [hitMarginPx] widens what counts as grabbing this, without widening the thing itself. A divider
 * drawn one pixel wide is a one-pixel target otherwise, and no amount of correct delta handling
 * makes that draggable.
 */
fun Modifier.draggable(
    hitMarginPx: Int = 0,
    onDrag: (dx: Int, dy: Int) -> Unit,
): Modifier = this then DraggableElement(hitMarginPx, onDrag)

private class DraggableElement(
    private val hitMarginPx: Int,
    private val onDrag: (Int, Int) -> Unit,
) : ModifierNodeElement<DraggableNode>() {
    override fun create(): DraggableNode = DraggableNode(hitMarginPx)

    override fun update(node: DraggableNode) {
        node.onDrag = onDrag
        node.hitMarginPx = hitMarginPx
    }
    override fun toString(): String = "draggable(hitMargin=$hitMarginPx)"
}

private class DraggableNode(override var hitMarginPx: Int) : Modifier.Node(), PointerInputNode {
    lateinit var onDrag: (Int, Int) -> Unit
    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main) return
        when (event.type) {
            // Consumed so the dispatcher makes this node the capture holder; the press itself is
            // not a drag.
            PointerEventType.Press -> event.consume()
            // `isCaptureHolder` rather than a `dragging` field. This instance did not exist when
            // the press landed -- the chain is rebuilt every pass -- so a remembered flag reads
            // false on the very next frame and the drag never starts. It did, and produced no drag
            // at all in a live frame loop while its test passed by never reconciling.
            //
            // Deliberately not gated on `isInBounds`: a slider must keep tracking a pointer dragged
            // past its own track, which is the whole reason capture exists.
            PointerEventType.Move -> if (event.isCaptureHolder) {
                onDrag(event.dx, event.dy)
                event.consume()
            }
            else -> Unit
        }
    }

    override fun toString(): String = "draggable(hitMargin=$hitMarginPx)"
}
