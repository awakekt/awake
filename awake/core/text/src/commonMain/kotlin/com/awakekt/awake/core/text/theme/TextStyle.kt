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
 */
data class TextStyle(
    val color: Color? = null,
    val size: Sp? = null,
    /** Optional authored line advance. Null keeps the font's intrinsic metrics. */
    val lineHeight: Sp? = null,
    val scale: Float = 1f,
    val weight: FontWeight = FontWeight.Normal,
    val letterSpacing: Sp = 0f.sp,
) {
    companion object {
        val Default = TextStyle()
    }

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
