/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.draw

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.RenderEffect
import com.awakekt.awake.compose.ui.graphics.TileMode
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.dp

/**
 * Blurs this node's content with separate horizontal and vertical radii.
 *
 * If both radii are non-positive (<= 0.dp), this modifier returns the unmodified receiver.
 *
 * When [edgeTreatment] specifies a clipping shape (such as [BlurredEdgeTreatment.Rectangle]),
 * the content is clipped to that shape and sampled with [TileMode.Clamp]. When [edgeTreatment]
 * is [BlurredEdgeTreatment.Unbounded], blurred pixels expand freely outside the layout bounds
 * with [TileMode.Decal].
 *
 * @param radiusX Horizontal blur radius in dp.
 * @param radiusY Vertical blur radius in dp.
 * @param edgeTreatment How edges outside the node's layout bounds are handled.
 */
fun Modifier.blur(
    radiusX: Dp,
    radiusY: Dp,
    edgeTreatment: BlurredEdgeTreatment = BlurredEdgeTreatment.Rectangle,
): Modifier {
    if (radiusX <= 0.dp && radiusY <= 0.dp) return this
    return graphicsLayer {
        this.renderEffect = if (radiusX > 0.dp || radiusY > 0.dp) {
            RenderEffect.createBlurEffect(
                radiusX = radiusX.toPx().coerceAtLeast(0f),
                radiusY = radiusY.toPx().coerceAtLeast(0f),
                edgeTreatment = if (edgeTreatment.shape == null) TileMode.Decal else TileMode.Clamp,
            )
        } else {
            null
        }
        val shape = edgeTreatment.shape
        if (shape != null) {
            this.clip = true
            this.shape = shape
        }
    }
}

/**
 * Blurs this node's content uniformly along both axes.
 *
 * @param radius Blur radius in dp applied to both horizontal and vertical axes.
 * @param edgeTreatment How edges outside the node's layout bounds are handled.
 */
fun Modifier.blur(
    radius: Dp,
    edgeTreatment: BlurredEdgeTreatment = BlurredEdgeTreatment.Rectangle,
): Modifier = blur(radius, radius, edgeTreatment)
