/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * What the backend recorded for one submitted frame: every pass, shadow cascades and UI included.
 *
 * Counts come from the draw calls the backend issued, not from the scene, so an auto-instanced
 * batch of 4,000 entities is one draw of 4,000 instances, and a shadow-casting mesh is counted
 * once per cascade it lands in. Offscreen work submitted between two frames counts toward the next.
 */
data class RenderFrameStats(
    /** Draw calls recorded. */
    val drawCalls: Int,
    /** Instances across those draws; equals [drawCalls] when nothing was instanced. */
    val instances: Int,
    /** Triangles rasterised, instances included. Line draws add none. */
    val triangles: Long,
    /**
     * Milliseconds the GPU spent on the frame, from timestamps the device wrote around it, or
     * null when this backend or device cannot time the GPU or a complete measurement is unavailable.
     * Includes offscreen render submissions before that frame. Results lag until GPU readback completes.
     * WebGPU sums pass durations; browser timestamp precision may be reduced by the implementation.
     */
    val gpuTimeMs: Float? = null,
)

/**
 * Accumulates the draws a backend records until it submits a frame, then publishes them.
 *
 * A backend calls [recordDraw] at each draw it issues and [publish] once per submitted frame.
 * Not thread-safe: recording happens on the render thread.
 */
class RenderStatsCounter {
    private var drawCalls = 0
    private var instances = 0
    private var triangles = 0L

    /** The last frame [publish] closed, or null before the first one. */
    var latest: RenderFrameStats? = null
        private set

    /** Counts one draw of [elementCount] vertices or indices, [instanceCount] times. */
    fun recordDraw(elementCount: Int, instanceCount: Int = 1, triangleList: Boolean = true) {
        drawCalls += 1
        instances += instanceCount
        if (triangleList) triangles += (elementCount / TRIANGLE_VERTICES).toLong() * instanceCount
    }

    /** Closes the frame being recorded and starts counting the next one. */
    fun publish(gpuTimeMs: Float? = null) {
        latest = RenderFrameStats(drawCalls, instances, triangles, gpuTimeMs)
        drawCalls = 0
        instances = 0
        triangles = 0L
    }

    private companion object {
        const val TRIANGLE_VERTICES = 3
    }
}
