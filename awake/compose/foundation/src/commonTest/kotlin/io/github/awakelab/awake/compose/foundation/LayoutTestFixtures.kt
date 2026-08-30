/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.unit.Constraints

internal fun child(width: Int, height: Int, modifier: Modifier = Modifier): LayoutNode =
    LayoutNode(FixedSize(width, height)).also { it.modifier = modifier }

internal fun childWithBaseline(
    width: Int,
    height: Int,
    firstBaseline: Int,
    modifier: Modifier = Modifier,
): LayoutNode = LayoutNode(FixedSizeWithBaseline(width, height, firstBaseline)).also { it.modifier = modifier }

internal class FixedSizeWithBaseline(
    private val width: Int,
    private val height: Int,
    private val firstBaseline: Int,
) : io.github.awakelab.awake.compose.ui.layout.MeasurePolicy {
    override fun io.github.awakelab.awake.compose.ui.layout.MeasureScope.measure(
        measurables: List<io.github.awakelab.awake.compose.ui.layout.Measurable>,
        constraints: Constraints,
    ): io.github.awakelab.awake.compose.ui.layout.MeasureResult = layout(
        constraints.constrainWidth(width),
        constraints.constrainHeight(height),
        mapOf(
            io.github.awakelab.awake.compose.ui.layout.FirstBaseline to firstBaseline,
            io.github.awakelab.awake.compose.ui.layout.LastBaseline to firstBaseline,
        ),
    ) {}
}

/** Measure, place at the origin, then resolve tree-space positions -- one real layout pass. */
internal fun LayoutNode.layoutIn(constraints: Constraints): LayoutNode {
    measure(constraints)
    placeAt(0, 0)
    resolveAbsolutePositions()
    return this
}
