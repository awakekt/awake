/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.layout

import com.awakekt.awake.compose.runtime.PhaseReads
import com.awakekt.awake.compose.runtime.recording
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints

/**
 * One full layout pass: measure, place, resolve tree-space positions, then do the same for layers.
 *
 * Layers need a second pass because a measure policy only ever sees [LayoutNode.children] -- that
 * is the point of the separate slot, and it means nothing would ever size a popup otherwise. They
 * are measured against the **viewport**, not the enclosing node's constraints, so a dialog inside a
 * 20px row is not 20px wide.
 */
fun LayoutNode.layoutTree(viewport: Constraints) {
    layoutTree(viewport, measure = true, reads = null)
}

/**
 * A layout pass, optionally without measuring (#1512, the placement-only path).
 *
 * With [measure] off every node keeps the size and the placement block its last measure gave it, and
 * only placement and position resolution run again: a value read while placing, such as a
 * `Modifier.offset { }` lambda or a world anchor's provider, moves its node and nothing is measured.
 * That is only correct when nothing a measure read has changed since, which is the caller's to know.
 *
 * With [reads], what measuring reads is recorded there, and placement is not; a caller asks
 * [PhaseReads.isInvalid] whether the next pass must measure.
 */
internal fun LayoutNode.layoutTree(viewport: Constraints, measure: Boolean, reads: PhaseReads?) {
    LayoutPasses.current++
    if (measure) {
        reads?.beginPass()
        if (reads == null) measure(viewport) else reads.recording { measure(viewport) }
    }
    placeAt(0, 0)
    resolveAbsolutePositions(0, 0, includeLayers = false)
    layoutLayers(viewport.loosened(), measure, reads)
}

/**
 * A layer sizes to its own content within the viewport, never to the viewport itself -- a tooltip
 * is tooltip-sized. Only the maximum is inherited.
 */
private fun Constraints.loosened(): Constraints =
    Constraints.of(0, maxWidth, 0, maxHeight)

private fun LayoutNode.layoutLayers(viewport: Constraints, measure: Boolean, reads: PhaseReads?) {
    for (i in 0 until layers.size) {
        val layer = layers[i]
        if (measure) {
            if (reads == null) layer.measure(viewport) else reads.recording { layer.measure(viewport) }
        }
        val position = layer.layerPositionProvider?.position(
            parentX = absoluteX,
            parentY = absoluteY,
            layerWidth = layer.width,
            layerHeight = layer.height,
            viewportWidth = viewport.maxWidth,
            viewportHeight = viewport.maxHeight,
        ) ?: LayerPosition(0, 0)
        layer.placeAt(position.x, position.y)
        layer.resolveAbsolutePositions(absoluteX, absoluteY, includeLayers = false)
        layer.layoutLayers(viewport, measure, reads)
    }
    for (i in 0 until children.size) {
        val child = children[i]
        if (placesChild(child)) child.layoutLayers(viewport, measure, reads)
    }
}

/**
 * Counts layout passes, so a node can tell whether its parent placed it in the latest one: see
 * [LayoutNode.placesChild]. Only ever compared between a node and its parent, which one pass stamps
 * alike, so hosts that lay out in turn do not disturb each other. Frame thread only.
 */
internal object LayoutPasses {
    var current: Int = 0
}
