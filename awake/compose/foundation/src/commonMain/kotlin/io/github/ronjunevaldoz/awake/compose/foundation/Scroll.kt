// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.graphics.drawscope.DrawScope
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEvent
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventPass
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventType
import io.github.ronjunevaldoz.awake.compose.ui.layout.IntrinsicMeasurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.Measurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureResult
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureScope
import io.github.ronjunevaldoz.awake.compose.ui.node.DrawModifierNode
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutModifierNode
import io.github.ronjunevaldoz.awake.compose.ui.node.PointerInputNode
import io.github.ronjunevaldoz.awake.compose.ui.node.ScrollableNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.Density

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
): Modifier = if (!enabled) this else this then ScrollNode(state)

private const val WHEEL_STEP_PX = 40f

private class ScrollNode(private val state: ScrollState) :
    LayoutModifierNode,
    DrawModifierNode,
    PointerInputNode,
    ScrollableNode {

    override val canScroll: Boolean
        get() = state.canScrollForward || state.canScrollBackward

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // Unbounded on the scroll axis: the child says how tall it truly is, and the difference
        // between that and the viewport is exactly how far there is to scroll.
        val content = measurable.measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
        )
        val viewport = constraints.constrainHeight(content.height)
        state.viewportSize = viewport
        state.maxValue = (content.height - viewport).coerceAtLeast(0)
        return layout(content.width, viewport) {
            content.placeAt(0, -state.value)
        }
    }

    // Height is the viewport's, not the content's: a scrollable asked how tall it wants to be must
    // not answer with its content, or a Column would give it all the room and it would never scroll.
    override fun Density.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int = 0

    override fun DrawScope.draw(drawContent: () -> Unit) {
        clipped { drawContent() }
    }

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main || event.type != PointerEventType.Wheel) return
        // Consumed only if it actually moved. A list already at its end must let the event through,
        // or an outer scrollable can never take over at the boundary.
        val consumed = state.scrollBy((-event.scrollDelta * WHEEL_STEP_PX).toInt())
        if (consumed != 0) event.consume()
    }

    override fun toString(): String = "verticalScroll(${state.value}/${state.maxValue})"
}
