// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.layout

import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.layout.IntrinsicMeasurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.Measurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureResult
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureScope
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutModifierNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.Density

/** Which intrinsic answer a size request should take. */
enum class IntrinsicSize { Min, Max }

/**
 * Sizes this node to what its content asks for, rather than to a number.
 *
 * The primitive a dropdown needs: a menu should be as wide as its widest item, and nobody can write
 * that width down. `ui-core` has no equivalent, which is why `DropdownMenuIntrinsicWidthTest` exists
 * and why the button-group audit named this as a missing primitive rather than a modifier bug.
 *
 * Costs an extra walk of the subtree -- the query runs before the real measure. [LayoutStats]
 * counts them.
 */
fun Modifier.width(intrinsicSize: IntrinsicSize): Modifier =
    this then IntrinsicSizeNode(intrinsicSize, horizontal = true)

fun Modifier.height(intrinsicSize: IntrinsicSize): Modifier =
    this then IntrinsicSizeNode(intrinsicSize, horizontal = false)

private class IntrinsicSizeNode(
    private val intrinsicSize: IntrinsicSize,
    private val horizontal: Boolean,
) : LayoutModifierNode {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val size = if (horizontal) {
            resolve(measurable, constraints.maxHeight, horizontal = true)
        } else {
            resolve(measurable, constraints.maxWidth, horizontal = false)
        }
        // Tight on the chosen axis: the point is to *be* that size, not to be allowed up to it.
        val childConstraints = if (horizontal) {
            constraints.copy(minWidth = size, maxWidth = size)
        } else {
            constraints.copy(minHeight = size, maxHeight = size)
        }
        val placeable = measurable.measure(childConstraints)
        return layout(placeable.width, placeable.height) { placeable.placeAt(0, 0) }
    }

    private fun Density.resolve(
        measurable: IntrinsicMeasurable,
        crossAxis: Int,
        horizontal: Boolean,
    ): Int = when {
        horizontal && intrinsicSize == IntrinsicSize.Min -> measurable.minIntrinsicWidth(crossAxis)
        horizontal -> measurable.maxIntrinsicWidth(crossAxis)
        intrinsicSize == IntrinsicSize.Min -> measurable.minIntrinsicHeight(crossAxis)
        else -> measurable.maxIntrinsicHeight(crossAxis)
    }

    // The node reports the resolved intrinsic to its own parent too, so a chain of them agrees.
    override fun Density.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        if (horizontal) resolve(measurable, height, true) else measurable.minIntrinsicWidth(height)

    override fun Density.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        if (horizontal) resolve(measurable, height, true) else measurable.maxIntrinsicWidth(height)

    override fun Density.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        if (horizontal) measurable.minIntrinsicHeight(width) else resolve(measurable, width, false)

    override fun Density.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        if (horizontal) measurable.maxIntrinsicHeight(width) else resolve(measurable, width, false)

    override fun toString(): String =
        "${if (horizontal) "width" else "height"}(IntrinsicSize.$intrinsicSize)"
}
