/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.graphics

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.UiLinearGradient

/**
 * Paint source for APIs that can accept either a flat colour or a four-corner linear gradient.
 *
 * It is intentionally small: texture, radial, sweep, and runtime-shader brushes need a material
 * path, while these two choices are representable by the existing UI vertex format.
 */
sealed interface Brush {
    data class SolidColor(val color: Color) : Brush

    data class LinearGradient(val colors: UiLinearGradient) : Brush

    companion object {
        fun linearGradient(
            topLeft: Color,
            topRight: Color,
            bottomRight: Color,
            bottomLeft: Color,
        ): Brush = LinearGradient(UiLinearGradient(topLeft, topRight, bottomRight, bottomLeft))

        fun horizontal(start: Color, end: Color): Brush = LinearGradient(UiLinearGradient.horizontal(start, end))

        fun vertical(top: Color, bottom: Color): Brush = LinearGradient(UiLinearGradient.vertical(top, bottom))
    }
}
