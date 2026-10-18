/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

/**
 * A rectangle of a picture and the insets that cut it into nine, in the picture's pixels: one cell
 * of a sheet, and the frame drawn from it, as [InspectorFieldScope.imageSlices] edits them.
 *
 * The rectangle runs from ([x], [y]), [width] by [height]; a size of 0 reaches the picture's right or
 * bottom edge. [left], [top], [right] and [bottom] are in from its edges: the corners they cut keep
 * their size, and the edges and the centre stretch or tile.
 *
 * @property x The rectangle's left edge in the picture.
 * @property y The rectangle's top edge in the picture.
 * @property width The rectangle's width; 0 reaches the picture's right edge.
 * @property height The rectangle's height; 0 reaches the picture's bottom edge.
 * @property left The left slice, in from the rectangle's left edge.
 * @property top The top slice, in from the rectangle's top edge.
 * @property right The right slice, in from the rectangle's right edge.
 * @property bottom The bottom slice, in from the rectangle's bottom edge.
 */
data class ImageSlices(
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    /**
     * These slices kept inside a picture [pictureWidth] by [pictureHeight]: the rectangle on it, and
     * each pair of insets no wider than the rectangle, the near one given way first. What a host
     * writes after a drag, so a guide dragged past the edge stops there.
     */
    fun keptInside(pictureWidth: Int, pictureHeight: Int): ImageSlices {
        val left = x.coerceIn(0, (pictureWidth - 1).coerceAtLeast(0))
        val top = y.coerceIn(0, (pictureHeight - 1).coerceAtLeast(0))
        val across = (if (width > 0) width else pictureWidth - left).coerceIn(1, (pictureWidth - left).coerceAtLeast(1))
        val down = (if (height > 0) height else pictureHeight - top).coerceIn(1, (pictureHeight - top).coerceAtLeast(1))
        val far = right.coerceIn(0, across)
        val bottomInset = bottom.coerceIn(0, down)
        return ImageSlices(
            x = left,
            y = top,
            width = if (width > 0) across else 0,
            height = if (height > 0) down else 0,
            left = this.left.coerceIn(0, across - far),
            top = this.top.coerceIn(0, down - bottomInset),
            right = far,
            bottom = bottomInset,
        )
    }
}
