/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.theme

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Sp
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.font.FontWeight

/**
 * Groups text-related styling properties.
 *
 * @property color The foreground text color, or null to inherit.
 * @property size The font size in sp, or null to inherit.
 * @property lineHeight Optional authored line advance. Null keeps the font's intrinsic metrics.
 * @property scale Relative scale multiplier applied to the text.
 * @property weight Font weight indicating stroke thickness.
 * @property letterSpacing Additional spacing between adjacent characters.
 */
data class TextStyle(
    val color: Color? = null,
    val size: Sp? = null,
    val lineHeight: Sp? = null,
    val scale: Float = 1f,
    val weight: FontWeight = FontWeight.Normal,
    val letterSpacing: Sp = 0f.sp,
) {
    /**
     * Default text style constants.
     */
    companion object {
        /** Default empty text style with no overrides. */
        val Default = TextStyle()
    }

    /**
     * Merges this text style with [other], with properties in [other] taking precedence.
     *
     * @param other The style whose defined properties should override this style's properties.
     * @return The merged [TextStyle] instance.
     */
    infix fun then(other: TextStyle): TextStyle {
        val mergedColor = other.color ?: color
        val mergedSize = other.size ?: size
        val mergedLineHeight = other.lineHeight ?: lineHeight
        // A merge that changes nothing returns an instance that already exists. Every Text merges
        // its style over the inherited one every frame, and most of those merges are no-ops.
        val isOther = mergedColor == other.color && mergedSize == other.size && mergedLineHeight == other.lineHeight
        val keepsOwnFields = mergedColor == color && mergedSize == size && mergedLineHeight == lineHeight
        val keepsOwnRest = other.scale == scale && other.weight == weight && other.letterSpacing == letterSpacing
        return when {
            isOther -> other
            keepsOwnFields && keepsOwnRest -> this
            else -> TextStyle(mergedColor, mergedSize, mergedLineHeight, other.scale, other.weight, other.letterSpacing)
        }
    }
}
