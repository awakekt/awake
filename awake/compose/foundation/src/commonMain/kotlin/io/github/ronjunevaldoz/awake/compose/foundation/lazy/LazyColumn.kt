// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.ronjunevaldoz.awake.compose.foundation.lazy

import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.runtime.key
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEvent
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventPass
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventType
import io.github.ronjunevaldoz.awake.compose.ui.layout.Layout
import io.github.ronjunevaldoz.awake.compose.ui.layout.Measurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasurePolicy
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureResult
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureScope
import io.github.ronjunevaldoz.awake.compose.ui.node.PointerInputNode
import io.github.ronjunevaldoz.awake.compose.ui.node.ScrollableNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints

// PascalCase composables: Compose's own convention. See 11-refinements.md rule 1.

private object LazyColumnNodeType

private const val OVERSCAN_ITEMS = 2

private const val WHEEL_STEP_PX = 40f

/**
 * A vertical list that only builds the items near the viewport.
 *
 * Items may differ in height. `ui-core`'s `LazyList` forced one shared height on every row, and did
 * so only to avoid paying trial-measure cost per item -- there is no trial pass here, so the
 * compromise goes away and each item is measured for real.
 *
 * **Divergence from Compose, and it is visible.** Compose picks the visible window *during*
 * measurement, through subcomposition, so it is always exact. This engine composes before it
 * measures, so the window is chosen from the previous pass's measurements and an average for items
 * never yet seen. The list settles within a frame or two of a jump and is exact while scrolling
 * steadily. Closing the gap means measure-time composition, which is not built -- see
 * `08-lazy-lists.md`.
 */
context(composer: Composer)
fun LazyColumn(
    itemCount: Int,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    itemContent: context(Composer) (index: Int) -> Unit,
) {
    state.itemCount = itemCount
    val first = state.firstVisibleItemIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    val last = lastVisible(state, first, itemCount)

    Layout(
        LazyColumnNodeType,
        modifier = modifier then LazyScrollNode(state),
        measurePolicy = LazyColumnMeasurePolicy(state, first),
        content = {
            for (index in first..last) {
                // Keyed by item index, not position: scrolling reorders which items are present,
                // and positional identity would hand item 7's node to item 8 on the next frame.
                key(index) { itemContent(composer, index) }
            }
        },
    )
}

/**
 * The last item worth building: enough to fill the viewport, plus a little either side.
 *
 * Overscan exists because the window is chosen a frame late. Without it a fast scroll shows the
 * blank strip where the next item will be.
 */
private fun lastVisible(state: LazyListState, first: Int, itemCount: Int): Int {
    if (itemCount == 0) return -1
    val viewport = if (state.viewportSize > 0) state.viewportSize else state.estimatedItemHeight
    var filled = -state.firstVisibleItemScrollOffset
    var index = first
    while (index < itemCount - 1 && filled < viewport) {
        filled += state.heightOf(index)
        index++
    }
    return (index + OVERSCAN_ITEMS).coerceAtMost(itemCount - 1)
}

/** Stacks the built window, offset so [LazyListState.firstVisibleItemIndex] sits at the top edge. */
private class LazyColumnMeasurePolicy(
    private val state: LazyListState,
    private val firstIndex: Int,
) : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val itemConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(itemConstraints) }
        for (i in placeables.indices) {
            state.recordHeight(firstIndex + i, placeables[i].height)
        }
        val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
        val height = constraints.constrainHeight(
            if (constraints.hasBoundedHeight) constraints.maxHeight else placeables.sumOf { it.height },
        )
        state.viewportSize = height
        return layout(constraints.constrainWidth(width), height) {
            var y = -state.firstVisibleItemScrollOffset
            for (placeable in placeables) {
                placeable.placeAt(0, y)
                y += placeable.height
            }
        }
    }
}

private class LazyScrollNode(private val state: LazyListState) :
    PointerInputNode,
    ScrollableNode {

    override val canScroll: Boolean
        get() = state.canScrollForward || state.canScrollBackward

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main || event.type != PointerEventType.Wheel) return
        // Unconsumed at the ends, so an outer scrollable can take over at the boundary.
        if (state.scrollBy((-event.scrollDelta * WHEEL_STEP_PX).toInt()) != 0) event.consume()
    }

    override fun toString(): String = "lazyScroll(${state.firstVisibleItemIndex})"
}
