/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.layout.Placeable
import com.awakekt.awake.compose.ui.layout.PlacementScope
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.IntOffset
import com.awakekt.awake.compose.ui.unit.IntSize
import com.awakekt.awake.compose.ui.unit.LayoutDirection

/**
 * Stacks children on top of each other, sized to the largest.
 *
 * Shrink-wraps to content, matching Compose. The removed `ui-core`'s `box()` defaulted to
 * `FillMax`, a divergence from Compose this engine did not carry forward.
 */
class BoxMeasurePolicy(
    internal val alignment: Alignment = Alignment.TopStart,
) : MeasurePolicy {

    constructor(
        horizontalAlignment: Alignment.Horizontal,
        verticalAlignment: Alignment.Vertical,
    ) : this(AxisAlignment(horizontalAlignment, verticalAlignment))

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val count = measurables.size
        // A leaf box -- an icon, a swatch, a spacer -- has nothing to place, and needs neither the
        // array nor a placement block, each of which would be a new object every measure.
        if (count == 0) return layout(constraints.minWidth, constraints.minHeight, place = PlaceNothing)
        val placeables = arrayOfNulls<Placeable>(count)
        // Children never inherit the box's own minimum -- a Box with a minimum size must not force
        // every child to match it. Only the box itself honours the minimum, below.
        val childConstraints = Constraints.of(
            0,
            constraints.maxWidth,
            0,
            constraints.maxHeight,
        )
        var maxWidth = 0
        var maxHeight = 0
        var hasMatchParentSize = false
        // Two passes, because a matchParentSize child is sized *by* the box and so cannot be part
        // of what decides the box's size. Every child is still measured exactly once.
        for (i in 0 until count) {
            if (measurables[i].matchesParentSize()) {
                hasMatchParentSize = true
                continue
            }
            val placeable = measurables[i].measure(childConstraints)
            placeables[i] = placeable
            maxWidth = maxOf(maxWidth, placeable.width)
            maxHeight = maxOf(maxHeight, placeable.height)
        }

        val width = constraints.constrainWidth(maxWidth)
        val height = constraints.constrainHeight(maxHeight)
        if (hasMatchParentSize) {
            // Exact, not a maximum: the point is to be the box's size, and a child free to measure
            // smaller would leave the gap this exists to close.
            val exact = Constraints.of(width, width, height, height)
            for (i in 0 until count) {
                if (placeables[i] == null) placeables[i] = measurables[i].measure(exact)
            }
        }
        return layout(width, height) {
            val space = IntSize(width, height)
            for (i in 0 until count) {
                val placeable = placeables[i] ?: continue
                // A child's own alignment wins over the box's: five anchors in one Box is the
                // whole reason `BoxScope.align` exists.
                val anchor = measurables[i].childAlignment() ?: alignment
                val offset = anchor.align(IntSize(placeable.width, placeable.height), space, layoutDirection)
                placeable.placeAbsoluteAt(offset.x, offset.y)
            }
        }
    }
}

private val PlaceNothing: PlacementScope.() -> Unit = {}

/**
 * [horizontal] and [vertical] as one [Alignment]. A class rather than a lambda through `Alignment`'s
 * SAM constructor, which Kotlin/Wasm wraps in a generic function that boxes its `IntSize` arguments
 * and `IntOffset` result on every child placed (#1512).
 */
internal data class AxisAlignment(
    val horizontal: Alignment.Horizontal,
    val vertical: Alignment.Vertical,
) : Alignment {
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset =
        IntOffset(horizontal.align(size.width, space.width, layoutDirection), vertical.align(size.height, space.height))
}
