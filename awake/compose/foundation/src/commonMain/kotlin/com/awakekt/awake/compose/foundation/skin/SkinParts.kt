/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.skin

import com.awakekt.awake.compose.foundation.sheet.SheetAxis
import com.awakekt.awake.compose.foundation.sheet.SheetState
import com.awakekt.awake.compose.foundation.sheet.SliceInsets
import com.awakekt.awake.compose.foundation.sheet.StateSheet
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.ImageFill

/** A rectangle of the sheet image in source pixels: [width] by [height] from ([x], [y]). */
data class SheetRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    init {
        require(x >= 0 && y >= 0 && width > 0 && height > 0) { "A sheet rectangle needs a non-negative origin and a positive size." }
    }
}

/** How a part is cut from the sheet, before the sheet has decoded. Turns into its [R] given the image. */
internal sealed interface PartSpec<R : Any> {
    fun resolve(image: ImageBitmap, pixelsPerDp: Float): R
}

internal class FrameSpec(
    val rect: SheetRect,
    val slice: SliceInsets,
    val repeatEdges: Boolean,
    val repeatCenter: Boolean,
    val hollow: Boolean,
) : PartSpec<ImageFill> {
    override fun resolve(image: ImageBitmap, pixelsPerDp: Float): ImageFill = ImageFill(
        image = image,
        srcX = rect.x,
        srcY = rect.y,
        srcWidth = rect.width,
        srcHeight = rect.height,
        sliceLeft = slice.left,
        sliceTop = slice.top,
        sliceRight = slice.right,
        sliceBottom = slice.bottom,
        repeatEdges = repeatEdges,
        repeatCenter = repeatCenter,
        pixelsPerDp = pixelsPerDp,
        hollow = hollow,
    )
}

internal class StatesSpec(
    val rect: SheetRect,
    val states: List<SheetState>,
    val axis: SheetAxis,
    val slice: SliceInsets,
    val tiled: Boolean,
) : PartSpec<StateSheet> {
    init {
        val span = if (axis == SheetAxis.Row) rect.width else rect.height
        require(states.isNotEmpty() && span % states.size == 0) {
            "A strip of $span pixels cannot hold ${states.size} equal frames."
        }
    }

    override fun resolve(image: ImageBitmap, pixelsPerDp: Float): StateSheet {
        val row = axis == SheetAxis.Row
        return StateSheet.fromGrid(
            image,
            cellWidth = if (row) rect.width / states.size else rect.width,
            cellHeight = if (row) rect.height else rect.height / states.size,
            states = states, axis = axis, originX = rect.x, originY = rect.y,
            slice = slice, tiled = tiled, pixelsPerDp = pixelsPerDp,
        )
    }
}
