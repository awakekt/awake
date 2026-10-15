/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable

/**
 * An image a [CanvasElement] draws: the pixels of the project file [path] from ([regionX],
 * [regionY]), [regionWidth] by [regionHeight], cut into nine by the insets [sliceLeft], [sliceTop],
 * [sliceRight] and [sliceBottom], all in the image's pixels. A region size of 0 reaches the image's
 * right or bottom edge.
 *
 * The corners keep their size, one dp per image pixel. The edges stretch along their length, or
 * repeat at that size when [repeatEdges]; the centre stretches, or repeats both ways when
 * [repeatCenter]. With no slices the whole region stretches, and slicing only the left and right
 * makes a three-part strip. Colour is multiplied by [tint], `#RRGGBB` or `#RRGGBBAA`, and a
 * [pixelated] image keeps its pixels sharp when it is scaled.
 */
@Serializable
data class CanvasImage(
    val path: String,
    @PropertyRange(min = 0.0) val regionX: Int = 0,
    @PropertyRange(min = 0.0) val regionY: Int = 0,
    @PropertyRange(min = 0.0) val regionWidth: Int = 0,
    @PropertyRange(min = 0.0) val regionHeight: Int = 0,
    @PropertyRange(min = 0.0) val sliceLeft: Int = 0,
    @PropertyRange(min = 0.0) val sliceTop: Int = 0,
    @PropertyRange(min = 0.0) val sliceRight: Int = 0,
    @PropertyRange(min = 0.0) val sliceBottom: Int = 0,
    val repeatEdges: Boolean = false,
    val repeatCenter: Boolean = false,
    val tint: String = "#FFFFFF",
    val pixelated: Boolean = false,
) {
    /** What is wrong with this image, as messages naming the field; empty when nothing is. */
    internal fun problems(): List<String> = buildList {
        if (path.isBlank()) add("path must name an image")
        if (minOf(regionX, regionY, regionWidth, regionHeight) < 0) add("region must not be negative")
        if (minOf(sliceLeft, sliceTop, sliceRight, sliceBottom) < 0) add("slices must not be negative")
        if (regionWidth > 0 && sliceLeft + sliceRight > regionWidth) add("left and right slices must fit in regionWidth")
        if (regionHeight > 0 && sliceTop + sliceBottom > regionHeight) add("top and bottom slices must fit in regionHeight")
        if (!isCanvasColor(tint)) add("tint \"$tint\" must be #RRGGBB or #RRGGBBAA")
    }

    /**
     * How [images]' picture for [path] fills an element, or null when none was loaded or the region
     * does not fit in it: the element then draws without it, as a bad colour falls back.
     */
    internal fun fill(images: Map<String, ImageBitmap>): ImageFill? {
        val image = images[path] ?: return null
        val width = if (regionWidth > 0) regionWidth else image.width - regionX
        val height = if (regionHeight > 0) regionHeight else image.height - regionY
        return if (fitsIn(image, width, height)) {
            ImageFill(
                image, regionX, regionY, width, height,
                sliceLeft, sliceTop, sliceRight, sliceBottom,
                repeatEdges, repeatCenter,
                tint = if (isCanvasColor(tint)) Color.fromHex(tint) else Color.White,
                filterQuality = if (pixelated) FilterQuality.None else FilterQuality.Low,
            )
        } else {
            null
        }
    }

    /** Whether the [width] by [height] region lies inside [image] and holds its slices. */
    private fun fitsIn(image: ImageBitmap, width: Int, height: Int): Boolean {
        val placed = minOf(regionX, regionY) >= 0 && minOf(width, height) > 0
        val inside = regionX + width <= image.width && regionY + height <= image.height
        val sliced = minOf(sliceLeft, sliceTop, sliceRight, sliceBottom) >= 0 &&
            sliceLeft + sliceRight <= width && sliceTop + sliceBottom <= height
        return placed && inside && sliced
    }
}
