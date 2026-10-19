/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.compose.ui.unit.Density
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.math2d.Dp

/**
 * Receiver scope for [com.awakekt.awake.compose.ui.draw.graphicsLayer] configuration lambda.
 */
interface GraphicsLayerScope : Density {
    /** Horizontal scale factor. */
    var scaleX: Float

    /** Vertical scale factor. */
    var scaleY: Float

    /** Horizontal translation offset in pixels. */
    var translationX: Float

    /** Vertical translation offset in pixels. */
    var translationY: Float

    /** Rotation around the Z axis in degrees. */
    var rotationDegrees: Float

    /** Opacity applied to the layer content, in range `[0f, 1f]`. */
    var alpha: Float

    /** Blend mode used when compositing the layer into its parent. */
    var blendMode: BlendMode

    /** Optional effect (e.g. blur or offset) applied to the layer texture. */
    var renderEffect: RenderEffect?

    /** Elevation shadow drawn behind the layer. */
    var shadowElevation: Dp

    /** Shape used for outline clipping and shadow geometry. */
    var shape: Shape

    /** Whether to clip the layer contents to [shape]. */
    var clip: Boolean
}
