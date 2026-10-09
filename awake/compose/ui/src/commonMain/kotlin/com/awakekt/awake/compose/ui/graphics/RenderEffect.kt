/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.core.math2d.Dp

/** An effect applied to an isolated [com.awakekt.awake.compose.ui.draw.graphicsLayer]. */
sealed interface RenderEffect {
    /**
     * A nine-tap binomial blur over the isolated layer texture.
     *
     * @property radiusX The horizontal blur radius in dp, scaled by density when the layer is drawn.
     * @property radiusY The vertical blur radius in dp, scaled by density when the layer is drawn.
     */
    data class Blur(val radiusX: Dp, val radiusY: Dp) : RenderEffect {
        init {
            require(radiusX.value >= 0f && radiusY.value >= 0f) { "Blur radii must be non-negative." }
        }
    }

    /** Factories for [RenderEffect] values. */
    companion object {
        /** Creates a [Blur] with the same [radius] on both axes. */
        fun blur(radius: Dp): Blur = Blur(radius, radius)

        /** Creates a [Blur] with a separate radius on each axis. */
        fun blur(radiusX: Dp, radiusY: Dp): Blur = Blur(radiusX, radiusY)
    }
}
