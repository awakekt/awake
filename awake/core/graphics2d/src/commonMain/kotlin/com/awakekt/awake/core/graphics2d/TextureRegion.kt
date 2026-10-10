/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.math2d.Rectangle

/**
 * A rectangle of a texture in its UV space, (0, 0) its top-left corner and (1, 1) its bottom-right:
 * one icon of a sheet, one frame of an animation, one tile of a frame. Corners may be swapped to flip
 * the region, as a sprite drawn facing the other way is.
 *
 * @property u0 Left edge, as a fraction of the texture's width.
 * @property v0 Top edge, as a fraction of its height.
 * @property u1 Right edge.
 * @property v1 Bottom edge.
 */
data class TextureRegion(val u0: Float, val v0: Float, val u1: Float, val v1: Float) {

    /** This region with its left and right edges swapped: the same texels, mirrored. */
    fun flippedHorizontally(): TextureRegion = TextureRegion(u1, v0, u0, v1)

    /** This region with its top and bottom edges swapped: the same texels, upside down. */
    fun flippedVertically(): TextureRegion = TextureRegion(u0, v1, u1, v0)

    /** The whole texture, and regions measured in texels. */
    companion object {
        /** The whole texture. */
        val Whole: TextureRegion = TextureRegion(0f, 0f, 1f, 1f)

        /** The [texels] of a texture [textureWidth] by [textureHeight], measured from its top-left texel. */
        fun ofTexels(texels: Rectangle, textureWidth: Int, textureHeight: Int): TextureRegion {
            require(textureWidth > 0 && textureHeight > 0) { "A texture is at least 1 x 1; was $textureWidth x $textureHeight." }
            return TextureRegion(
                texels.x / textureWidth,
                texels.y / textureHeight,
                (texels.x + texels.width) / textureWidth,
                (texels.y + texels.height) / textureHeight,
            )
        }
    }
}
