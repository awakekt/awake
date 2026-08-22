// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.core.graphics2d

import io.github.ronjunevaldoz.awake.core.color.Color

data class LinearGradient(
    val topLeft: Color,
    val topRight: Color,
    val bottomRight: Color,
    val bottomLeft: Color,
) {
    companion object {
        fun vertical(top: Color, bottom: Color): LinearGradient = LinearGradient(
            topLeft = top,
            topRight = top,
            bottomRight = bottom,
            bottomLeft = bottom,
        )

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
