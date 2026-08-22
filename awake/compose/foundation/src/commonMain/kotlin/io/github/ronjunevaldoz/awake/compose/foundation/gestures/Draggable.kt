// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.gestures

import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEvent
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventPass
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventType
import io.github.ronjunevaldoz.awake.compose.ui.node.PointerInputNode

/**
 * Reports pointer movement while pressed.
 *
 * [onDrag] receives the delta since the last event, not an absolute position: a divider or slider
 * cares how far the pointer moved, and deltas are what survive the node itself being repositioned
 * mid-drag.
 */
fun Modifier.draggable(onDrag: (dx: Int, dy: Int) -> Unit): Modifier = this then DraggableNode(onDrag)

private class DraggableNode(private val onDrag: (Int, Int) -> Unit) : PointerInputNode {
    private var dragging = false
    private var lastX = 0
    private var lastY = 0

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main) return
        when (event.type) {
            PointerEventType.Press -> {
                dragging = true
                lastX = event.x
                lastY = event.y
                event.consume()
            }
            PointerEventType.Move -> {
                if (!dragging) return
                onDrag(event.x - lastX, event.y - lastY)
                lastX = event.x
                lastY = event.y
                event.consume()
            }
            PointerEventType.Release -> dragging = false
            else -> Unit
        }
    }

    override fun toString(): String = "draggable()"
}
