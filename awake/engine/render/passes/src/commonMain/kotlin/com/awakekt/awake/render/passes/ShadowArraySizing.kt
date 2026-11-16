/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

/**
 * Decides, frame by frame, how big the shadow depth array should be (#1698): a backend asks before
 * recording a frame and reallocates only when this says so.
 *
 * - Nothing is resident until a light casts: the array starts as [PLACEHOLDER_LAYERS] layer of
 *   [PLACEHOLDER_SIZE] texel, which a shader's shadow binding still needs to point at.
 * - On the first frame that draws shadows it becomes the layers that frame draws, at the budget's map
 *   size. While shadows keep being drawn it only grows, so lights that come and go reallocate once.
 * - A change of map size, a quality change, reallocates at once.
 * - After [releaseAfterFrames] frames in a row draw no shadow, it goes back to the placeholder, freeing
 *   the array. A scene that switches its shadows off and on within that time keeps the array.
 *
 * Each change makes the backend wait for its frames in flight before it frees the old array, a
 * one-frame hitch; [update] returns true at most once per change, never per frame.
 */
class ShadowArraySizing(
    private val releaseAfterFrames: Int = DEFAULT_RELEASE_AFTER_FRAMES,
) {
    init {
        require(releaseAfterFrames > 0) { "releaseAfterFrames must be positive: $releaseAfterFrames" }
    }

    /** The layers the array should hold after the last [update]. */
    var layers: Int = PLACEHOLDER_LAYERS
        private set

    /** The edge, in texels, the array should have after the last [update]. */
    var size: Int = PLACEHOLDER_SIZE
        private set

    /** Whether the array is the placeholder: no shadow memory beyond one texel is resident. */
    val isPlaceholder: Boolean get() = size == PLACEHOLDER_SIZE && layers == PLACEHOLDER_LAYERS

    private var idleFrames = 0

    /**
     * Takes a frame that draws [neededLayers] layers (0 when no light casts) under a budget of
     * [mapSize] texels, and returns whether [layers] or [size] changed, so the array must be
     * reallocated before the frame is recorded.
     */
    fun update(neededLayers: Int, mapSize: Int): Boolean {
        if (neededLayers <= 0) {
            idleFrames += 1
            if (isPlaceholder || idleFrames < releaseAfterFrames) return false
            layers = PLACEHOLDER_LAYERS
            size = PLACEHOLDER_SIZE
            return true
        }
        idleFrames = 0
        val wantedLayers = if (isPlaceholder) neededLayers else maxOf(layers, neededLayers)
        if (wantedLayers == layers && mapSize == size) return false
        layers = wantedLayers
        size = mapSize
        return true
    }

    companion object {
        /** The placeholder's layers: one, so an array view of it is valid. */
        const val PLACEHOLDER_LAYERS = 1

        /** The placeholder's edge: one texel. */
        const val PLACEHOLDER_SIZE = 1

        /** About five seconds at 60 frames a second, longer than a scene toggles its shadows. */
        const val DEFAULT_RELEASE_AFTER_FRAMES = 300
    }
}
