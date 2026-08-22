// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.input.pointer

import io.github.ronjunevaldoz.awake.compose.ui.focus.FocusOwner
import io.github.ronjunevaldoz.awake.compose.ui.layout.LayerKind
import io.github.ronjunevaldoz.awake.compose.ui.node.activeModalLayer
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode

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

    // The node holding the pointer for the duration of a drag. Node identity, not a string id --
    // ui-core's tryClaimActive/releaseActiveIfMatches keyed this by id and could collide.
    private var captured: LayoutNode? = null

    /**
     * True while a node holds the pointer, which is what "the UI owns this input" means.
     *
     * Distinct from an event merely being consumed: a hover handler consumes nothing and a click
     * consumes one event, but neither means gameplay should stop reading the mouse.
     */
    val hasCapture: Boolean get() = captured != null

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
    fun dispatch(root: LayoutNode, event: PointerEvent, x: Int, y: Int): Boolean {
        event.reset()
        path.clear()

        // A modal owns the frame: nothing outside it is hit, so a click on the backdrop reaches
        // nothing rather than falling through to the page behind. Derived from the tree, not
        // registered -- `ui-core`'s registerOverlayOcclusion was correct only if a widget remembered
        // to call it, and 07-overlay-layering.md names that as the structural problem.
        val modal = root.activeModalLayer()
        isModalOpen = modal != null
        val hitRoot = modal ?: root

        val holder = captured
        if (holder != null && holder.isAttachedTo(root)) {
            // A drag that leaves its node keeps arriving: that is what capture is for.
            buildPathTo(root, holder)
        } else {
            captured = null
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
        val ignoreBounds = captured != null || event.type == PointerEventType.Exit
        event.isCaptureHolder = captured === node
        for (h in handlers.indices) {
            node.drawBoundsAt(node.pointerDepths[h], linkBounds)
            val localX = x - node.absoluteX - linkBounds[0]
            val localY = y - node.absoluteY - linkBounds[1]
            val inside = linkBounds.holds(localX, localY)
            if (!ignoreBounds && !inside) continue
            event.isInBounds = inside
            event.x = localX
            event.y = localY
            handlers[h].onPointerEvent(event, pass)
        }
    }

    private fun updateCapture(event: PointerEvent) {
        when (event.type) {
            PointerEventType.Press -> if (event.isConsumed) captured = path.lastOrNull()
            PointerEventType.Release -> captured = null
            else -> Unit
        }
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
        for (i in node.children.indices.reversed()) {
            if (hitTest(into, node.children[i], x, y)) return
        }
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

private fun LayoutNode.contains(x: Int, y: Int): Boolean =
    x >= absoluteX && y >= absoluteY && x < absoluteX + width && y < absoluteY + height

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

/** True if the point falls inside the `x, y, width, height` a link was given. */
private fun IntArray.holds(x: Int, y: Int): Boolean =
    x >= 0 && y >= 0 && x < this[2] && y < this[3]
