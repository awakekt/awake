/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.color.Color

/**
 * A four-corner linear gradient definition specifying colors at each corner of a quad.
 *
 * @property topLeft Color at the top-left corner.
 * @property topRight Color at the top-right corner.
 * @property bottomRight Color at the bottom-right corner.
 * @property bottomLeft Color at the bottom-left corner.
 */
data class LinearGradient(
    val topLeft: Color,
    val topRight: Color,
    val bottomRight: Color,
    val bottomLeft: Color,
) {
    /**
     * Factory methods for constructing directional [LinearGradient] instances.
     */
    companion object {
        /**
         * Creates a vertical linear gradient transitioning from [top] to [bottom].
         *
         * @param top The color at the top edge.
         * @param bottom The color at the bottom edge.
         * @return A [LinearGradient] configured vertically.
         */
        fun vertical(top: Color, bottom: Color): LinearGradient = LinearGradient(
            topLeft = top,
            topRight = top,
            bottomRight = bottom,
            bottomLeft = bottom,
        )

        /**
         * Creates a horizontal linear gradient transitioning from [start] to [end].
         *
         * @param start The color at the left edge.
         * @param end The color at the right edge.
         * @return A [LinearGradient] configured horizontally.
         */
        fun horizontal(start: Color, end: Color): LinearGradient = LinearGradient(
            topLeft = start,
            topRight = end,
            bottomRight = end,
            bottomLeft = start,
        )
    }
}

typealias UiLinearGradient = LinearGradient
typealias Gradient = LinearGradient
