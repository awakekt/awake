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
import kotlin.math.abs

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
    private val longPressConsumed = mutableSetOf<Long>()

    // Where each captured press went down, in root pixels, so a press that moved off is a drag.
    private val pressOrigins = mutableMapOf<Long, Long>()

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

    // This dispatch's stamp on the nodes with a layer somewhere among their descendants. A layer
    // may sit outside its declaring node, so a walk looks for one below each child of its root;
    // the stamps keep that look out of the subtrees with no layer, which is most of them. A stamp
    // on the node rather than a set, so marking them allocates nothing per pointer move.
    private var layerStamp = 0

    /** Hit-test walk steps taken by the last dispatch, for tests that keep the walk linear. */
    internal var hitSteps: Int = 0
        private set

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
        event.longPressConsumed = event.pointerId in longPressConsumed
        event.dx = dx
        event.dy = dy
        path.clear()
        consumer = null
        hitSteps = 0

        // A modal owns the frame: nothing outside it is hit, so a click on the backdrop reaches
        // nothing rather than falling through to the page behind. Derived from the tree, not
        // registered -- `ui-core`'s registerOverlayOcclusion was correct only if a widget remembered
        // to call it, and 07-overlay-layering.md names that as the structural problem.
        val modal = root.activeModalLayer()
        isModalOpen = modal != null
        val dismissable = root.activeDismissableLayer()
        if (dismissable != null && !dismissable.contains(x, y)) {
            if (event.type == PointerEventType.Press) {
                dismissable.onDismissRequest?.invoke()
                event.consume()
                if (event.pointerId == MOUSE_POINTER) {
                    indexLayers(root)
                    updateHover(modal ?: root, x, y)
                }
                return true
            } else if (event.type == PointerEventType.SecondaryPress) {
                dismissable.onDismissRequest?.invoke()
            }
        }
        val hitRoot = modal ?: root
        // After onDismissRequest, the only code of the app's that runs before the walks: a dispatch
        // it made would leave its own stamps behind.
        indexLayers(root)

        val holder = captures[event.pointerId]
        if (holder != null && holder.isAttachedTo(root)) {
            // A drag that leaves its node keeps arriving: that is what capture is for.
            buildPathTo(root, holder)
        } else {
            captures.remove(event.pointerId)
            heldSeconds.remove(event.pointerId)
            longPressTriggered.remove(event.pointerId)
            longPressConsumed.remove(event.pointerId)
            pressOrigins.remove(event.pointerId)
            hitTest(path, hitRoot, x, y, searchLayers = true)
        }
        // Before the empty-path return: a pointer leaving everything must still un-hover what it left.
        // Only the mouse hovers: a finger has no hover, and one would stay "over" whatever it lifted from.
        if (event.pointerId == MOUSE_POINTER) updateHover(hitRoot, x, y)
        if (path.isEmpty()) return false

        deliver(event, x, y, PointerEventPass.Initial, rootFirst = true)
        deliver(event, x, y, PointerEventPass.Main, rootFirst = false)
        deliver(event, x, y, PointerEventPass.Final, rootFirst = true)

        updateCapture(event)
        if (event.type == PointerEventType.Press && event.pointerId in captures) {
            pressOrigins[event.pointerId] = packPosition(x, y)
        }
        updateFocus(root, event)
        return event.isConsumed
    }

    /** Advances a captured press and dispatches one long-press event after [LONG_PRESS_SECONDS]. */
    fun advanceTime(root: LayoutNode, x: Int, y: Int, deltaSeconds: Float, pointerId: Long = 0L) {
        if (pointerId !in captures || pointerId in longPressTriggered) return
        // A press that moved off where it went down is a drag, and a held drag is not a long
        // press: a finger dragging a list row must not open the row's context menu.
        val origin = pressOrigins[pointerId]
        if (origin != null && (abs(x - unpackX(origin)) > LONG_PRESS_SLOP_PX || abs(y - unpackY(origin)) > LONG_PRESS_SLOP_PX)) {
            longPressTriggered += pointerId
            return
        }
        val held = (heldSeconds[pointerId] ?: 0f) + deltaSeconds.coerceAtLeast(0f)
        heldSeconds[pointerId] = held
        if (held >= LONG_PRESS_SECONDS) {
            longPressTriggered += pointerId
            // A long press something acted on, such as opening a context menu, makes its release no
            // click. The capture stays, so the release still reaches whoever acted, to close a tooltip.
            if (dispatch(root, PointerEvent(PointerEventType.LongPress, pointerId = pointerId), x, y)) {
                longPressConsumed += pointerId
            }
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
        hitTest(hoverPath, root, x, y, searchLayers = true)
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
                longPressConsumed.remove(event.pointerId)
                pressOrigins.remove(event.pointerId)
            }
            else -> Unit
        }
    }

    private companion object {
        const val LONG_PRESS_SECONDS = 0.5f

        // What a hit test found: nothing, a path of nodes that do nothing with a pointer there, or a
        // path with one that does.
        const val MISS = 0
        const val HELD = 1
        const val TAKEN = 2
    }

    /**
     * Fills [into] root-first with the nodes under the point, or leaves it untouched. Returns [MISS]
     * when [node] doesn't hold the point, [TAKEN] when a node on the path it added does something
     * with a pointer there, and [HELD] when none does.
     *
     * Reverse of paint order: whatever is drawn last is on top, so it is offered the event first.
     *
     * [searchLayers] is for the root of a walk and for a layer: see [descendInto].
     */
    private fun hitTest(into: MutableList<LayoutNode>, node: LayoutNode, x: Int, y: Int, searchLayers: Boolean): Int {
        hitSteps++
        if (!node.contains(x, y)) return MISS
        into.add(node)
        return if (descendInto(into, node, x, y, searchLayers) || node.takes(x, y)) TAKEN else HELD
    }

    /**
     * Layers first, highest kind on top, then children -- all in reverse declaration order. True when
     * a node it added to [into] takes the point.
     *
     * Only the root of a walk, or a layer, looks for layers below it. That look offers the point to
     * every layer under the node's children, and a layer holds the point or not by where it is
     * placed, so the same look again a level down could only miss them again. Looking at every
     * level walked the layer-bearing part of the tree once per level.
     */
    private fun descendInto(into: MutableList<LayoutNode>, node: LayoutNode, x: Int, y: Int, searchLayers: Boolean): Boolean {
        if (searchLayers) {
            val own = hitOwnLayers(into, node, x, y)
            if (own != MISS) return own == TAKEN
            // A layer declared under an early sibling (a top bar's popup) is painted above a later
            // sibling (the workspace). Check every descendant layer before ordinary child content so
            // input follows that same global overlay ordering.
            for (i in node.children.size - 1 downTo 0) {
                val detached = hitDetachedLayer(into, node.children[i], x, y)
                if (detached != MISS) return detached == TAKEN
            }
        }
        val count = node.children.size
        if (count == 0) return false
        var hasNonZeroZ = false
        for (i in 0 until count) {
            if (node.children[i].zIndex != 0f) {
                hasNonZeroZ = true
                break
            }
        }
        if (!hasNonZeroZ) return hitChildren(into, node, x, y, null)
        val indices = Array(count) { it }
        indices.sortWith { a, b ->
            val zA = node.children[a].zIndex
            val zB = node.children[b].zIndex
            if (zA != zB) zB.compareTo(zA) else b.compareTo(a)
        }
        return hitChildren(into, node, x, y, indices)
    }

    /**
     * Offers the point to [node]'s children topmost first, stopping at the first that takes it, and
     * returns whether one did.
     *
     * A child whose box holds the point but whose subtree does nothing with a pointer there -- the
     * padding box a layout wraps around a child, a decorative panel -- is passed over, so a handler
     * beneath it is still reachable. That is how Compose hit-tests: only a node with pointer input
     * is hit, and a box without any is transparent to the pointer. When no child takes the point,
     * the topmost one that held it is kept, as the walk always has, so a point over nothing but
     * plain boxes still finds the innermost of them.
     *
     * [order] is the children's indices in hit order, or null for reverse declaration order.
     */
    private fun hitChildren(into: MutableList<LayoutNode>, node: LayoutNode, x: Int, y: Int, order: Array<Int>?): Boolean {
        val count = node.children.size
        // The first child that held the point but took nothing keeps its path where it is, from
        // keptFrom to keptTo, in case no later child takes the point. Walking it again at the end
        // instead doubled the work at every level of plain boxes, as over a 3D view.
        var keptFrom = -1
        var keptTo = -1
        for (n in 0 until count) {
            val child = node.children[if (order == null) count - 1 - n else order[n]]
            val mark = into.size
            val hit = hitTest(into, child, x, y, searchLayers = false)
            if (hit == MISS) continue
            if (hit == TAKEN) {
                // The kept path is under the taker's, which starts at keptTo: cut it out by moving
                // the taker's down over it once.
                val cut = keptTo - keptFrom
                if (cut > 0) {
                    for (i in keptTo until into.size) into[i - cut] = into[i]
                    repeat(cut) { into.removeAt(into.lastIndex) }
                }
                return true
            }
            if (keptFrom < 0) {
                keptFrom = mark
                keptTo = into.size
            } else {
                while (into.size > mark) into.removeAt(into.lastIndex)
            }
        }
        return false
    }

    /**
     * Whether this node does anything with a pointer at ([x], [y]): a pointer link whose own box holds
     * it, or something that scrolls, takes focus or asks for a cursor.
     */
    private fun LayoutNode.takes(x: Int, y: Int): Boolean {
        var takes = scrollables.isNotEmpty() || focusTargets.isNotEmpty() || pointerCursor != null
        var h = 0
        while (!takes && h < pointerInputs.size) {
            drawBoundsAt(pointerDepths[h], linkBounds)
            takes = linkBounds.holds(x - absoluteX - linkBounds[0], y - absoluteY - linkBounds[1], pointerInputs[h].hitMarginPx)
            h++
        }
        return takes
    }

    /**
     * Layers may be positioned outside their declaring node (an anchored popup under a small
     * trigger), so a miss on that node must not make its layer subtree unreachable to input.
     *
     * Follows children, not layers: a layer is entered only through [hitTest], when it holds the
     * point, so a layer declared in another and placed outside it is not reached.
     */
    private fun hitDetachedLayer(into: MutableList<LayoutNode>, node: LayoutNode, x: Int, y: Int): Int {
        // Most subtrees hold no layer at all; nothing below them can be detached.
        if (node.layerStamp != layerStamp) return MISS
        hitSteps++
        into.add(node)
        var hit = hitOwnLayers(into, node, x, y)
        var i = node.children.size - 1
        while (hit == MISS && i >= 0) {
            hit = hitDetachedLayer(into, node.children[i], x, y)
            i--
        }
        if (hit == MISS) {
            into.removeAt(into.lastIndex)
            return MISS
        }
        return if (hit == TAKEN || node.takes(x, y)) TAKEN else HELD
    }

    /**
     * Offers the point to [node]'s own layers, highest kind first, each kind in reverse declaration
     * order, and returns the hit test of the first that holds it, or [MISS].
     */
    private fun hitOwnLayers(into: MutableList<LayoutNode>, node: LayoutNode, x: Int, y: Int): Int {
        if (node.layers.size == 0) return MISS
        for (i in hitOrder.indices) {
            val kind = hitOrder[i]
            for (j in node.layers.size - 1 downTo 0) {
                val layer = node.layers[j]
                if (layer.nodeType != kind) continue
                val hit = hitTest(into, layer, x, y, searchLayers = true)
                if (hit != MISS) return hit
            }
        }
        return MISS
    }

    /** Stamps every node under [root] that has a layer of its own or below it, for this dispatch. */
    private fun indexLayers(root: LayoutNode) {
        // Never 0, the stamp a node starts with; wrapping after two billion dispatches is harmless.
        layerStamp = if (layerStamp == Int.MAX_VALUE) 1 else layerStamp + 1
        markLayerBearing(root)
    }

    private fun markLayerBearing(node: LayoutNode): Boolean {
        var bearing = node.layers.size > 0
        for (i in 0 until node.children.size) {
            if (markLayerBearing(node.children[i])) bearing = true
        }
        // A walk inside a layer looks for the layers below its children too, so the nodes inside a
        // layer need stamps of their own. Whether they bear one doesn't matter here: this node's
        // own layer already makes it bearing.
        for (i in 0 until node.layers.size) markLayerBearing(node.layers[i])
        if (bearing) node.layerStamp = layerStamp
        return bearing
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

// About Android's 8 dp touch slop on a phone; the dispatcher works in pixels and has no density.
private const val LONG_PRESS_SLOP_PX = 24

private fun packPosition(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

private fun unpackX(packed: Long): Int = (packed shr 32).toInt()

private fun unpackY(packed: Long): Int = packed.toInt()

/** Pointer id 0 is the mouse's; fingers have their own. */
private const val MOUSE_POINTER = 0L
