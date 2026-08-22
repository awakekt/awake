// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.layout

import io.github.ronjunevaldoz.awake.compose.ui.Alignment
import io.github.ronjunevaldoz.awake.compose.ui.layout.Measurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasurePolicy
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureResult
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureScope
import io.github.ronjunevaldoz.awake.compose.ui.layout.Placeable
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints

/**
 * Stacks children on top of each other, sized to the largest.
 *
 * Shrink-wraps to content, matching Compose. `ui-core`'s `box()` defaults to `FillMax`, which
 * `docs/reference/mirror-map.md` records as a divergence -- not carried forward.
 */
class BoxMeasurePolicy(
    private val horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    private val verticalAlignment: Alignment.Vertical = Alignment.Top,
) : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val count = measurables.size
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
        for (i in 0 until count) {
            val placeable = measurables[i].measure(childConstraints)
            placeables[i] = placeable
            maxWidth = maxOf(maxWidth, placeable.width)
            maxHeight = maxOf(maxHeight, placeable.height)
        }

        val width = constraints.constrainWidth(maxWidth)
        val height = constraints.constrainHeight(maxHeight)
        return layout(width, height) {
            for (i in 0 until count) {
                val placeable = placeables[i] ?: continue
                placeable.placeAt(
                    horizontalAlignment.align(placeable.width, width),
                    verticalAlignment.align(placeable.height, height),
                )
            }
        }
    }
}
