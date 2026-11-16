/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.render.command.GpuSubPass

/**
 * The shadow array layers [prePasses] render into: one past the highest target layer, 0 when they
 * render none (#1698).
 *
 * The cascades a directional light draws are layers `0 until cascades`; a casting point light's six
 * faces follow them from `MAX_SHADOW_CASCADES`, one block of six per light. So a scene of three
 * cascades holds three layers, and one with a casting point light holds the cascade block and six
 * layers per point light.
 */
fun shadowLayersNeeded(prePasses: List<GpuSubPass>): Int {
    var needed = 0
    for (index in prePasses.indices) needed = maxOf(needed, prePasses[index].targetLayer + 1)
    return needed
}

/**
 * How many shadow array layers stay resident, frame by frame (#1698). Pure bookkeeping, shared by
 * the backends: a renderer reallocates its array when the answer changes.
 *
 * Grows at once, the first frame a light needs more layers, so turning shadows on costs one frame.
 * Shrinks to the most any frame used in the last window of [releaseAfterFrames] frames, and to none
 * when no light cast in it: a scene whose shadows were switched off frees its array between
 * [releaseAfterFrames] and twice that many frames later, and a light that casts only now and then
 * does not reallocate every frame it toggles.
 *
 * @property releaseAfterFrames Frames a layer must go unused before it is released.
 */
class ShadowArrayResidency(val releaseAfterFrames: Int = DEFAULT_RELEASE_AFTER_FRAMES) {
    init {
        require(releaseAfterFrames > 0) { "A residency window is at least one frame; was $releaseAfterFrames." }
    }

    private var windowPeak = 0
    private var windowFrames = 0

    /**
     * The layers to hold for a render that needs [needed] while [resident] are held: never fewer
     * than either. An offscreen render calls only this; it never ends a window.
     */
    fun layersFor(needed: Int, resident: Int): Int {
        windowPeak = maxOf(windowPeak, needed)
        return maxOf(needed, resident)
    }

    /**
     * The layers to hold after a presented frame that needed [needed] while [resident] are held:
     * [layersFor]'s answer, or fewer when this frame closes a window that used fewer.
     */
    fun frame(needed: Int, resident: Int): Int {
        val held = layersFor(needed, resident)
        windowFrames += 1
        if (windowFrames < releaseAfterFrames) return held
        val peak = windowPeak
        windowFrames = 0
        windowPeak = 0
        return minOf(held, peak)
    }

    /** The window every renderer uses. */
    companion object {
        /** Two seconds at 60 frames a second. */
        const val DEFAULT_RELEASE_AFTER_FRAMES: Int = 120
    }
}
