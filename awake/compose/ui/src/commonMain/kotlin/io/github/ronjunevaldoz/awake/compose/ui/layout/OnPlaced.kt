// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.layout

import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.node.OnPlacedModifierNode

/**
 * Reports this node's tree-space bounds after each layout pass.
 *
 * Fires on every pass, not only on change: a caller that wants change-detection can compare, and a
 * modifier that silently skipped a call would be the harder bug to find.
 */
fun Modifier.onPlaced(onPlaced: (x: Int, y: Int, width: Int, height: Int) -> Unit): Modifier =
    this then OnPlacedNode(onPlaced)

private class OnPlacedNode(
    private val callback: (Int, Int, Int, Int) -> Unit,
) : OnPlacedModifierNode {
    override fun onPlaced(x: Int, y: Int, width: Int, height: Int) = callback(x, y, width, height)

    override fun toString(): String = "onPlaced()"
}
