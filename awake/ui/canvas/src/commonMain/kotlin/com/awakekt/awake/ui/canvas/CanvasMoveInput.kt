/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.node.PointerInputNode
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2

/** The canvas's width this pass, in canvas units: what a window is kept on. */
private val CanvasTree.canvasWidth: Float get() = box.width * box.hostDensity / canvasDensity

private val CanvasTree.canvasHeight: Float get() = box.height * box.hostDensity / canvasDensity

/** Canvas units per dp of [element]'s subtree this pass. */
private fun CanvasTree.ratioOf(element: CanvasElement): Float = densityOf(element) / canvasDensity

/**
 * How much of [window] stays on the canvas, in canvas units: [handle]'s [CanvasInput.moveStrip] in
 * the window's dp, else the window's top down to the handle's bottom as both were last placed, else
 * the handle's height.
 */
private fun CanvasTree.stripOf(window: CanvasElement, handle: CanvasElement): Float {
    if (handle.moveStrip > 0f) return handle.moveStrip * ratioOf(window)
    val handleBox = handle.placedBounds
    val windowBox = window.placedBounds
    val reach = if (handleBox != null && windowBox != null) handleBox.y + handleBox.height - windowBox.y else 0f
    return if (reach > 0f) reach else handle.height * ratioOf(handle)
}

/**
 * Where [window] lands this pass by its anchor and offsets alone, in canvas units: what the overlay
 * places it at before its parent box squeezes anything that sticks out, so a move and a clamp work
 * from the window's own place even while it is partly off the canvas.
 */
private fun CanvasTree.anchoredBox(window: CanvasElement): Rectangle {
    val ratio = ratioOf(window)
    val width = window.width * ratio
    val height = window.height * ratio
    val toUnits = box.hostDensity / canvasDensity
    val inset = if (window.safeArea) box.safeArea else CanvasInsets.None
    val left = inset.left * toUnits
    val top = inset.top * toUnits
    val x = left + along(window.anchor.column, window.offsetX * ratio, width, canvasWidth - left - inset.right * toUnits)
    val y = top + along(window.anchor.row, window.offsetY * ratio, height, canvasHeight - top - inset.bottom * toUnits)
    return Rectangle(x, y, width, height)
}

/** Where a box of [size] starts along one axis of a safe [space], from the space's start, for an anchor at [edge]. */
private fun along(edge: Int, offset: Float, size: Float, space: Float): Float = when (edge) {
    0 -> offset.coerceAtLeast(0f)
    2 -> space - offset.coerceAtLeast(0f) - size
    else -> (space - size) / 2f + offset
}

/**
 * Holds every shown window on the canvas, as a resize of the canvas, or a host restoring a position
 * saved on a larger screen, may have left it off. A window being moved is already held by its move.
 */
internal fun CanvasTree.keepWindowsOnCanvas() {
    val width = canvasWidth
    val height = canvasHeight
    // A box with no size yet, as under unbounded constraints, has nothing to keep a window on.
    if (width <= 0f || height <= 0f) return
    windows.forEachWindow { window, handle ->
        if (!window.moving && shows(window)) {
            val strip = stripOf(window, handle)
            val box = anchoredBox(window)
            // Left alone while it is on: a saved offset, a negative one held at its edge included, stays as written.
            val off = keepOnCanvasX(box.x, box.width, width, strip) != box.x || keepOnCanvasY(box.y, box.height, height, strip) != box.y
            if (off) placeWindow(window, window.offsetX, window.offsetY, box, 0f, 0f, width, height, strip, ratioOf(window))
        }
    }
}

/**
 * [modifier] with a [CanvasInput.moveHandle]'s move added when this element is one, and with a
 * window's bring-to-front press when it is one. Goes ahead of a Button's `clickable`, as a drag does.
 */
internal fun CanvasElement.movedModifier(modifier: Modifier, tree: CanvasTree, units: Float): Modifier {
    val raised = if (tree.windows.handleOf(this) != null) modifier then RaiseOnPressElement(this) else modifier
    return if (tree.windows.windowOf(this) != null) raised then MoveHandleElement(this, tree, units) else raised
}

private class MoveHandleElement(
    private val handle: CanvasElement,
    private val tree: CanvasTree,
    private val units: Float,
) : ModifierNodeElement<MoveHandleNode>() {
    override fun create(): MoveHandleNode = MoveHandleNode(handle, tree, units)

    override fun update(node: MoveHandleNode) {
        node.handle = handle
        node.tree = tree
        node.units = units
    }

    override fun toString(): String = "moveHandle()"
}

/**
 * Claims a press on the handle at once, as a draggable element's drag does, so it keeps the pointer
 * wherever it goes; a release before the threshold is left for a Button's `clickable` to click.
 * A press another element took first, a button on the bar, is left to it.
 */
private class MoveHandleNode(var handle: CanvasElement, var tree: CanvasTree, var units: Float) :
    Modifier.Node(),
    PointerInputNode {
    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main) return
        val move = handle.pointer.move
        when (event.type) {
            PointerEventType.Press -> if (!event.isConsumed && event.isInBounds && move.accepts(event.pointerId)) press(event, move)
            PointerEventType.Move -> if (move.holds(event.pointerId) && move.travel(event.dx, event.dy)) follow(event, move)
            PointerEventType.Release -> if (move.holds(event.pointerId)) {
                // A real move's release is not also a click.
                if (move.started) event.consume()
                handle.dropMove()
            }
            else -> Unit
        }
    }

    /** Starts holding the window, from where its anchor and offsets put it, when it is drawn. */
    private fun press(event: PointerEvent, move: CanvasMove) {
        val window = tree.windows.windowOf(handle)
        if (window?.screenBounds != null) {
            move.begin(event.pointerId, window, tree.anchoredBox(window))
            event.consume()
        }
    }

    /** Puts the window where the pointer's travel since the press takes it. */
    private fun follow(event: PointerEvent, move: CanvasMove) {
        val window = move.window ?: return
        window.pointer.moving = true
        placeWindow(
            window, move.offsetX, move.offsetY, move.box, move.travelX(units), move.travelY(units),
            tree.canvasWidth, tree.canvasHeight, tree.stripOf(window, handle), tree.ratioOf(window),
        )
        event.consume()
    }
}

/** Ends the move this handle holds, as its pointer lifts or it stops being drawn, reporting where its window was left. */
internal fun CanvasElement.dropMove() {
    val move = pointer.move
    if (!move.held) return
    val window = move.window
    if (move.started && window != null) {
        window.pointer.moving = false
        window.pointer.movedTo = Vec2(window.offsetX, window.offsetY)
    }
    move.end()
}

private class RaiseOnPressElement(private val window: CanvasElement) : ModifierNodeElement<RaiseOnPressNode>() {
    override fun create(): RaiseOnPressNode = RaiseOnPressNode(window)

    override fun update(node: RaiseOnPressNode) {
        node.window = window
    }

    override fun toString(): String = "raiseOnPress()"
}

/** Brings a window to the front on any press inside it, whatever takes the press; it only watches. */
private class RaiseOnPressNode(var window: CanvasElement) :
    Modifier.Node(),
    PointerInputNode {
    override val blocksHitTest: Boolean get() = false

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass == PointerEventPass.Initial && event.type == PointerEventType.Press && event.isInBounds) raiseCanvasElement(window)
    }
}
