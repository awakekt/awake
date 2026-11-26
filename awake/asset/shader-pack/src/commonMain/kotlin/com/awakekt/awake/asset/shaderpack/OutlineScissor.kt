/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.render.command.GpuSubPass
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The scissor rectangle(s) the outline overlay draws under (#1709): one or more strictly pairwise
 * disjoint pixel rectangles around every styled mask layer's draws ([GpuSubPass.screenBoundRects]
 * or [GpuSubPass.screenBounds]), each grown by its layer's outline width plus one pixel and clamped
 * to the scene's rectangle.
 *
 * Overlapping clusters are iteratively merged into their bounding union so no screen pixel is ever
 * shaded or alpha-blended twice by the overlay, while distant outlined targets keep separate tight
 * scissor rectangles instead of shading the empty gap between them. When a styled layer's bounds
 * are unknown, the scissor falls back to the whole scene ([narrowed] false). Reused every frame:
 * [fit] allocates nothing.
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

    /** The scene's whole rectangle, to put back after the overlay's draw(s). */
    var fullX = 0
        private set
    var fullY = 0
        private set
    var fullWidth = 0
        private set
    var fullHeight = 0
        private set

    /** Whether at least one scissor rectangle is narrower than the scene's rectangle. */
    var narrowed = false
        private set

    /** Number of disjoint scissor rectangles to draw (0 when off-screen, 1..N otherwise). */
    var rectCount = 0
        private set

    private val rectX0 = IntArray(MAX_SCISSOR_RECTS)
    private val rectY0 = IntArray(MAX_SCISSOR_RECTS)
    private val rectX1 = IntArray(MAX_SCISSOR_RECTS)
    private val rectY1 = IntArray(MAX_SCISSOR_RECTS)

    fun rectX(index: Int): Int = rectX0[index]

    fun rectY(index: Int): Int = rectY0[index]

    fun rectWidth(index: Int): Int = rectX1[index] - rectX0[index]

    fun rectHeight(index: Int): Int = rectY1[index] - rectY0[index]

    /**
     * Fits one or more disjoint scissors to [masks]' active styled layers in the scene rectangle at
     * [left], [top], [sceneWidth] by [sceneHeight] pixels. Returns false when no outline reaches
     * the scene.
     */
    @Suppress("LongParameterList")
    fun fit(
        masks: List<GpuSubPass>,
        draws: BooleanArray,
        widths: FloatArray,
        left: Float,
        top: Float,
        sceneWidth: Float,
        sceneHeight: Float,
    ): Boolean {
        fullX = left.toInt()
        fullY = top.toInt()
        fullWidth = sceneWidth.toInt()
        fullHeight = sceneHeight.toInt()
        rectCount = 0
        return when {
            !collect(masks, draws, widths, left, top, sceneWidth, sceneHeight) -> whole()
            rectCount == 0 -> false
            else -> {
                mergeOverlapping()
                x = rectX0[0]
                y = rectY0[0]
                width = rectWidth(0)
                height = rectHeight(0)
                narrowed = rectCount > 1 || x != fullX || y != fullY || width != fullWidth || height != fullHeight
                true
            }
        }
    }

    /** Collects padded, scene-clamped rects for every active styled layer; false if any lacks bounds. */
    @Suppress("LongParameterList")
    private fun collect(
        masks: List<GpuSubPass>,
        draws: BooleanArray,
        widths: FloatArray,
        left: Float,
        top: Float,
        sceneWidth: Float,
        sceneHeight: Float,
    ): Boolean {
        for (i in masks.indices) {
            val mask = masks[i]
            val layer = mask.targetLayer
            if (layer !in draws.indices || !draws[layer]) continue
            val bounds = mask.screenBounds ?: return false
            val grow = widths[layer] + 1f
            val packed = mask.screenBoundRects
            val packedCount = mask.screenBoundRectCount
            if (packed != null && packedCount > 0) {
                for (r in 0 until packedCount) {
                    val base = r * RECT_FLOATS
                    addClamped(
                        packed[base],
                        packed[base + 1],
                        packed[base + 2],
                        packed[base + 3],
                        grow,
                        left,
                        top,
                        sceneWidth,
                        sceneHeight,
                    )
                }
            } else {
                addClamped(bounds.x, bounds.y, bounds.right, bounds.bottom, grow, left, top, sceneWidth, sceneHeight)
            }
        }
        return true
    }

    @Suppress("LongParameterList")
    private fun addClamped(
        minU: Float,
        minV: Float,
        maxU: Float,
        maxV: Float,
        grow: Float,
        left: Float,
        top: Float,
        sceneWidth: Float,
        sceneHeight: Float,
    ) {
        val x0 = floor(left + minU * sceneWidth - grow).coerceIn(left, left + sceneWidth).toInt()
        val y0 = floor(top + minV * sceneHeight - grow).coerceIn(top, top + sceneHeight).toInt()
        val x1 = ceil(left + maxU * sceneWidth + grow).coerceIn(left, left + sceneWidth).toInt()
        val y1 = ceil(top + maxV * sceneHeight + grow).coerceIn(top, top + sceneHeight).toInt()
        if (x1 <= x0 || y1 <= y0) return
        if (rectCount < MAX_SCISSOR_RECTS) {
            val slot = rectCount++
            rectX0[slot] = x0
            rectY0[slot] = y0
            rectX1[slot] = x1
            rectY1[slot] = y1
        } else {
            rectX0[0] = minOf(rectX0[0], x0)
            rectY0[0] = minOf(rectY0[0], y0)
            rectX1[0] = maxOf(rectX1[0], x1)
            rectY1[0] = maxOf(rectY1[0], y1)
        }
    }

    /**
     * Merges any overlapping pair (and any adjacent pair whose bounding union adds zero pixels)
     * until every remaining rectangle is strictly pairwise disjoint.
     */
    private fun mergeOverlapping() {
        var changed = true
        while (changed && rectCount > 1) {
            changed = false
            var i = 0
            while (i < rectCount && !changed) {
                var j = i + 1
                while (j < rectCount && !changed) {
                    if (shouldMerge(i, j)) {
                        mergeInto(i, j)
                        changed = true
                    }
                    j++
                }
                i++
            }
        }
    }

    private fun shouldMerge(i: Int, j: Int): Boolean {
        val overlaps = rectX0[i] < rectX1[j] && rectX0[j] < rectX1[i] &&
            rectY0[i] < rectY1[j] && rectY0[j] < rectY1[i]
        if (overlaps) return true
        val mx0 = minOf(rectX0[i], rectX0[j])
        val my0 = minOf(rectY0[i], rectY0[j])
        val mx1 = maxOf(rectX1[i], rectX1[j])
        val my1 = maxOf(rectY1[i], rectY1[j])
        val mergedArea = (mx1 - mx0).toLong() * (my1 - my0).toLong()
        val combinedArea = (rectX1[i] - rectX0[i]).toLong() * (rectY1[i] - rectY0[i]).toLong() +
            (rectX1[j] - rectX0[j]).toLong() * (rectY1[j] - rectY0[j]).toLong()
        return mergedArea <= combinedArea
    }

    private fun mergeInto(target: Int, source: Int) {
        rectX0[target] = minOf(rectX0[target], rectX0[source])
        rectY0[target] = minOf(rectY0[target], rectY0[source])
        rectX1[target] = maxOf(rectX1[target], rectX1[source])
        rectY1[target] = maxOf(rectY1[target], rectY1[source])
        val last = --rectCount
        if (source != last) {
            rectX0[source] = rectX0[last]
            rectY0[source] = rectY0[last]
            rectX1[source] = rectX1[last]
            rectY1[source] = rectY1[last]
        }
    }

    private fun whole(): Boolean {
        rectCount = 1
        rectX0[0] = fullX
        rectY0[0] = fullY
        rectX1[0] = fullX + fullWidth
        rectY1[0] = fullY + fullHeight
        x = fullX
        y = fullY
        width = fullWidth
        height = fullHeight
        narrowed = false
        return true
    }

    private companion object {
        const val MAX_SCISSOR_RECTS = 32
        const val RECT_FLOATS = 4
    }
}
