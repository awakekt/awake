/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.layout.IntrinsicMeasurable
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.node.LayoutModifierNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Density

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
    this then IntrinsicSizeElement(intrinsicSize, horizontal = true)

fun Modifier.height(intrinsicSize: IntrinsicSize): Modifier =
    this then IntrinsicSizeElement(intrinsicSize, horizontal = false)

private class IntrinsicSizeElement(
    private val intrinsicSize: IntrinsicSize,
    private val horizontal: Boolean,
) : ModifierNodeElement<IntrinsicSizeNode>() {
    override fun create(): IntrinsicSizeNode = IntrinsicSizeNode()

    override fun update(node: IntrinsicSizeNode) {
        node.intrinsicSize = intrinsicSize
        node.horizontal = horizontal
    }

    override fun toString(): String =
        "${if (horizontal) "width" else "height"}(IntrinsicSize.$intrinsicSize)"
}

private class IntrinsicSizeNode :
    Modifier.Node(),
    LayoutModifierNode {
    lateinit var intrinsicSize: IntrinsicSize
    var horizontal: Boolean = false

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
