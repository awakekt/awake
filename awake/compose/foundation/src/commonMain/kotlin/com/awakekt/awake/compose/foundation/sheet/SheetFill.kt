/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.sheet

import com.awakekt.awake.asset.sprite.SpriteSheet
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.FilterQuality

/**
 * An [ImageFill] of one cell, [cellWidth] by [cellHeight] pixels, of a regular grid on [image]:
 * column [column] and row [row], counted from the grid's top left at ([originX], [originY]).
 *
 * The cell is cut by [slice] into a nine-slice, or with [tiled] its edges and centre repeat at the
 * art's own size instead of stretching. [tint], [filterQuality] and [pixelsPerDp] are those of
 * [ImageFill]. A cell outside [image] fails as [ImageFill] does.
 */
@Suppress("LongParameterList")
fun gridCellFill(
    image: ImageBitmap,
    cellWidth: Int,
    cellHeight: Int,
    column: Int,
    row: Int,
    originX: Int = 0,
    originY: Int = 0,
    slice: SliceInsets = SliceInsets.None,
    tiled: Boolean = false,
    tint: Color = Color.White,
    filterQuality: FilterQuality = FilterQuality.Low,
    pixelsPerDp: Float = 1f,
): ImageFill {
    require(cellWidth > 0 && cellHeight > 0) { "A cell must be at least 1 x 1 pixel." }
    require(column >= 0 && row >= 0) { "Cell (, ) must not be negative." }
    return ImageFill(
        image = image,
        srcX = originX + column * cellWidth,
        srcY = originY + row * cellHeight,
        srcWidth = cellWidth,
        srcHeight = cellHeight,
        sliceLeft = slice.left,
        sliceTop = slice.top,
        sliceRight = slice.right,
        sliceBottom = slice.bottom,
        repeatEdges = tiled,
        repeatCenter = tiled,
        tint = tint,
        filterQuality = filterQuality,
        pixelsPerDp = pixelsPerDp,
    )
}

/**
 * An [ImageFill] of cell [frame] of this sheet, counted in reading order from the top left, over
 * the decoded [image], whose size must be this sheet's. See [gridCellFill] for [slice], [tiled]
 * and the rest.
 */
@Suppress("LongParameterList")
fun SpriteSheet.cellFill(
    image: ImageBitmap,
    frame: Int,
    slice: SliceInsets = SliceInsets.None,
    tiled: Boolean = false,
    tint: Color = Color.White,
    filterQuality: FilterQuality = FilterQuality.Low,
    pixelsPerDp: Float = 1f,
): ImageFill {
    requireImageSize(image.width, image.height)
    require(frame in 0 until columns * rows) { "Cell  is outside the  x  sheet." }
    return gridCellFill(
        image, cellWidth, cellHeight, frame % columns, frame / columns,
        slice = slice, tiled = tiled, tint = tint, filterQuality = filterQuality, pixelsPerDp = pixelsPerDp,
    )
}

/**
 * An [ImageFill] of frame [offset] of the clip named [clipId], the named region of this sheet:
 * [offset] 0 is its first cell. See [cellFill].
 */
@Suppress("LongParameterList")
fun SpriteSheet.clipFill(
    image: ImageBitmap,
    clipId: String,
    offset: Int = 0,
    slice: SliceInsets = SliceInsets.None,
    tiled: Boolean = false,
    tint: Color = Color.White,
    filterQuality: FilterQuality = FilterQuality.Low,
    pixelsPerDp: Float = 1f,
): ImageFill {
    val clip = requireNotNull(clips[clipId]) { "No clip \"$clipId\"; the sheet has ${clips.keys}." }
    require(offset in 0 until clip.frameCount) { "Frame $offset is outside clip \"$clipId\" of ${clip.frameCount} frames." }
    return cellFill(image, clip.firstFrame + offset, slice, tiled, tint, filterQuality, pixelsPerDp)
}
