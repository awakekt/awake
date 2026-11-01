/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

/**
 * A renderer-owned, frame-to-frame pool of reusable staging arrays for the UI runs whose contents
 * change every frame, sitting beside [RetainedDrawRunCache] in the same per-renderer state.
 *
 * ## Why this exists
 *
 * A dynamic run (an animated mesh, a moving HUD element) misses [RetainedDrawRunCache] every frame,
 * so the coalescer re-tessellates it and -- before this -- allocated a fresh `FloatArray` and
 * `IntArray` for it each time. That was the per-frame garbage #1224 measured. This pool hands the
 * coalescer a reused buffer pair instead, so a dynamic run that holds a steady vertex count
 * allocates nothing after the first few frames.
 *
 * ## The identity-skip invariant it must not break
 *
 * Both backends' `DynamicMesh.update` skip the GPU upload when the vertex and index arrays are the
 * **same instances** the slot last received -- that is how an unchanged, retained run avoids
 * re-uploading. If a reused array were handed to the same mesh slot two frames running while its
 * contents changed, that skip would misfire and the changed run would not upload, leaving stale or
 * garbled UI.
 *
 * This pool prevents that with a **generation flip**: it keeps two disjoint generations of buffers
 * and alternates which one it serves every frame ([beginFrame]). Any buffer handed this frame comes
 * from a different generation than last frame's, so it is a different instance than whatever a mesh
 * slot last saw -- a dynamic run therefore always re-uploads correctly. Within a frame the active
 * generation hands out a distinct buffer per [take] call, so two runs never alias. Retained
 * (unchanged) runs keep their own immutable arrays from [RetainedDrawRunCache] and never pass
 * through this pool, so their upload-skip keeps working.
 *
 * ## First sight of a span
 *
 * A span the retained cache has never stored must be built into a fresh, immutable array and stored
 * once, so that -- if it turns out to be static -- it can hit the cache and skip from then on.
 * [firstSightSpan] reports that first encounter; the coalescer builds fresh and stores it, and only
 * routes a span through this pool on its later misses, which are the dynamic ones.
 *
 * ## Usage contract
 *
 * One pool per staging sequence that feeds one set of mesh slots, staged once per frame (the UI
 * overlay). [beginFrame] must be called exactly once at the start of each such staging pass. The
 * returned [Buffers] are valid only until the next [take] with the same cursor position, which is
 * next frame; a caller copies or uploads them (both backends' uploads copy) before then. Not
 * thread-safe: render thread only, like the coalescer it serves.
 */
class DynamicRunArrayPool {

    /**
     * A reused vertex/index buffer pair handed out by [take]. The arrays are replaced only when a
     * run at this cursor position needs a different size; otherwise the same instances are refilled.
     */
    class Buffers internal constructor() {
        /** Interleaved vertex floats, sized exactly to the current run. */
        var vertices: FloatArray = EMPTY_FLOATS
            private set

        /** Triangle-list indices, sized exactly to the current run. */
        var indices: IntArray = EMPTY_INTS
            private set

        /** Resizes to fit, reporting whether anything was reallocated so the pool can count it. */
        internal fun fit(floatCount: Int, intCount: Int): Boolean {
            var reallocated = false
            if (vertices.size != floatCount) {
                vertices = FloatArray(floatCount)
                reallocated = true
            }
            if (indices.size != intCount) {
                indices = IntArray(intCount)
                reallocated = true
            }
            return reallocated
        }
    }

    /** One of the two alternating buffer sets; its slots are reused by cursor position each frame. */
    private class Generation {
        val slots = ArrayList<Buffers>()
        private var cursor = 0

        fun reset() {
            cursor = 0
        }

        /** Takes the next slot, resizing it, and returns true from [Buffers.fit] when it grew. */
        fun take(floatCount: Int, intCount: Int, onResize: () -> Unit): Buffers {
            val fresh = cursor >= slots.size
            val buffers = if (fresh) Buffers().also { slots.add(it) } else slots[cursor]
            cursor += 1
            if (buffers.fit(floatCount, intCount) && !fresh) onResize()
            return buffers
        }
    }

    private val generations = arrayOf(Generation(), Generation())
    private var current = 0
    private var spanIndex = 0
    private var spansEverSeen = 0

    /** How many times a buffer at some cursor position was resized to fit a larger-or-smaller run.
     * Steady state is zero: once every run's size holds, nothing reallocates. A climbing count means
     * run sizes churn every frame -- the same signal `DynamicMesh.growthCount` carries. */
    var resizeCount: Int = 0
        private set

    /**
     * Flips to the other generation and rewinds its cursor. Call once per staging pass, before the
     * coalescer takes any buffers, so this frame's buffers are a different instance than last
     * frame's for every run.
     */
    fun beginFrame() {
        current = current xor 1
        generations[current].reset()
        spanIndex = 0
    }

    /**
     * Reports whether this cacheable span position is being reached for the first time across all
     * frames. Call once per cacheable span that missed [RetainedDrawRunCache], in span order.
     *
     * @return `true` on first sight -- the caller should build the span fresh and store it so a
     * static span can hit the cache later; `false` afterwards -- the caller should route the span
     * through [take], since a repeated miss is a changing (dynamic) span not worth caching.
     */
    fun firstSightSpan(): Boolean {
        val first = spanIndex >= spansEverSeen
        spanIndex += 1
        if (first) spansEverSeen = spanIndex
        return first
    }

    /**
     * Hands out this frame's reused buffer pair for the next run, sized to [floatCount] and
     * [intCount]. Distinct per call within a frame; a different instance than last frame's at the
     * same position.
     *
     * @param floatCount Vertex float count the run needs.
     * @param intCount Index count the run needs.
     * @return The reused buffers, already sized.
     */
    fun take(floatCount: Int, intCount: Int): Buffers =
        generations[current].take(floatCount, intCount) { resizeCount += 1 }

    private companion object {
        val EMPTY_FLOATS = FloatArray(0)
        val EMPTY_INTS = IntArray(0)
    }
}
