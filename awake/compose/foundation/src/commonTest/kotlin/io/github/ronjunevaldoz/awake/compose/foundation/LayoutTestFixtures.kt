// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints

internal fun child(width: Int, height: Int, modifier: Modifier = Modifier): LayoutNode =
    LayoutNode(FixedSize(width, height)).also { it.modifier = modifier }

/** Measure, place at the origin, then resolve tree-space positions -- one real layout pass. */
internal fun LayoutNode.layoutIn(constraints: Constraints): LayoutNode {
    measure(constraints)
    placeAt(0, 0)
    resolveAbsolutePositions()
    return this
}
