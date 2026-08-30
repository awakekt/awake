/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.layout

import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.unit.Constraints

/**
 * One full layout pass: measure, place, resolve tree-space positions, then do the same for layers.
 *
 * Layers need a second pass because a measure policy only ever sees [LayoutNode.children] -- that
 * is the point of the separate slot, and it means nothing would ever size a popup otherwise. They
 * are measured against the **viewport**, not the enclosing node's constraints, so a dialog inside a
 * 20px row is not 20px wide.
 */
fun LayoutNode.layoutTree(viewport: Constraints) {
    measure(viewport)
    placeAt(0, 0)
    resolveAbsolutePositions()
    layoutLayers(viewport.loosened())
    // Layers were placed after positions resolved, so redo the walk to pick them up.
    resolveAbsolutePositions()
}

/**
 * A layer sizes to its own content within the viewport, never to the viewport itself -- a tooltip
 * is tooltip-sized. Only the maximum is inherited.
 */
private fun Constraints.loosened(): Constraints =
    Constraints.of(0, maxWidth, 0, maxHeight)

private fun LayoutNode.layoutLayers(viewport: Constraints) {
    for (i in layers.indices) {
        val layer = layers[i]
        layer.measure(viewport)
        val position = layer.layerPositionProvider?.position(
            parentX = absoluteX,
            parentY = absoluteY,
            layerWidth = layer.width,
            layerHeight = layer.height,
            viewportWidth = viewport.maxWidth,
            viewportHeight = viewport.maxHeight,
        ) ?: LayerPosition(0, 0)
        layer.placeAt(position.x, position.y)
        layer.layoutLayers(viewport)
    }
    for (i in children.indices) {
        children[i].layoutLayers(viewport)
    }
}
