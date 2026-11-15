/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.layout.Placeable
import com.awakekt.awake.compose.ui.layout.PlacementScope
import com.awakekt.awake.compose.ui.node.LayoutModifierNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Density
import com.awakekt.awake.compose.ui.unit.IntOffset

/**
 * Moves the content by the pixels [offset] returns, read while placing: neither composing nor
 * measuring sees it (#1512, the placement-only path).
 *
 * The `Dp` overload takes its value when the modifier is built and applies it while measuring, so a
 * value that changes every frame has to compose and measure again to move the node. This one calls
 * [offset] inside the placement block only, every frame the node is placed. Read a moving value --
 * a dragged window's position, a marker that follows the pointer -- here, not in the composable
 * body, and with `ComposeHost.skipUnchangedComposition` and `ComposeHost.skipUnchangedMeasure` on a
 * frame that only moves it composes nothing and measures nothing: the node and its subtree keep
 * their sizes and are placed again. With them off, the frame costs what it did. Compose's
 * `Modifier.offset { }`.
 *
 * Like the `Dp` overload, the node reports its unmoved size, so siblings never move with it, and the
 * x offset mirrors in right-to-left layout. The offset is in whole pixels and no sub-pixel remainder
 * is drawn. [offset] runs on the frame thread once per placement and should not allocate; the
 * [IntOffset] it returns is boxed by the function type, one small object per call.
 */
fun Modifier.offset(offset: Density.() -> IntOffset): Modifier = this then PlacementOffsetElement(offset)

private class PlacementOffsetElement(
    private val offset: Density.() -> IntOffset,
) : ModifierNodeElement<PlacementOffsetNode>() {
    override fun create(): PlacementOffsetNode = PlacementOffsetNode(offset)

    override fun update(node: PlacementOffsetNode) {
        node.offset = offset
    }

    override fun toString(): String = "offset { }"
}

private class PlacementOffsetNode(var offset: Density.() -> IntOffset) :
    Modifier.Node(),
    LayoutModifierNode {
    private var placeable: Placeable? = null
    private var scope: Density? = null

    // One block for the node's life, so measuring hands back no new lambda and placing again after a
    // skipped measure reads [offset] afresh.
    private val place: PlacementScope.() -> Unit = {
        val content = placeable
        val density = scope
        if (content != null && density != null) {
            val moved = density.offset()
            content.placeRelativeAt(moved.x, moved.y)
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val content = measurable.measure(constraints)
        placeable = content
        scope = this
        return layout(content.width, content.height, place = place)
    }

    override fun toString(): String = "offset { }"
}
