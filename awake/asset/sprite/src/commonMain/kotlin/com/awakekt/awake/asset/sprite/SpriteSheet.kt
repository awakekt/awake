/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.sprite

import com.awakekt.awake.core.animation.FrameClip

/**
 * A regular pixel grid and its named animation runs, with no scene, renderer or file I/O.
 *
 * @property image Image reference as written by the exporter; the caller chooses how to resolve it.
 * @property width Sheet width in pixels.
 * @property height Sheet height in pixels.
 * @property cellWidth Cell width in pixels.
 * @property cellHeight Cell height in pixels.
 * @param clips Named runs, copied on construction. Indices count from the top left in reading order.
 */
class SpriteSheet(
    val image: String,
    val width: Int,
    val height: Int,
    val cellWidth: Int,
    val cellHeight: Int,
    clips: Map<String, FrameClip>,
) {
    /** Named runs in exporter order, independent of subsequent mutations to the input map. */
    val clips: Map<String, FrameClip> = clips.toMap()

    init {
        require(image.isNotBlank()) { "Sprite sheet must name an image." }
        require(width > 0 && height > 0 && cellWidth > 0 && cellHeight > 0) { "Sprite sheet and cell sizes must be positive." }
        require(width % cellWidth == 0 && height % cellHeight == 0) { "Sprite sheet must divide into whole cells." }
        require(columns.toLong() * rows <= Int.MAX_VALUE) { "Sprite sheet cell count must fit in an Int." }
        require(this.clips.keys.none { it.isBlank() }) { "Sprite clip names must not be blank." }
        require(this.clips.values.all { it.firstFrame.toLong() + it.frameCount <= columns.toLong() * rows }) {
            "Sprite clip must stay within the sheet."
        }
    }

    /** Columns of equally sized cells. */
    val columns: Int get() = width / cellWidth

    /** Rows of equally sized cells. */
    val rows: Int get() = height / cellHeight

    /** Rejects an image whose decoded dimensions disagree with the manifest before it is rendered. */
    fun requireImageSize(width: Int, height: Int) {
        require(width == this.width && height == this.height) {
            "Sprite image $image is ${width}x$height; its manifest declares ${this.width}x${this.height}."
        }
    }
}
