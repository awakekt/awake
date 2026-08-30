/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.graphics.drawscope.DrawScope
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEvent
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventPass
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventType
import io.github.awakelab.awake.compose.ui.layout.IntrinsicMeasurable
import io.github.awakelab.awake.compose.ui.layout.Measurable
import io.github.awakelab.awake.compose.ui.layout.MeasureResult
import io.github.awakelab.awake.compose.ui.layout.MeasureScope
import io.github.awakelab.awake.compose.ui.node.DrawModifierNode
import io.github.awakelab.awake.compose.ui.node.LayoutModifierNode
import io.github.awakelab.awake.compose.ui.node.PointerInputNode
import io.github.awakelab.awake.compose.ui.node.ScrollableNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.Density

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

    override val canScroll: Boolean
        get() = state.canScrollForward || state.canScrollBackward

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // Unbounded on the scroll axis: the child says how big it truly is, and the difference
        // between that and the viewport is exactly how far there is to scroll.
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
        if (pass != PointerEventPass.Main || event.type != PointerEventType.Wheel) return
        val remaining = event.remainingScrollDelta
        if (remaining == 0f) return
        // Main runs leaf-to-root. Take only the inner viewport's available movement, then hand the
        // exact wheel remainder to the parent; at an edge the full delta naturally bubbles out.
        val consumed = state.scrollBy((-remaining * WHEEL_STEP_PX).toInt())
        if (consumed != 0) event.consumeScrollDelta(-consumed / WHEEL_STEP_PX)
    }

    override fun toString(): String = "scroll($orientation, ${state.value}/${state.maxValue})"
}
