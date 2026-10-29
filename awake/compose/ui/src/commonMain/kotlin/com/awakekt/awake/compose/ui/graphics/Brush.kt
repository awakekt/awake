/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiLinearGradient

/**
 * Paint source for flat colours, four-corner gradients and evenly spaced axis gradients.
 *
 * It is intentionally small: texture, radial, sweep, and runtime-shader brushes need a material
 * path, while these two choices are representable by the existing UI vertex format.
 */
sealed interface Brush {
    /**
     * A single flat colour.
     *
     * @property color The colour to fill with.
     */
    data class SolidColor(val color: Color) : Brush

    /**
     * A gradient defined by one colour at each corner of the painted area.
     *
     * @property colors The four corner colours.
     */
    data class LinearGradient(val colors: UiLinearGradient) : Brush

    /** Factories for common [Brush] values. */
    companion object {
        /** Creates a gradient from the colour at each corner of the painted area. */
        fun linearGradient(
            topLeft: Color,
            topRight: Color,
            bottomRight: Color,
            bottomLeft: Color,
        ): Brush = LinearGradient(UiLinearGradient(topLeft, topRight, bottomRight, bottomLeft))

        /** Creates a gradient that runs from [start] on the left edge to [end] on the right edge. */
        fun horizontal(start: Color, end: Color): Brush = LinearGradient(UiLinearGradient.horizontal(start, end))

        /** Creates a gradient that runs from [top] on the top edge to [bottom] on the bottom edge. */
        fun vertical(top: Color, bottom: Color): Brush = LinearGradient(UiLinearGradient.vertical(top, bottom))

        /** Creates a left-to-right gradient with [colors] evenly spaced across the painted width. */
        fun horizontalGradient(colors: List<Color>): Brush = com.awakekt.awake.compose.ui.graphics.LinearGradient(colors.toList(), horizontal = true)

        /** Creates a top-to-bottom gradient with [colors] evenly spaced across the painted height. */
        fun verticalGradient(colors: List<Color>): Brush = com.awakekt.awake.compose.ui.graphics.LinearGradient(colors.toList(), horizontal = false)
    }
}

/**
 * An axis-aligned, evenly spaced linear gradient, created by [Brush.horizontalGradient] or
 * [Brush.verticalGradient]. Custom stop positions, tile modes and gradient shadows are not supported.
 *
 * @property colors Colours in stop order; at least two.
 * @property horizontal Whether the stops run left to right rather than top to bottom.
 */
data class LinearGradient internal constructor(val colors: List<Color>, val horizontal: Boolean) : Brush {
    init {
        require(colors.size >= 2) { "A linear gradient needs at least two colours" }
    }
}
