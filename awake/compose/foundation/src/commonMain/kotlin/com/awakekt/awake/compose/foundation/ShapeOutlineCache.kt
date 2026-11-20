/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.ui.graphics.RectangleShape
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.graphics.Shape
import com.awakekt.awake.compose.ui.graphics.ShapeOutline
import com.awakekt.awake.compose.ui.unit.LayoutDirection
import com.awakekt.awake.core.math2d.Size2D

/**
 * One draw node's last resolved outline, kept while its shape, size, density and direction stay the
 * same. Resolving a shape on every paint built its bounds and its outline per node per frame (#1742).
 *
 * Only shapes known to be immutable are kept: [RectangleShape] and [RoundedCornerShape]. Any other
 * [Shape] may read state of its own, so it is resolved every time, as before. A rounded shape is
 * compared by its corners, so one written inline, a new instance at every composition, still hits.
 */
internal class ShapeOutlineCache {
    private var shape: Shape? = null
    private var size = 0L
    private var density = 0f
    private var direction = LayoutDirection.Ltr
    private var outline: ShapeOutline? = null

    fun outline(shape: Shape, width: Int, height: Int, density: Float, direction: LayoutDirection): ShapeOutline {
        val size = (width.toLong() shl Int.SIZE_BITS) or (height.toLong() and INT_MASK)
        val kept = outline
        if (kept != null && keeps(shape, size, density, direction)) return kept
        val resolved = shape.createOutline(Size2D(width.toFloat(), height.toFloat()), density, direction)
        if (shape === RectangleShape || shape is RoundedCornerShape) {
            this.shape = shape
            this.size = size
            this.density = density
            this.direction = direction
            outline = resolved
        } else {
            outline = null
        }
        return resolved
    }

    private fun keeps(shape: Shape, size: Long, density: Float, direction: LayoutDirection): Boolean =
        sameShape(shape, this.shape) && size == this.size && sameMetrics(density, direction)

    private fun sameShape(shape: Shape, kept: Shape?): Boolean =
        shape === kept || (shape is RoundedCornerShape && kept is RoundedCornerShape && sameCorners(shape, kept))

    private fun sameCorners(a: RoundedCornerShape, b: RoundedCornerShape): Boolean =
        a.topStart == b.topStart && a.topEnd == b.topEnd && a.bottomEnd == b.bottomEnd && a.bottomStart == b.bottomStart

    private fun sameMetrics(density: Float, direction: LayoutDirection): Boolean =
        density == this.density && direction == this.direction

    private companion object {
        const val INT_MASK = 0xFFFFFFFFL
    }
}
