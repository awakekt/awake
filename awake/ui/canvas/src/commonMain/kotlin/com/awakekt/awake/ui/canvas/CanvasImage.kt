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
 * The corners keep their size, one dp per [pixelsPerDp] image pixels: 1 for art drawn at one pixel
 * per dp, 2 for art drawn at twice that. The edges stretch along their length, or repeat at that
 * size when [repeatEdges]; the centre stretches, repeats both ways when [repeatCenter], or is left
 * out when [hollow], so a frame shows what is behind it. With no slices the whole region stretches,
 * and slicing only the left and right makes a three-part strip. Colour is multiplied by [tint],
 * `#RRGGBB` or `#RRGGBBAA`, and a [pixelated] image keeps its pixels sharp when it is scaled.
 *
 * A [lazy] image is read and decoded the first time an element drawing it shows, not when the scene
 * opens: for a picture only a window closed at entry uses, such as an icon atlas. Until it has
 * decoded, the element draws without it. A path some other element names without [lazy] still loads
 * with the scene.
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
    @PropertyRange(min = 0.0, exclusiveMin = true) val pixelsPerDp: Float = 1f,
    val hollow: Boolean = false,
    val lazy: Boolean = false,
) {
    /** [CanvasImage] as it was before [lazy]: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        path: String,
        regionX: Int = 0,
        regionY: Int = 0,
        regionWidth: Int = 0,
        regionHeight: Int = 0,
        sliceLeft: Int = 0,
        sliceTop: Int = 0,
        sliceRight: Int = 0,
        sliceBottom: Int = 0,
        repeatEdges: Boolean = false,
        repeatCenter: Boolean = false,
        tint: String = "#FFFFFF",
        pixelated: Boolean = false,
        pixelsPerDp: Float = 1f,
        hollow: Boolean = false,
    ) : this(
        path, regionX, regionY, regionWidth, regionHeight, sliceLeft, sliceTop, sliceRight, sliceBottom,
        repeatEdges, repeatCenter, tint, pixelated, pixelsPerDp, hollow, false,
    )

    /** [copy] as it was before [lazy], which keeps this one's [lazy]: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        path: String = this.path,
        regionX: Int = this.regionX,
        regionY: Int = this.regionY,
        regionWidth: Int = this.regionWidth,
        regionHeight: Int = this.regionHeight,
        sliceLeft: Int = this.sliceLeft,
        sliceTop: Int = this.sliceTop,
        sliceRight: Int = this.sliceRight,
        sliceBottom: Int = this.sliceBottom,
        repeatEdges: Boolean = this.repeatEdges,
        repeatCenter: Boolean = this.repeatCenter,
        tint: String = this.tint,
        pixelated: Boolean = this.pixelated,
        pixelsPerDp: Float = this.pixelsPerDp,
        hollow: Boolean = this.hollow,
    ): CanvasImage = CanvasImage(
        path, regionX, regionY, regionWidth, regionHeight, sliceLeft, sliceTop, sliceRight, sliceBottom,
        repeatEdges, repeatCenter, tint, pixelated, pixelsPerDp, hollow, lazy,
    )

    /** What is wrong with this image, as messages naming the field; empty when nothing is. */
    internal fun problems(): List<String> = buildList {
        if (path.isBlank()) add("path must name an image")
        if (minOf(regionX, regionY, regionWidth, regionHeight) < 0) add("region must not be negative")
        if (minOf(sliceLeft, sliceTop, sliceRight, sliceBottom) < 0) add("slices must not be negative")
        if (regionWidth > 0 && sliceLeft + sliceRight > regionWidth) add("left and right slices must fit in regionWidth")
        if (regionHeight > 0 && sliceTop + sliceBottom > regionHeight) add("top and bottom slices must fit in regionHeight")
        if (!isCanvasColor(tint)) add("tint \"$tint\" must be #RRGGBB or #RRGGBBAA")
        if (!(pixelsPerDp > 0f && pixelsPerDp.isFinite())) add("pixelsPerDp must be above 0")
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
                pixelsPerDp = if (pixelsPerDp > 0f && pixelsPerDp.isFinite()) pixelsPerDp else 1f,
                hollow = hollow,
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
