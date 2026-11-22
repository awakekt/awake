/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.renderer.OutlineStyle
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The scissor the outline overlay draws under (#1709): the screen rectangle around every styled
 * mask layer's draws ([GpuSubPass.screenBounds]), grown by the widest outline and a pixel, and kept
 * inside the scene's rectangle. The overlay is a full-screen triangle, so without it every pixel of
 * the scene ran the outline shader's mask samples for one outlined object.
 *
 * When a styled layer's bounds are unknown, the scissor is the whole scene ([narrowed] false). Reused
 * every frame: [fit] allocates nothing.
 */
internal class OutlineScissor {
    var x = 0
        private set
    var y = 0
        private set
    var width = 0
        private set
    var height = 0
        private set

    /** The scene's whole rectangle, to put back after the overlay's draw. */
    var fullX = 0
        private set
    var fullY = 0
        private set
    var fullWidth = 0
        private set
    var fullHeight = 0
        private set

    /** Whether the scissor is narrower than the scene's rectangle. */
    var narrowed = false
        private set

    // The styled layers' union, in texture coordinates, and their widest outline: [union]'s output.
    private var minU = 0f
    private var minV = 0f
    private var maxU = 0f
    private var maxV = 0f
    private var pad = 0f

    /**
     * Fits the scissor to [masks]' styled layers in the scene rectangle at [left], [top], [sceneWidth]
     * by [sceneHeight] pixels. Returns false when the outline can't reach the scene: nothing to draw.
     */
    fun fit(masks: List<GpuSubPass>, styles: Array<OutlineStyle?>, left: Float, top: Float, sceneWidth: Float, sceneHeight: Float): Boolean {
        fullX = left.toInt()
        fullY = top.toInt()
        fullWidth = sceneWidth.toInt()
        fullHeight = sceneHeight.toInt()
        val known = union(masks, styles)
        return if (known && minU <= maxU) clip(left, top, sceneWidth, sceneHeight) else whole()
    }

    /** Unions the styled layers' bounds and widest outline; false when one of them has no bounds. */
    private fun union(masks: List<GpuSubPass>, styles: Array<OutlineStyle?>): Boolean {
        minU = Float.POSITIVE_INFINITY
        minV = Float.POSITIVE_INFINITY
        maxU = Float.NEGATIVE_INFINITY
        maxV = Float.NEGATIVE_INFINITY
        pad = 0f
        var known = true
        for (mask in masks) {
            val style = styles.getOrNull(mask.targetLayer)?.takeIf { it.draws } ?: continue
            pad = maxOf(pad, style.widthPixels)
            val bounds = mask.screenBounds
            if (bounds == null) {
                known = false
            } else {
                minU = minOf(minU, bounds.x)
                minV = minOf(minV, bounds.y)
                maxU = maxOf(maxU, bounds.right)
                maxV = maxOf(maxV, bounds.bottom)
            }
        }
        return known
    }

    /** The union's pixels in the scene, grown by the outline and a pixel and clamped to it; false when empty. */
    private fun clip(left: Float, top: Float, sceneWidth: Float, sceneHeight: Float): Boolean {
        val grow = pad + 1f
        val x0 = floor(left + minU * sceneWidth - grow).coerceIn(left, left + sceneWidth)
        val y0 = floor(top + minV * sceneHeight - grow).coerceIn(top, top + sceneHeight)
        val x1 = ceil(left + maxU * sceneWidth + grow).coerceIn(left, left + sceneWidth)
        val y1 = ceil(top + maxV * sceneHeight + grow).coerceIn(top, top + sceneHeight)
        val drawn = x1 > x0 && y1 > y0
        if (drawn) {
            x = x0.toInt()
            y = y0.toInt()
            width = (x1 - x0).toInt()
            height = (y1 - y0).toInt()
            narrowed = x != fullX || y != fullY || width != fullWidth || height != fullHeight
        }
        return drawn
    }

    private fun whole(): Boolean {
        x = fullX
        y = fullY
        width = fullWidth
        height = fullHeight
        narrowed = false
        return true
    }
}
