/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.input.pointer

import com.awakekt.awake.compose.ui.focus.FocusOwner
import com.awakekt.awake.compose.ui.layout.LayerKind
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.node.activeDismissableLayer
import com.awakekt.awake.compose.ui.node.activeModalLayer
import com.awakekt.awake.core.input.PointerCursor

/**
 * Routes pointer events through the placed tree.
 *
 * Hit-tested against **placed** geometry, so this runs against the previous frame's tree and never
 * during layout. Input is dispatched before the next reconcile, which is what removes the
 * one-frame lag immediate mode has -- it hit-tests against bounds it is still computing.
 */
class PointerInputDispatcher(
    /** Where a press sends focus. Shared with the host so a Tab key and a click agree. */
    val focusOwner: FocusOwner = FocusOwner(),
) {

    // Capture is per pointer. A finger must not cancel a simultaneous mouse drag (or another
    // finger's slider), and node identity avoids the string-id collisions ui-core had.
    private val captures = mutableMapOf<Long, LayoutNode>()
    private val heldSeconds = mutableMapOf<Long, Float>()
    private val longPressTriggered = mutableSetOf<Long>()

    /**
     * True while a node holds the pointer, which is what "the UI owns this input" means.
     *
     * Distinct from an event merely being consumed: a hover handler consumes nothing and a click
     * consumes one event, but neither means gameplay should stop reading the mouse.
     */
    val hasCapture: Boolean get() = captures.isNotEmpty()

    /**
     * True when something under the pointer could still take a scroll.
     *
     * Read from the hover path rather than the dispatch path, so it answers about where the pointer
     * *is*. False at a scroller's end, so gameplay gets the wheel back at the boundary instead of
     * the UI silently swallowing it forever.
     */
    val isOverScrollable: Boolean
        get() {
            for (i in hovered.indices) {
                val scrollables = hovered[i].scrollables
                for (j in scrollables.indices) {
                    if (scrollables[j].canScroll) return true
                }
            }
            return false
        }

    private val path = mutableListOf<LayoutNode>()

    // What is under the pointer right now, and what was last time. Hover is the one piece of pointer
    // state a node cannot derive from the event it is handed -- "the pointer is no longer over you"
    // is the absence of an event.
    //
    // Tracked separately from `path` because during a capture `path` is the held node's ancestry,
    // not what the pointer is over. Sharing them would leave a button pressed-looking after the
    // pointer walked off it, which is the bug `clickable` cancels on.
    private val hoverPath = mutableListOf<LayoutNode>()
    private val hovered = mutableListOf<LayoutNode>()

    // Reused rather than allocated per move: a synthesised event per node per frame is exactly the
    // per-frame allocation this engine exists to remove.
    private val enterEvent = PointerEvent(PointerEventType.Enter)
    private val exitEvent = PointerEvent(PointerEventType.Exit)

    // Hoisted for the same reason as Painter.paintOrder -- `entries` allocates on every read.
    // Reversed once: hit order is the reverse of paint order.
    private val hitOrder = LayerKind.entries.reversed().toTypedArray()

    // Reused: drawBoundsAt writes into it once per pointer link per delivered node.
    private val linkBounds = IntArray(4)

    // Who consumed the event currently being dispatched, cleared per dispatch.
    //
    // Capture follows consumption, not depth. `path.last()` is the innermost node the pointer is
    // over, which is only the same node when nothing fills its parent: give a Row's child
    // `weight(1f)` and the child covers the Row exactly, so the child takes a capture it has no
    // handler for and the Row's own release arrives with `isCaptureHolder = false` -- a button that
    // highlights on press and never fires.
    private var consumer: LayoutNode? = null

    /**
     * True while a modal layer is open, whether or not the pointer is over it.
     *
     * Reported to gameplay separately from [hasCapture], because clicking a dialog's backdrop
     * consumes nothing -- no node is under the pointer to consume it -- and a game that gated on
     * capture alone would fire a world action through an open dialog.
     */
    var isModalOpen: Boolean = false
        private set

    /** True if any node consumed the event. */
    fun dispatch(root: LayoutNode, event: PointerEvent, x: Int, y: Int, dx: Int = 0, dy: Int = 0): Boolean {
        event.reset()
        event.longPressTriggered = event.pointerId in longPressTriggered
        event.dx = dx
        event.dy = dy
        path.clear()
        consumer = null

        // A modal owns the frame: nothing outside it is hit, so a click on the backdrop reaches
        // nothing rather than falling through to the page behind. Derived from the tree, not
        // registered -- `ui-core`'s registerOverlayOcclusion was correct only if a widget remembered
        // to call it, and 07-overlay-layering.md names that as the structural problem.
        val modal = root.activeModalLayer()
        isModalOpen = modal != null
        val dismissable = root.activeDismissableLayer()
        if (event.type == PointerEventType.Press && dismissable != null && !dismissable.contains(x, y)) {
            dismissable.onDismissRequest?.invoke()
            event.consume()
            updateHover(modal ?: root, x, y)
            return true
        }
        val hitRoot = modal ?: root

        val holder = captures[event.pointerId]
        if (holder != null && holder.isAttachedTo(root)) {
            // A drag that leaves its node keeps arriving: that is what capture is for.
            buildPathTo(root, holder)
        } else {
            captures.remove(event.pointerId)
            heldSeconds.remove(event.pointerId)
            longPressTriggered.remove(event.pointerId)
            hitTest(path, hitRoot, x, y)
        }
        // Before the empty-path return: a pointer leaving everything must still un-hover what it left.
        updateHover(hitRoot, x, y)
        if (path.isEmpty()) return false

        deliver(event, x, y, PointerEventPass.Initial, rootFirst = true)
        deliver(event, x, y, PointerEventPass.Main, rootFirst = false)
        deliver(event, x, y, PointerEventPass.Final, rootFirst = true)

        updateCapture(event)
        updateFocus(root, event)
        return event.isConsumed
    }

    /** Advances a captured press and dispatches one long-press event after [LONG_PRESS_SECONDS]. */
    fun advanceTime(root: LayoutNode, x: Int, y: Int, deltaSeconds: Float, pointerId: Long = 0L) {
        if (pointerId !in captures || pointerId in longPressTriggered) return
        val held = (heldSeconds[pointerId] ?: 0f) + deltaSeconds.coerceAtLeast(0f)
        heldSeconds[pointerId] = held
        if (held >= LONG_PRESS_SECONDS) {
            longPressTriggered += pointerId
            dispatch(root, PointerEvent(PointerEventType.LongPress, pointerId = pointerId), x, y)
        }
    }

    /**
     * A press moves focus to the innermost focusable under it, or clears focus if there is none.
     *
     * Clearing matters as much as setting: clicking away from a text field is how a caret is
     * dismissed, and a focus that only ever moved forward would leave the field editable from the
     * keyboard after the user visibly left it.
     */
    private fun updateFocus(root: LayoutNode, event: PointerEvent) {
        if (event.type != PointerEventType.Press) return
        for (i in path.indices.reversed()) {
            if (focusOwner.requestFocus(root, path[i])) return
        }
        focusOwner.clearFocus()
    }

    /**
     * Synthesises [PointerEventType.Enter] and [PointerEventType.Exit] from what is under the
     * pointer now versus last dispatch.
     *
     * Ancestors count as hovered, like CSS `:hover` -- a card containing a hovered button is itself
     * hovered, which is what a styling layer expects.
     *
     * Hit-tested on its own rather than reusing the dispatch path, because a capture pins that path
     * to the held node. A press that drags off its button must still un-hover it, which is how
     * `clickable` knows to cancel instead of firing.
     */

    /**
     * The cursor the innermost hovered node asks for, or [PointerCursor.Default].
     *
     * Innermost rather than outermost, matching CSS: a resize handle inside a panel shows the
     * resize arrow, not the panel's. `hitTest` appends as it descends, so the last entry that
     * declares one is the deepest.
     */
    val hoveredCursor: PointerCursor
        get() {
            for (i in hovered.indices.reversed()) {
                hovered[i].pointerCursor?.let { return it }
            }
            return PointerCursor.Default
        }

    private fun updateHover(root: LayoutNode, x: Int, y: Int) {
        hoverPath.clear()
        hitTest(hoverPath, root, x, y)
        for (i in hovered.indices) {
            val node = hovered[i]
            if (!hoverPath.contains(node)) deliverTo(node, exitEvent, x, y)
        }
        for (i in hoverPath.indices) {
            val node = hoverPath[i]
            if (!hovered.contains(node)) deliverTo(node, enterEvent, x, y)
        }
        hovered.clear()
        hovered.addAll(hoverPath)
    }

    private fun deliverTo(node: LayoutNode, event: PointerEvent, x: Int, y: Int) {
        if (node.pointerInputs.isEmpty()) return
        event.reset()
        deliverToLinks(node, event, x, y, PointerEventPass.Main)
    }

    /**
     * Offers the event to each pointer link whose own box contains the point.
     *
     * A link inset by a `padding` outside it has a smaller hit target than its node, so the box is
     * per link rather than per node. Coordinates handed to the link are relative to that box, the
     * same rule `DrawScope` follows -- a handler that had to subtract its own inset would break the
     * moment the padding changed.
     */
    private fun deliverToLinks(
        node: LayoutNode,
        event: PointerEvent,
        x: Int,
        y: Int,
        pass: PointerEventPass,
    ) {
        val handlers = node.pointerInputs
        // Two events are outside the box by definition and must not be filtered by it: an Exit says
        // the pointer left, and a captured drag reports where the pointer went after leaving.
        val captured = captures[event.pointerId]
        val ignoreBounds = captured != null || event.type == PointerEventType.Exit
        event.isCaptureHolder = captured === node
        for (h in handlers.indices) {
            node.drawBoundsAt(node.pointerDepths[h], linkBounds)
            val localX = x - node.absoluteX - linkBounds[0]
            val localY = y - node.absoluteY - linkBounds[1]
            val inside = linkBounds.holds(localX, localY, handlers[h].hitMarginPx)
            if (!ignoreBounds && !inside) continue
            event.isInBounds = inside
            event.x = localX
            event.y = localY
            val consumedBefore = event.isConsumed
            handlers[h].onPointerEvent(event, pass)
            if (!consumedBefore && event.isConsumed && consumer == null) consumer = node
        }
    }

    private fun updateCapture(event: PointerEvent) {
        when (event.type) {
            PointerEventType.Press -> if (event.isConsumed) {
                captures[event.pointerId] = consumer ?: path.lastOrNull() ?: return
                heldSeconds[event.pointerId] = 0f
                longPressTriggered.remove(event.pointerId)
            }
            PointerEventType.Release -> {
                captures.remove(event.pointerId)
                heldSeconds.remove(event.pointerId)
                longPressTriggered.remove(event.pointerId)
            }
            else -> Unit
        }
    }

    private companion object {
        const val LONG_PRESS_SECONDS = 0.5f
    }

    /**
     * Fills [into] root-first with the nodes under the point, or leaves it untouched.
     *
     * Reverse of paint order: whatever is drawn last is on top, so it is offered the event first.
     */
    private fun hitTest(into: MutableList<LayoutNode>, node: LayoutNode, x: Int, y: Int): Boolean {
        if (!node.contains(x, y)) return false
        into.add(node)
        descendInto(into, node, x, y)
        return true
    }

    /** Layers first, highest kind on top, then children -- all in reverse declaration order. */
    private fun descendInto(into: MutableList<LayoutNode>, node: LayoutNode, x: Int, y: Int) {
        for (i in hitOrder.indices) {
            val kind = hitOrder[i]
            for (j in node.layers.indices.reversed()) {
                val layer = node.layers[j]
                if (layer.nodeType == kind && hitTest(into, layer, x, y)) return
            }
        }
        // A layer declared under an early sibling (a top bar's popup) is painted above a later
        // sibling (the workspace). Check every descendant layer before ordinary child content so
        // input follows that same global overlay ordering.
        for (i in node.children.indices.reversed()) {
            if (hitDetachedLayer(into, node.children[i], x, y)) return
        }
        val count = node.children.size
        if (count == 0) return
        var hasNonZeroZ = false
        for (i in 0 until count) {
            if (node.children[i].zIndex != 0f) {
                hasNonZeroZ = true
                break
            }
        }
        if (!hasNonZeroZ) {
            for (i in node.children.indices.reversed()) {
                if (hitTest(into, node.children[i], x, y)) return
            }
        } else {
            val indices = Array(count) { it }
            indices.sortWith { a, b ->
                val zA = node.children[a].zIndex
                val zB = node.children[b].zIndex
                if (zA != zB) zB.compareTo(zA) else b.compareTo(a)
            }
            for (i in 0 until count) {
                if (hitTest(into, node.children[indices[i]], x, y)) return
            }
        }
    }

    /**
     * Layers may be positioned outside their declaring node (an anchored popup under a small
     * trigger), so a miss on that node must not make its layer subtree unreachable to input.
     */
    private fun hitDetachedLayer(into: MutableList<LayoutNode>, node: LayoutNode, x: Int, y: Int): Boolean {
        into.add(node)
        for (i in hitOrder.indices) {
            val kind = hitOrder[i]
            for (j in node.layers.indices.reversed()) {
                val layer = node.layers[j]
                if (layer.nodeType == kind && hitTest(into, layer, x, y)) return true
            }
        }
        for (i in node.children.indices.reversed()) {
            if (hitDetachedLayer(into, node.children[i], x, y)) return true
        }
        into.removeAt(into.lastIndex)
        return false
    }

    /** Rebuilds the root-first path to a captured node, so its ancestors still see the passes. */
    private fun buildPathTo(node: LayoutNode, target: LayoutNode): Boolean {
        path.add(node)
        var found = node === target
        var i = 0
        while (!found && i < node.children.size) {
            found = buildPathTo(node.children[i], target)
            i++
        }
        i = 0
        while (!found && i < node.layers.size) {
            found = buildPathTo(node.layers[i], target)
            i++
        }
        if (!found) path.removeAt(path.lastIndex)
        return found
    }

    private fun deliver(
        event: PointerEvent,
        x: Int,
        y: Int,
        pass: PointerEventPass,
        rootFirst: Boolean,
    ) {
        val indices = if (rootFirst) path.indices else path.indices.reversed()
        for (i in indices) {
            val node = path[i]
            if (node.pointerInputs.isEmpty()) continue
            deliverToLinks(node, event, x, y, pass)
        }
    }
}

