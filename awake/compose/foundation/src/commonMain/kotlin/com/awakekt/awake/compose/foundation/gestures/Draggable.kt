/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.gestures

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.node.PointerInputNode
import kotlin.math.abs

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
): Modifier = draggable(hitMarginPx, onDrag, {})

/**
 * Reports the end of the captured gesture in addition to movement. The overload preserves the
 * original trailing-lambda API for existing callers while giving controls such as sliders an
 * explicit commit boundary.
 */
fun Modifier.draggable(
    hitMarginPx: Int = 0,
    onDrag: (dx: Int, dy: Int) -> Unit,
    onDragStopped: () -> Unit,
): Modifier = this then DraggableElement(hitMarginPx, onDrag, onDragStopped)

private class DraggableElement(
    private val hitMarginPx: Int,
    private val onDrag: (Int, Int) -> Unit,
    private val onDragStopped: () -> Unit,
) : ModifierNodeElement<DraggableNode>() {
    override fun create(): DraggableNode = DraggableNode(hitMarginPx, onDragStopped)

    override fun update(node: DraggableNode) {
        node.onDrag = onDrag
        node.onDragStopped = onDragStopped
        node.hitMarginPx = hitMarginPx
    }
    override fun toString(): String = "draggable(hitMargin=$hitMarginPx)"
}

private class DraggableNode(
    override var hitMarginPx: Int,
    var onDragStopped: () -> Unit,
) : Modifier.Node(),
    PointerInputNode {
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
            PointerEventType.Release -> if (event.isCaptureHolder) {
                // Release is delivered to the capture holder even when the pointer left the
                // node. This is the stable end-of-gesture seam for sliders and other controls
                // that preview continuously but commit once the drag is complete.
                onDragStopped()
                event.consume()
            }
            else -> Unit
        }
    }

    override fun toString(): String = "draggable(hitMargin=$hitMarginPx)"
}

/**
 * Reports a drag only once the pointer has moved [thresholdPx] from where it pressed, so a click
 * chained after this on the same node -- a draggable button's `clickable` -- still fires when the
 * pointer never moved that far.
 *
 * Claims the press at once regardless, the same unconditional [PointerEvent.consume] [draggable]
 * uses: that is what keeps this node capturing the pointer wherever it moves once a drag actually
 * starts. [onDrag] fires once the threshold is crossed and again on every move after, and
 * [onDragEnd] once on release, both with the pointer's position local to this node's own box --
 * the same coordinates a captured gesture keeps reporting after the pointer leaves it. Released
 * before the threshold, neither fires and whatever handled the press is free to act on the release.
 */
fun Modifier.dragAfterThreshold(
    thresholdPx: Int = DEFAULT_DRAG_THRESHOLD_PX,
    onDrag: (x: Int, y: Int) -> Unit,
    onDragEnd: (x: Int, y: Int) -> Unit,
): Modifier = this then DragAfterThresholdElement(thresholdPx, onDrag, onDragEnd)

/** About the same slop a touch screen gives a tap before it counts as a drag. */
private const val DEFAULT_DRAG_THRESHOLD_PX = 8

private class DragAfterThresholdElement(
    private val thresholdPx: Int,
    private val onDrag: (Int, Int) -> Unit,
    private val onDragEnd: (Int, Int) -> Unit,
) : ModifierNodeElement<DragAfterThresholdNode>() {
    override fun create(): DragAfterThresholdNode = DragAfterThresholdNode(thresholdPx)

    override fun update(node: DragAfterThresholdNode) {
        node.thresholdPx = thresholdPx
        node.onDrag = onDrag
        node.onDragEnd = onDragEnd
    }
    override fun toString(): String = "dragAfterThreshold(threshold=$thresholdPx)"
}

private class DragAfterThresholdNode(
    var thresholdPx: Int,
) : Modifier.Node(),
    PointerInputNode {
    lateinit var onDrag: (Int, Int) -> Unit
    lateinit var onDragEnd: (Int, Int) -> Unit
    private var originX = 0
    private var originY = 0
    private var dragging = false

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main) return
        when (event.type) {
            PointerEventType.Press -> {
                originX = event.x
                originY = event.y
                dragging = false
                event.consume()
            }
            PointerEventType.Move -> if (event.isCaptureHolder) {
                val crossed = abs(event.x - originX) >= thresholdPx || abs(event.y - originY) >= thresholdPx
                if (dragging || crossed) {
                    dragging = true
                    onDrag(event.x, event.y)
                }
            }
            PointerEventType.Release -> if (event.isCaptureHolder && dragging) {
                dragging = false
                onDragEnd(event.x, event.y)
                event.consume()
            }
            else -> Unit
        }
    }

    override fun toString(): String = "dragAfterThreshold(threshold=$thresholdPx)"
}
