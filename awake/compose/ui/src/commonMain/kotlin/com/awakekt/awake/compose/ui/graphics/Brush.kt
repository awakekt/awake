/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiLinearGradient

/**
 * Paint source for APIs that can accept either a flat colour or a four-corner linear gradient.
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
    }
}