/**
 * Whether the point is anywhere this node could have put something.
 *
 * The union of the node's own box and its content's, not the box alone. A `Modifier.offset` reports
 * its child's size and then places it somewhere else, so the node's box stays where the parent put
 * it while everything inside -- every draw, every pointer link -- is elsewhere. The two never
 * overlap, and the node was unclickable at the old position and the new one alike: the walk stopped
 * at this gate before any link was offered the event.
 *
 * Deliberately permissive rather than exact. Every pointer link is filtered again against its own
 * box in [deliverToLinks] and every child against its own bounds, so widening this can only let a
 * walk continue that would otherwise have stopped early -- never deliver an event to something the
 * pointer is not over.
 */
private fun LayoutNode.contains(x: Int, y: Int): Boolean {
    val margin = maxPointerHitMargin
    val left = minOf(absoluteX, contentAbsoluteX) - margin
    val top = minOf(absoluteY, contentAbsoluteY) - margin
    val right = maxOf(absoluteX, contentAbsoluteX) + width + margin
    val bottom = maxOf(absoluteY, contentAbsoluteY) + height + margin
    return x >= left && y >= top && x < right && y < bottom
}

/**
 * Capture ends when the holding node leaves the tree.
 *
 * Without this a reconcile that removes the node mid-drag strands the pointer, and every later
 * event is delivered to something no longer laid out.
 */
private fun LayoutNode.isAttachedTo(root: LayoutNode): Boolean {
    var found = this === root
    var i = 0
    while (!found && i < root.children.size) {
        found = isAttachedTo(root.children[i])
        i++
    }
    i = 0
    while (!found && i < root.layers.size) {
        found = isAttachedTo(root.layers[i])
        i++
    }
    return found
}

/**
 * True if the point falls inside the `x, y, width, height` a link was given, grown by [margin].
 *
 * [margin] is hit area only: the box itself is where the link was laid out and drawn, so growing it
 * here lets a one-pixel divider be grabbed without moving anything or taking width from its
 * neighbours.
 */
private fun IntArray.holds(x: Int, y: Int, margin: Int = 0): Boolean =
    x >= -margin && y >= -margin && x < this[2] + margin && y < this[3] + margin
