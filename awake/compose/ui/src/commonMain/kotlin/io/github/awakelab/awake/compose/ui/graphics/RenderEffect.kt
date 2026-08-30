/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.graphics

import io.github.awakelab.awake.core.math2d.Dp

/** An effect applied to an isolated [io.github.awakelab.awake.compose.ui.draw.graphicsLayer]. */
sealed interface RenderEffect {
    /** A nine-tap binomial blur over the isolated layer texture. */
    data class Blur(val radiusX: Dp, val radiusY: Dp) : RenderEffect {
        init {
            require(radiusX.value >= 0f && radiusY.value >= 0f) { "Blur radii must be non-negative." }
        }
    }

    companion object {
        fun blur(radius: Dp): Blur = Blur(radius, radius)
        fun blur(radiusX: Dp, radiusY: Dp): Blur = Blur(radiusX, radiusY)
    }
}
