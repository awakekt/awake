/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.layout.LayerPosition
import io.github.awakelab.awake.compose.ui.layout.LayerPositionProvider
import io.github.awakelab.awake.compose.ui.layout.onPlaced

/**
 * Where a trigger ended up, so the popup it opens can be placed against it.
 *
 * Written during placement and read by a [LayerPositionProvider] in the same frame -- layers are
 * measured and placed after the content tree, so by the time the provider runs these are the
 * trigger's resolved bounds rather than last frame's.
 */
internal class PopupAnchor {
    var x: Int = 0
    var y: Int = 0
    var width: Int = 0
    var height: Int = 0
}

/** Records the node's placed bounds into [anchor]. */
internal fun Modifier.popupAnchor(anchor: PopupAnchor): Modifier = onPlaced { x, y, width, height ->
    anchor.x = x
    anchor.y = y
    anchor.width = width
    anchor.height = height
}

/**
 * Places a popup below its trigger, flipping above it when there is no room and clamping to the
 * viewport horizontally.
 *
 * Shared rather than copied per component: the dropdown, select and combobox each had their own
 * anchor class and their own provider computing the same thing, and a popover would have been the
 * fourth. Flip-and-clamp is the kind of rule that is wrong in one copy and right in the others.
 */
internal class AnchoredBelowPositionProvider(
    private val anchor: PopupAnchor,
    private val gap: Int,
) : LayerPositionProvider {
    override fun position(
        parentX: Int,
        parentY: Int,
        layerWidth: Int,
        layerHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ): LayerPosition {
        val x = anchor.x.coerceIn(0, (viewportWidth - layerWidth).coerceAtLeast(0))
        val below = anchor.y + anchor.height + gap
        val y = if (below + layerHeight <= viewportHeight) {
            below
        } else {
            (anchor.y - gap - layerHeight).coerceAtLeast(0)
        }
        return LayerPosition(x - parentX, y - parentY)
    }
}
