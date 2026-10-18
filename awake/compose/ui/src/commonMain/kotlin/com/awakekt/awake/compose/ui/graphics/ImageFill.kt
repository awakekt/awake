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
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.round

/**
 * How [image] fills a rectangle of any size: a frame, a panel, a gauge.
 *
 * The pixels from ([srcX], [srcY]), [srcWidth] by [srcHeight], are cut into nine by the insets
 * [sliceLeft], [sliceTop], [sliceRight] and [sliceBottom], in source pixels. The corners keep their
 * size, one dp per [pixelsPerDp] source pixels: 1, the default, for art drawn at one pixel per dp,
 * and 2 for art drawn at twice that, so it stays as large on screen and sharper. The edges stretch
 * along their length, or repeat at that size when [repeatEdges]; the centre stretches, repeats both
 * ways when [repeatCenter], or is left out when [hollow], so what is behind a frame shows through.
 * With every inset 0 the whole region is the centre, and with top and bottom insets of 0 it is a
 * three-part strip. Colour is multiplied by [tint], and [filterQuality] `None` keeps pixel art crisp.
 *
 * [drawImageFillBar] draws it as a bar's fill, its end caps whole at every value.
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
    val pixelsPerDp: Float = 1f,
    val hollow: Boolean = false,
) {
    init {
        require(srcX >= 0 && srcY >= 0 && srcWidth > 0 && srcHeight > 0 && srcX + srcWidth <= image.width && srcY + srcHeight <= image.height) {
            "The region ($srcX, $srcY) $srcWidth x $srcHeight must lie inside the ${image.width} x ${image.height} image."
        }
        require(sliceLeft >= 0 && sliceTop >= 0 && sliceRight >= 0 && sliceBottom >= 0) { "Slice insets must not be negative." }
        require(sliceLeft + sliceRight <= srcWidth && sliceTop + sliceBottom <= srcHeight) {
            "Slice insets must fit in the $srcWidth x $srcHeight region."
        }
        require(pixelsPerDp > 0f && pixelsPerDp.isFinite()) { "pixelsPerDp must be above 0, but is $pixelsPerDp." }
    }
}

/**
 * Fills [x], [y], [width], [height] of this node, the whole node by default, with [fill]. Corners
 * too big for the rectangle shrink together until they meet.
 *
 * Every piece's edges land on whole pixels, so at a scale that is not a whole number, such as 1.5,
 * pixel art stays crisp and no seam opens between pieces.
 */
