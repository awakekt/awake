/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints

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
) : com.awakekt.awake.compose.ui.layout.MeasurePolicy {
    override fun com.awakekt.awake.compose.ui.layout.MeasureScope.measure(
        measurables: List<com.awakekt.awake.compose.ui.layout.Measurable>,
        constraints: Constraints,
    ): com.awakekt.awake.compose.ui.layout.MeasureResult = layout(
        constraints.constrainWidth(width),
        constraints.constrainHeight(height),
        mapOf(
            com.awakekt.awake.compose.ui.layout.FirstBaseline to firstBaseline,
            com.awakekt.awake.compose.ui.layout.LastBaseline to firstBaseline,
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
