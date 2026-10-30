/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.GlyphDecoration
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.GlyphRect
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.theme.TextStyle

/**
 * A text style's outline and shadow in device pixels, drawn with each glyph in its own draw from the font's effect
 * field (#1061): one quad a glyph, where stamping the run eight times for the outline and once more for the shadow
 * cost up to ten, and left gaps between the stamps of a thick outline.
 */
internal class GlyphEffects private constructor(
    internal val outlineColor: Color,
    internal val outlineWidth: Float,
    internal val shadowColor: Color,
    internal val shadowOffsetX: Float,
    internal val shadowOffsetY: Float,
    internal val shadowBlur: Float,
) {
    /**
     * Each code point's decoration, built the first time it is drawn: its tile sits at the same place from its quad
     * at this run's size every frame, so a run at rest allocates none. A [GlyphEffects] serves one laid-out run, whose
     * font, size and weight do not change; null records a glyph with no effect tile.
     */
    private val decorations = HashMap<Int, GlyphDecoration?>()

    /**
     * Draws the glyph [uv] of [codePoint] with these effects, its pen at [penX] on the line whose em box starts at
     * [lineTop]. False, and nothing drawn, when [font] has no effect tile for it or [glyphFont], the page it samples,
     * reads no effect field: the caller stamps that glyph instead.
     */
    @Suppress("LongParameterList")
    fun draw(
        scope: DrawScope,
        font: UiFont,
        glyphFont: UiFont,
        codePoint: Int,
        weight: FontWeight,
        uv: GlyphRect,
        penX: Float,
        lineTop: Float,
        glyphPx: Float,
        color: Color,
    ): Boolean {
        val decoration = if (glyphFont.effectFieldRangePx > 0f) {
            decorations.getOrPut(codePoint) { decorationOf(font, codePoint, weight, uv, glyphPx) }
        } else {
            null
        }
        decoration ?: return false
        scope.drawDecoratedGlyph(
            x = penX + uv.offsetXEm * glyphPx,
            y = lineTop + uv.offsetYEm * glyphPx,
            width = uv.widthEm * glyphPx,
            height = uv.heightEm * glyphPx,
            u0 = uv.u0,
            v0 = uv.v0,
            u1 = uv.u1,
            v1 = uv.v1,
            color = color,
            font = glyphFont,
            page = uv.page,
            decoration = decoration,
        )
        return true
    }

    /** [codePoint]'s decoration at [glyphPx], its tile placed from the corner of the quad [uv] draws; null with no tile. */
    private fun decorationOf(font: UiFont, codePoint: Int, weight: FontWeight, uv: GlyphRect, glyphPx: Float): GlyphDecoration? {
        val tile = font.effectGlyphFor(codePoint, weight) ?: return null
        return GlyphDecoration(
            fieldOffsetX = (tile.offsetXEm - uv.offsetXEm) * glyphPx,
            fieldOffsetY = (tile.offsetYEm - uv.offsetYEm) * glyphPx,
            fieldW = tile.widthEm * glyphPx,
            fieldH = tile.heightEm * glyphPx,
            fieldU0 = tile.u0,
            fieldV0 = tile.v0,
            fieldU1 = tile.u1,
            fieldV1 = tile.v1,
            outlineColor = outlineColor,
            outlineWidth = outlineWidth,
            shadowColor = shadowColor,
            shadowOffsetX = shadowOffsetX,
            shadowOffsetY = shadowOffsetY,
            shadowBlur = shadowBlur,
        )
    }

    /**
     * The glyph [draw] could not decorate, stamped the old way: its shadow moved by the offset, its outline in eight
     * steps around it, then the glyph, through [stamp] with a colour and a pixel offset.
     */
    inline fun stamp(color: Color, stamp: (color: Color, dx: Float, dy: Float) -> Unit) {
        if (!shadowColor.isTransparent()) stamp(shadowColor, shadowOffsetX, shadowOffsetY)
        if (outlineWidth > 0f && !outlineColor.isTransparent()) {
            for (i in OUTLINE_X.indices) stamp(outlineColor, OUTLINE_X[i] * outlineWidth, OUTLINE_Y[i] * outlineWidth)
        }
        stamp(color, 0f, 0f)
    }

    companion object {
        /** [style]'s outline and shadow at [density] pixels a dp, or null when it has neither. */
        fun of(style: TextStyle, density: Float): GlyphEffects? {
            val outline = style.outline
            val shadow = style.shadow
            if (outline == null && shadow == null) return null
            return GlyphEffects(
                outlineColor = outline?.color ?: Color.Transparent,
                outlineWidth = (outline?.width ?: 0f) * density,
                shadowColor = shadow?.color ?: Color.Transparent,
                shadowOffsetX = (shadow?.offsetX ?: 0f) * density,
                shadowOffsetY = (shadow?.offsetY ?: 0f) * density,
                shadowBlur = (shadow?.blur ?: 0f) * density,
            )
        }

        // Unit steps to the eight neighbours; the diagonals are shortened so the ring is round, not square.
        internal val OUTLINE_X = floatArrayOf(1f, 0.7071f, 0f, -0.7071f, -1f, -0.7071f, 0f, 0.7071f)
        internal val OUTLINE_Y = floatArrayOf(0f, 0.7071f, 1f, 0.7071f, 0f, -0.7071f, -1f, -0.7071f)
    }
}
