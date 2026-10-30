/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.color.Color
import kotlin.math.abs
import kotlin.math.max

/**
 * An outline and a shadow drawn with a glyph in the glyph's own draw, from its font's effect field (#1061): a coarse,
 * wide-reaching distance field whose tile sits in the same atlas page as the glyph (`UiFont.effectGlyphFor`). The
 * outline is the field's edge moved out by [outlineWidth]; the shadow is the field's edge moved by the offset and
 * softened across [shadowBlur] either side. One quad draws all three, shadow under outline under fill, so a thick
 * outline has no gaps between stamps and outlined, shadowed text costs one draw a glyph.
 *
 * Every length is in the same pixels as the glyph's own quad, and the tile is placed from the quad's own corner, so
 * moving a glyph moves its decoration with nothing to copy. Nothing here is UI vocabulary: a scene text pass draws a
 * glyph's decoration from the same values.
 *
 * @property fieldOffsetX Left edge of the glyph's effect tile from the left edge of the glyph's quad, in pixels.
 * @property fieldOffsetY Top edge of the effect tile from the top edge of the glyph's quad, in pixels.
 * @property fieldW Width of the effect tile, in pixels.
 * @property fieldH Height of the effect tile, in pixels.
 * @property fieldU0 Left UV of the effect tile in the glyph's atlas page.
 * @property fieldV0 Top UV of the effect tile.
 * @property fieldU1 Right UV of the effect tile.
 * @property fieldV1 Bottom UV of the effect tile.
 * @property outlineColor The outline's colour; transparent for none.
 * @property outlineWidth How far the outline reaches past the glyph's edge, in pixels; 0 for none.
 * @property shadowColor The shadow's colour; transparent for none.
 * @property shadowOffsetX How far right the shadow falls, in pixels.
 * @property shadowOffsetY How far down the shadow falls, in pixels.
 * @property shadowBlur The distance either side of the shadow's edge across which it fades, in pixels; 0 for a hard
 * shadow.
 */
data class GlyphDecoration(
    val fieldOffsetX: Float,
    val fieldOffsetY: Float,
    val fieldW: Float,
    val fieldH: Float,
    val fieldU0: Float,
    val fieldV0: Float,
    val fieldU1: Float,
    val fieldV1: Float,
    val outlineColor: Color = Color.Transparent,
    val outlineWidth: Float = 0f,
    val shadowColor: Color = Color.Transparent,
    val shadowOffsetX: Float = 0f,
    val shadowOffsetY: Float = 0f,
    val shadowBlur: Float = 0f,
) {
    /**
     * How far past a glyph's own quad the decoration can draw, in pixels: the outline, or the shadow's offset and blur,
     * whichever reaches further, and a pixel for the antialiased edge. The quad a decorated glyph draws is its own grown
     * by this and kept within its effect tile ([drawnBounds]).
     */
    val reach: Float
        get() {
            val outline = if (outlineColor.isTransparent()) 0f else outlineWidth
            val shadow = if (shadowColor.isTransparent()) 0f else shadowBlur + max(abs(shadowOffsetX), abs(shadowOffsetY))
            return max(outline, shadow) + EDGE_PX
        }

    /**
     * The rect a glyph with quad [x], [y], [w], [h] draws when decorated with this: its quad grown by [reach], within
     * its effect tile, and never smaller than the quad itself. Written to [out] as left, top, right, bottom.
     */
    fun drawnBounds(x: Float, y: Float, w: Float, h: Float, out: FloatArray) {
        val grow = reach
        val fieldX = x + fieldOffsetX
        val fieldY = y + fieldOffsetY
        out[0] = minOf(x, maxOf(x - grow, fieldX))
        out[1] = minOf(y, maxOf(y - grow, fieldY))
        out[2] = maxOf(x + w, minOf(x + w + grow, fieldX + fieldW))
        out[3] = maxOf(y + h, minOf(y + h + grow, fieldY + fieldH))
    }

    /** This decoration with its pixels scaled by [scaleX] and [scaleY], as a scope scales its glyph; itself at 1. */
    fun scaled(scaleX: Float, scaleY: Float): GlyphDecoration = if (scaleX == 1f && scaleY == 1f) {
        this
    } else {
        copy(
            fieldOffsetX = fieldOffsetX * scaleX,
            fieldOffsetY = fieldOffsetY * scaleY,
            fieldW = fieldW * scaleX,
            fieldH = fieldH * scaleY,
            outlineWidth = outlineWidth * scaleX,
            shadowOffsetX = shadowOffsetX * scaleX,
            shadowOffsetY = shadowOffsetY * scaleY,
            shadowBlur = shadowBlur * scaleX,
        )
    }

    /** This decoration with its colours dimmed by [alpha], as a scope dims its glyph's. */
    fun dimmedBy(alpha: Float): GlyphDecoration =
        if (alpha >= 1f) this else copy(outlineColor = outlineColor.timesAlpha(alpha), shadowColor = shadowColor.timesAlpha(alpha))

    private fun Color.timesAlpha(alpha: Float): Color = Color(r, g, b, a * alpha)
}

/** The antialiased edge's own pixel. */
private const val EDGE_PX = 1f
