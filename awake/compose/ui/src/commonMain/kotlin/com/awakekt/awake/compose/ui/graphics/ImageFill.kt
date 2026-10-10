/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.core.graphics2d.TextureRegion
import kotlin.math.min

/**
 * How [image] fills a rectangle of any size: a frame, a panel, a gauge.
 *
 * The pixels from ([srcX], [srcY]), [srcWidth] by [srcHeight], are cut into nine by the insets
 * [sliceLeft], [sliceTop], [sliceRight] and [sliceBottom], in source pixels. The corners keep their
 * size, one dp per source pixel. The edges
 * stretch along their length, or repeat at that size when [repeatEdges]; the centre stretches, or
 * repeats both ways when [repeatCenter]. With every inset 0 the whole region is the centre, and
 * with top and bottom insets of 0 it is a three-part strip. Colour is multiplied by [tint], and
 * [filterQuality] `None` keeps pixel art crisp.
 */
data class ImageFill(
    val image: ImageBitmap,
    val srcX: Int = 0,
    val srcY: Int = 0,
    val srcWidth: Int = image.width - srcX,
    val srcHeight: Int = image.height - srcY,
    val sliceLeft: Int = 0,
    val sliceTop: Int = 0,
    val sliceRight: Int = 0,
    val sliceBottom: Int = 0,
    val repeatEdges: Boolean = false,
    val repeatCenter: Boolean = false,
    val tint: Color = Color.White,
    val filterQuality: FilterQuality = FilterQuality.Low,
) {
    init {
        require(srcX >= 0 && srcY >= 0 && srcWidth > 0 && srcHeight > 0 && srcX + srcWidth <= image.width && srcY + srcHeight <= image.height) {
            "The region ($srcX, $srcY) $srcWidth x $srcHeight must lie inside the ${image.width} x ${image.height} image."
        }
        require(sliceLeft >= 0 && sliceTop >= 0 && sliceRight >= 0 && sliceBottom >= 0) { "Slice insets must not be negative." }
        require(sliceLeft + sliceRight <= srcWidth && sliceTop + sliceBottom <= srcHeight) {
            "Slice insets must fit in the $srcWidth x $srcHeight region."
        }
    }
}

/**
 * Fills [x], [y], [width], [height] of this node, the whole node by default, with [fill]. Corners
 * too big for the rectangle shrink together until they meet.
 */
fun DrawScope.drawImageFill(
    fill: ImageFill,
    x: Float = 0f,
    y: Float = 0f,
    width: Float = this.width.toFloat(),
    height: Float = this.height.toFloat(),
) {
    if (width <= 0f || height <= 0f) return
    val scaleX = sliceScale(fill.sliceLeft + fill.sliceRight, width)
    val scaleY = sliceScale(fill.sliceTop + fill.sliceBottom, height)
    val left = fill.sliceLeft * scaleX
    val right = fill.sliceRight * scaleX
    val top = fill.sliceTop * scaleY
    val bottom = fill.sliceBottom * scaleY
    val srcX = fill.srcX.toFloat()
    val srcY = fill.srcY.toFloat()
    val srcWidth = fill.srcWidth.toFloat()
    val srcHeight = fill.srcHeight.toFloat()
    for (row in 0..2) {
        for (column in 0..2) {
            val middleRow = row == 1
            val middleColumn = column == 1
            drawTiles(
                fill,
                cut(column, x, width, left, right),
                cut(row, y, height, top, bottom),
                cut(column + 1, x, width, left, right),
                cut(row + 1, y, height, top, bottom),
                cut(column, srcX, srcWidth, fill.sliceLeft.toFloat(), fill.sliceRight.toFloat()),
                cut(row, srcY, srcHeight, fill.sliceTop.toFloat(), fill.sliceBottom.toFloat()),
                cut(column + 1, srcX, srcWidth, fill.sliceLeft.toFloat(), fill.sliceRight.toFloat()),
                cut(row + 1, srcY, srcHeight, fill.sliceTop.toFloat(), fill.sliceBottom.toFloat()),
                repeatX = middleColumn && if (middleRow) fill.repeatCenter else fill.repeatEdges,
                repeatY = middleRow && if (middleColumn) fill.repeatCenter else fill.repeatEdges,
            )
        }
    }
}

/** Pixels per source pixel for an axis' two slices: one dp, or less so that both fit in [span]. */
private fun DrawScope.sliceScale(insets: Int, span: Float): Float =
    if (insets * density > span) span / insets else density

/** The [line]th of the four lines, 0 to 3, that cut [start]..[start] + [size] into near slice, middle and far slice. */
private fun cut(line: Int, start: Float, size: Float, near: Float, far: Float): Float = when (line) {
    0 -> start
    1 -> start + near
    2 -> start + size - far
    else -> start + size
}

/**
 * Draws the source pixels [u0]..[u1], [v0]..[v1] over [x0]..[x1], [y0]..[y1]: stretched, or
 * repeated at one dp per source pixel along an axis that repeats. Loose floats, as `drawGlyph`
 * takes them, so a fill allocates nothing beyond the regions it draws.
 */
@Suppress("LongParameterList")
private fun DrawScope.drawTiles(
    fill: ImageFill,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    u0: Float,
    v0: Float,
    u1: Float,
    v1: Float,
    repeatX: Boolean,
    repeatY: Boolean,
) {
    val width = x1 - x0
    val height = y1 - y0
    val srcWidth = u1 - u0
    val srcHeight = v1 - v0
    val tileWidth = if (repeatX) srcWidth * density else width
    val tileHeight = if (repeatY) srcHeight * density else height
    if (minOf(width, height, tileWidth, tileHeight) <= 0f) return
    val imageWidth = fill.image.width.toFloat()
    val imageHeight = fill.image.height.toFloat()
    var ty = 0f
    while (height - ty > MIN_TILE) {
        val h = min(tileHeight, height - ty)
        var tx = 0f
        while (width - tx > MIN_TILE) {
            val w = min(tileWidth, width - tx)
            // A tile cut short by the span's end shows only that much of its source.
            val region = TextureRegion(
                u0 / imageWidth,
                v0 / imageHeight,
                (u0 + srcWidth * w / tileWidth) / imageWidth,
                (v0 + srcHeight * h / tileHeight) / imageHeight,
            )
            drawTexture(fill.image, x0 + tx, y0 + ty, w, h, region, fill.tint, fill.filterQuality)
            tx += tileWidth
        }
        ty += tileHeight
    }
}

/** Less than this of a span is rounding left over from adding tiles, not a tile. */
private const val MIN_TILE = 0.01f