fun DrawScope.drawImageFill(
    fill: ImageFill,
    x: Float = 0f,
    y: Float = 0f,
    width: Float = this.width.toFloat(),
    height: Float = this.height.toFloat(),
) {
    if (width <= 0f || height <= 0f) return
    val unit = sourcePixel(fill)
    val scaleX = sliceScale(fill.sliceLeft + fill.sliceRight, width, unit)
    val scaleY = sliceScale(fill.sliceTop + fill.sliceBottom, height, unit)
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
            if (fill.hollow && middleRow && middleColumn) continue
            drawTiles(
                fill,
                snappedCut(column, x, width, left, right),
                snappedCut(row, y, height, top, bottom),
                snappedCut(column + 1, x, width, left, right),
                snappedCut(row + 1, y, height, top, bottom),
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

/** Which way a bar fills: from the edge its name starts at, toward the other. */
enum class FillDirection {
    /** From the left edge rightward. */
    LeftToRight,

    /** From the right edge leftward. */
    RightToLeft,

    /** From the top edge down. */
    TopToBottom,

    /** From the bottom edge up. */
    BottomToTop,
    ;

    /** Whether the bar runs down the screen rather than across it. */
    val vertical: Boolean get() = this == TopToBottom || this == BottomToTop
}

/**
 * Fills [value], from 0 to 1, of the bar [x], [y], [width], [height] with [fill], from the edge
 * [direction] names, as a health or resource bar's fill.
 *
 * The bar's two end caps, [ImageFill.sliceLeft] and [ImageFill.sliceRight] across or
 * [ImageFill.sliceTop] and [ImageFill.sliceBottom] down, stay whole at every value above 0, and only
 * the middle grows: from nothing just above 0 to all of it at 1. The filled length is the caps plus
 * [value] of the rest, on a whole pixel. A bar too short for its caps shrinks them as [drawImageFill]
 * does, and fills [value] of it. A value of 0 draws nothing.
 */
@Suppress("LongParameterList")
fun DrawScope.drawImageFillBar(
    fill: ImageFill,
    value: Float,
    direction: FillDirection = FillDirection.LeftToRight,
    x: Float = 0f,
    y: Float = 0f,
    width: Float = this.width.toFloat(),
    height: Float = this.height.toFloat(),
) {
    val amount = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
    if (amount <= 0f || width <= 0f || height <= 0f) return
    val span = if (direction.vertical) height else width
    val insets = if (direction.vertical) fill.sliceTop + fill.sliceBottom else fill.sliceLeft + fill.sliceRight
    val caps = insets * sourcePixel(fill)
    val length = round(if (caps >= span) amount * span else caps + amount * (span - caps)).coerceAtMost(span)
    if (length <= 0f) return
    when (direction) {
        FillDirection.LeftToRight -> drawImageFill(fill, x, y, length, height)
        FillDirection.RightToLeft -> drawImageFill(fill, x + width - length, y, length, height)
        FillDirection.TopToBottom -> drawImageFill(fill, x, y, width, length)
        FillDirection.BottomToTop -> drawImageFill(fill, x, y + height - length, width, length)
    }
}

/** Screen pixels per source pixel of [fill]: the density, over the art's own pixels per dp. */
private fun DrawScope.sourcePixel(fill: ImageFill): Float = density / fill.pixelsPerDp

/** Pixels per source pixel for an axis' two slices: [unit], or less so that both fit in [span]. */
private fun sliceScale(insets: Int, span: Float, unit: Float): Float =
    if (insets * unit > span) span / insets else unit

/** The [line]th of the four lines, 0 to 3, that cut [start]..[start] + [size] into near slice, middle and far slice. */
private fun cut(line: Int, start: Float, size: Float, near: Float, far: Float): Float = when (line) {
    0 -> start
    1 -> start + near
    2 -> start + size - far
    else -> start + size
}

/** [cut] on the screen: on the nearest whole pixel. */
private fun snappedCut(line: Int, start: Float, size: Float, near: Float, far: Float): Float = round(cut(line, start, size, near, far))

/**
 * Draws the source pixels [u0]..[u1], [v0]..[v1] over [x0]..[x1], [y0]..[y1]: stretched, or
 * repeated at the fill's size per source pixel along an axis that repeats. Loose floats, as `drawGlyph`
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
    val unit = sourcePixel(fill)
    val tileWidth = if (repeatX) srcWidth * unit else width
    val tileHeight = if (repeatY) srcHeight * unit else height
    if (minOf(width, height, tileWidth, tileHeight) <= 0f) return
    val imageWidth = fill.image.width.toFloat()
    val imageHeight = fill.image.height.toFloat()
    for (row in 0 until tiles(height, tileHeight)) {
        val top = y0 + row * tileHeight
        val bottom = min(top + tileHeight, y1)
        for (column in 0 until tiles(width, tileWidth)) {
            val left = x0 + column * tileWidth
            val right = min(left + tileWidth, x1)
            // A tile cut short by the span's end shows only that much of its source.
            val region = TextureRegion(
                u0 / imageWidth,
                v0 / imageHeight,
                (u0 + srcWidth * (right - left) / tileWidth) / imageWidth,
                (v0 + srcHeight * (bottom - top) / tileHeight) / imageHeight,
            )
            val x = round(left)
            val y = round(top)
            val w = round(right) - x
            val h = round(bottom) - y
            if (w > 0f && h > 0f) drawTexture(fill.image, x, y, w, h, region, fill.tint, fill.filterQuality)
        }
    }
}

/** How many tiles of [tile] cover [span], the last one cut short; rounding left over is not a tile. */
private fun tiles(span: Float, tile: Float): Int = ceil((span - MIN_TILE) / tile).toInt()

/** Less than this of a span is rounding left over from adding tiles, not a tile. */
private const val MIN_TILE = 0.01f
