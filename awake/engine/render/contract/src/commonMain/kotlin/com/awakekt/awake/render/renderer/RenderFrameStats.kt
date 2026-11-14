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
    private var renderPasses = 0
    private var uiBytes = 0L
    private var uiRuns = 0
    private var uiTriangles = 0L
    private val passes = RenderPassTally()

    /** Render passes the last frame [publish] closed began, offscreen blur and layer passes included; 0 before the first. */
    var latestRenderPasses: Int = 0
        private set

    /** UI mesh writes of the last frame [publish] closed, or null before the first one. */
    var latestUiUploads: UiUploadStats? = null
        private set

    /** The last frame [publish] closed split by the part of it that drew, or null before the first one. */
    var latestPasses: RenderPassStats? = null
        private set

    /** The last frame [publish] closed, or null before the first one. */
    var latest: RenderFrameStats? = null
        private set

    /** Counts one draw of [elementCount] vertices or indices, [instanceCount] times. */
    fun recordDraw(elementCount: Int, instanceCount: Int = 1, triangleList: Boolean = true) {
        drawCalls += 1
        instances += instanceCount
        val drawn = if (triangleList) (elementCount / TRIANGLE_VERTICES).toLong() * instanceCount else 0L
        triangles += drawn
        passes.record(instanceCount, drawn)
    }

    /**
     * Counts the draws recorded from here on toward [kind], until the next call or [publish].
     * A frame starts as [RenderPassKind.Other]. Shadow passes use [beginShadowPass] instead.
     */
    fun countAs(kind: RenderPassKind) = passes.countAs(kind)

    /** Counts the draws recorded from here on toward a new shadow pass of this frame. */
    fun beginShadowPass() = passes.beginShadowPass()

    /** Counts one UI run's mesh written to the GPU: [vertexFloats] floats and [indexCount] 32-bit indices. */
    fun recordUiUpload(vertexFloats: Int, indexCount: Int) {
        uiBytes += (vertexFloats.toLong() + indexCount) * Float.SIZE_BYTES
        uiRuns += 1
        uiTriangles += indexCount / TRIANGLE_VERTICES
    }

    /** Counts one render pass begun. */
    fun recordPass() {
        renderPasses += 1
    }

    /** Closes the frame being recorded and starts counting the next one. */
    fun publish(gpuTimeMs: Float? = null) {
        latest = RenderFrameStats(drawCalls, instances, triangles, gpuTimeMs)
        latestPasses = passes.publish()
        drawCalls = 0
        instances = 0
        triangles = 0L
        latestRenderPasses = renderPasses
        renderPasses = 0
        latestUiUploads = UiUploadStats(uiBytes, uiRuns, uiTriangles)
        uiBytes = 0L
        uiRuns = 0
        uiTriangles = 0L
    }

    private companion object {
        const val TRIANGLE_VERTICES = 3
    }
}
