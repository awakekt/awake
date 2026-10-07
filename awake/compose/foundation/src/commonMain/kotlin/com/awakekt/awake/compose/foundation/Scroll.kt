/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.layout.IntrinsicMeasurable
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.compose.ui.node.LayoutModifierNode
import com.awakekt.awake.compose.ui.node.PointerInputNode
import com.awakekt.awake.compose.ui.node.ScrollableNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Density
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

/**
 * Lets content taller than its box scroll vertically.
 *
 * The content is measured with an **unbounded** main axis and the node reports the viewport's size,
 * which is the whole mechanism: constraints down, sizes up, and the overflow becomes [state]'s
 * range rather than a layout error.
 *
 * Clips by default. An unclipped scroller paints its overflow over whatever is next to it, and the
 * symptom -- a list bleeding across a panel divider -- reads as a z-order bug rather than a missing
 * clip.
 */
fun Modifier.verticalScroll(
    state: ScrollState,
    enabled: Boolean = true,
): Modifier = if (!enabled) this else this then ScrollElement(state, Orientation.Vertical)

/**
 * The same, along the other axis.
 *
 * One node parameterised by [Orientation] rather than a second copy: the two differ only in which
 * constraint is loosened and which coordinate is offset, and a copy is where the two quietly drift
 * apart -- exactly what the three duplicated clip implementations in `graphics2d` cost.
 */
fun Modifier.horizontalScroll(
    state: ScrollState,
    enabled: Boolean = true,
): Modifier = if (!enabled) this else this then ScrollElement(state, Orientation.Horizontal)

/** Which axis a gesture or a scroller works along. */
enum class Orientation { Vertical, Horizontal }

private const val WHEEL_STEP_PX = 40f
private val TOUCH_SCROLL_SLOP = 8.dp

private class ScrollElement(
    private val state: ScrollState,
    private val orientation: Orientation,
) : ModifierNodeElement<ScrollNode>() {
    override fun create(): ScrollNode = ScrollNode()

    override fun update(node: ScrollNode) {
        node.state = state
        node.orientation = orientation
    }
}

private class ScrollNode :
    Modifier.Node(),
    LayoutModifierNode,
    DrawModifierNode,
    PointerInputNode,
    ScrollableNode {

    lateinit var state: ScrollState
    lateinit var orientation: Orientation
    private var touchSlopPx = 0

    override val canScroll: Boolean
        get() = state.canScrollForward || state.canScrollBackward

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // Unbounded on the scroll axis: the child says how big it truly is, and the difference
        // between that and the viewport is exactly how far there is to scroll.
        touchSlopPx = TOUCH_SCROLL_SLOP.roundToPx()
        val vertical = orientation == Orientation.Vertical
        val content = measurable.measure(
            if (vertical) {
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            } else {
                constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
            },
        )
        val extent = if (vertical) content.height else content.width
        val viewport = if (vertical) {
            constraints.constrainHeight(extent)
        } else {
            constraints.constrainWidth(extent)
        }
        state.viewportSize = viewport
        state.maxValue = (extent - viewport).coerceAtLeast(0)
        val width = if (vertical) content.width else viewport
        val height = if (vertical) viewport else content.height
        return layout(width, height) {
            if (vertical) content.placeAt(0, -state.value) else content.placeAt(-state.value, 0)
        }
    }

    // The viewport's, not the content's: a scrollable asked how big it wants to be must not answer
    // with its content, or a Column would give it all the room and it would never scroll.
    override fun Density.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        if (orientation == Orientation.Vertical) 0 else measurable.minIntrinsicHeight(width)

    override fun Density.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        if (orientation == Orientation.Horizontal) 0 else measurable.minIntrinsicWidth(height)

    /**
     * Clips the scrolled content to this node's viewport before painting it.
     *
     * This is intentionally a full rectangular clip. Compose's scroll container preserves a
     * cross-axis margin for elevation shadows; Awake keeps the stricter behavior until it has an
     * equivalent axis-aware clip shape.
     */
    override fun DrawScope.draw(drawContent: () -> Unit) {
        clipped { drawContent() }
    }

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (event.pointerId != 0L) handleTouch(event, pass)
        if (pass != PointerEventPass.Main || event.type != PointerEventType.Wheel) return
        val remaining = event.remainingScrollDelta
        if (remaining == 0f) return
        // Main runs leaf-to-root. Take only the inner viewport's available movement, then hand the
        // exact wheel remainder to the parent; at an edge the full delta naturally bubbles out.
        val consumed = state.scrollBy((-remaining * WHEEL_STEP_PX).toInt())
        if (consumed != 0) event.consumeScrollDelta(-consumed / WHEEL_STEP_PX)
    }

    private fun handleTouch(event: PointerEvent, pass: PointerEventPass) {
        if (pass == PointerEventPass.Initial) {
            when (event.type) {
                PointerEventType.Press -> if (canScroll) state.touchGestures[event.pointerId] = ScrollTouchGesture()
                // Before a child sees Release: a swipe that began on a button must never click it.
                PointerEventType.Release -> if (state.touchGestures.remove(event.pointerId)?.dragging == true) event.consume()
                else -> Unit
            }
        }
        if (pass != PointerEventPass.Main || event.isConsumed) return
        when (event.type) {
            // Child controls get first refusal; otherwise capture a press in empty list space.
            PointerEventType.Press -> if (canScroll) event.consume()
            PointerEventType.Move -> scrollTouchMove(event)
            else -> Unit
        }
    }

    private fun scrollTouchMove(event: PointerEvent) {
        val gesture = state.touchGestures[event.pointerId] ?: return
        gesture.distanceX += event.dx
        gesture.distanceY += event.dy
        val vertical = orientation == Orientation.Vertical
        val distance = if (vertical) gesture.distanceY else gesture.distanceX
        val crossDistance = if (vertical) gesture.distanceX else gesture.distanceY
        if (!gesture.dragging && (abs(distance) <= touchSlopPx || abs(distance) < abs(crossDistance))) return
        val movement = if (gesture.dragging) {
            if (vertical) event.dy else event.dx
        } else {
            distance - distance.sign * touchSlopPx
        }
        if (state.scrollBy(-movement) != 0) {
            gesture.dragging = true
            event.consume()
        }
    }

    override fun toString(): String = "scroll($orientation, ${state.value}/${state.maxValue})"
}
