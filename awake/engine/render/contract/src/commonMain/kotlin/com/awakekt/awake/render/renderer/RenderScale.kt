/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import kotlin.math.roundToInt

/**
 * The one definition of how [Renderer.renderScale] sizes a scene target, shared by every backend
 * and by hosts that want to show the cost of a scale before applying it.
 */
object RenderScale {
    /** The smallest scale [coerce] keeps; anything at or below 0 renders at this. */
    const val MIN: Float = 0.1f

    /** The largest scale, and the default: the scene renders at the presented surface's size. */
    const val FULL: Float = 1f

    /** [scale] clamped into `[MIN, FULL]`; a non-finite value is [FULL]. */
    fun coerce(scale: Float): Float = if (scale.isFinite()) scale.coerceIn(MIN, FULL) else FULL

    /**
     * Pixels along one axis of a scene target rendering [outputSize] pixels of output at [scale]:
     * rounded to the nearest pixel, and never below 1. At [FULL] it is [outputSize] exactly.
     */
    fun sceneExtent(outputSize: Int, scale: Float): Int {
        val coerced = coerce(scale)
        if (coerced == FULL || outputSize <= 0) return outputSize
        return (outputSize * coerced).roundToInt().coerceAtLeast(1)
    }

    /**
     * [viewport], measured on an [outputWidth] by [outputHeight] surface, moved onto a
     * [sceneWidth] by [sceneHeight] scene target: the same fraction of each axis.
     */
    fun sceneViewport(
        viewport: RenderViewport,
        outputWidth: Int,
        outputHeight: Int,
        sceneWidth: Int,
        sceneHeight: Int,
    ): RenderViewport {
        if (outputWidth <= 0 || outputHeight <= 0) return viewport
        val sx = sceneWidth.toFloat() / outputWidth
        val sy = sceneHeight.toFloat() / outputHeight
        return RenderViewport(viewport.x * sx, viewport.y * sy, viewport.width * sx, viewport.height * sy)
    }
}
