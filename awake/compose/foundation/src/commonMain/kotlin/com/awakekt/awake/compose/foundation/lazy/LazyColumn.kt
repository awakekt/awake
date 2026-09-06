/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation.lazy

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.layout.Placeable
import com.awakekt.awake.compose.ui.layout.SubcomposeLayout
import com.awakekt.awake.compose.ui.node.PointerInputNode
import com.awakekt.awake.compose.ui.node.ScrollableNode
import com.awakekt.awake.compose.ui.unit.Constraints

private const val WHEEL_STEP_PX = 40f

/**
 * A vertical list that only builds and measures the items visible in the viewport.
 *
 * Built on [SubcomposeLayout], subcomposing items during the measure pass so that the visible
 * window is exact on every frame with zero lag.
 */
context(composer: Composer)
fun LazyColumn(
    itemCount: Int,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    itemContent: context(Composer) (index: Int) -> Unit,
) {
    state.itemCount = itemCount

    SubcomposeLayout(
        modifier = modifier then LazyScrollElement(state),
    ) { constraints ->
        val firstIndex = state.firstVisibleItemIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        val itemConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)

        val placeables = mutableListOf<Placeable>()
        var currentIndex = firstIndex
        var accumulatedHeight = -state.firstVisibleItemScrollOffset
        val targetHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity

        if (itemCount > 0) {
            while (currentIndex < itemCount && (accumulatedHeight < targetHeight || placeables.isEmpty())) {
                val index = currentIndex
                val measurables = subcompose(index) {
                    itemContent(index)
                }
                for (measurable in measurables) {
                    val placeable = measurable.measure(itemConstraints)
                    placeables.add(placeable)
                    state.recordHeight(index, placeable.height)
                    accumulatedHeight += placeable.height
                }
                currentIndex++
            }
        }

        val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
        val height = constraints.constrainHeight(
            if (constraints.hasBoundedHeight) constraints.maxHeight else placeables.sumOf { it.height },
        )
        state.viewportSize = height

        layout(constraints.constrainWidth(width), height) {
            var y = -state.firstVisibleItemScrollOffset
            for (placeable in placeables) {
                placeable.placeAt(0, y)
                y += placeable.height
            }
        }
    }
}

private class LazyScrollElement(
    private val state: LazyListState,
) : ModifierNodeElement<LazyScrollNode>() {
    override fun create(): LazyScrollNode = LazyScrollNode()

    override fun update(node: LazyScrollNode) {
        node.state = state
    }
}

private class LazyScrollNode :
    Modifier.Node(),
    PointerInputNode,
    ScrollableNode {

    lateinit var state: LazyListState

    override val canScroll: Boolean
        get() = state.canScrollForward || state.canScrollBackward

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main || event.type != PointerEventType.Wheel) return
        // Unconsumed at the ends, so an outer scrollable can take over at the boundary.
        if (state.scrollBy((-event.scrollDelta * WHEEL_STEP_PX).toInt()) != 0) event.consume()
    }

    override fun toString(): String = "lazyScroll(${state.firstVisibleItemIndex})"
}
