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
    /**
     * The median time between presented frames over the renderer's recent window, in nanoseconds, or
     * null when this backend does not measure presents or before its second present. See
     * [PresentIntervalTracker].
     */
    val presentIntervalP50Ns: Long? = null,
    /** The 95th-percentile time between presented frames, in nanoseconds; null as for [presentIntervalP50Ns]. */
    val presentIntervalP95Ns: Long? = null,
    /** Where [presentIntervalP50Ns] and [presentIntervalP95Ns] were measured, or null when they are. */
    val presentTiming: PresentTiming? = null,
) {
    /** The constructor as released before the present intervals, kept so compiled callers still link. */
    @Deprecated("Use the constructor with the present intervals.", level = DeprecationLevel.HIDDEN)
    constructor(drawCalls: Int, instances: Int, triangles: Long, gpuTimeMs: Float? = null) :
        this(drawCalls, instances, triangles, gpuTimeMs, null, null, null)

    /** [copy] as released before the present intervals. */
    @Deprecated("Use copy with the present intervals.", level = DeprecationLevel.HIDDEN)
    fun copy(
        drawCalls: Int = this.drawCalls,
        instances: Int = this.instances,
        triangles: Long = this.triangles,
        gpuTimeMs: Float? = this.gpuTimeMs,
    ): RenderFrameStats = copy(drawCalls, instances, triangles, gpuTimeMs, presentIntervalP50Ns, presentIntervalP95Ns, presentTiming)
}

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
    private var argumentBuffers = 0L
    private var instanceBuffers = 0L
    private var uniformBuffers = 0L
    private var publishedArgumentBuffers = 0L
    private var publishedInstanceBuffers = 0L
    private var publishedUniformBuffers = 0L
    private var countsBuffers = false
    private var packetsBuilt = 0L
    private var packetsReused = 0L
    private var bundlesEncoded = 0L
    private var publishedPacketsBuilt = 0L
    private var publishedPacketsReused = 0L
    private var publishedBundlesEncoded = 0L
    private var frameBuilt = 0
    private var frameReused = 0
    private var frameEncoded = 0
    private var countsReuse = false
    private var reusePublished = false

    /**
     * The backend's present intervals, carried into each frame [publish] closes, or null for a backend
     * that does not measure its presents.
     */
    var presentIntervals: PresentIntervalTracker? = null

    /** Render passes the last frame [publish] closed began, offscreen blur and layer passes included; 0 before the first. */
    var latestRenderPasses: Int = 0
        private set

    /** UI mesh writes of the last frame [publish] closed, or null before the first one. */
    var latestUiUploads: UiUploadStats? = null
        private set

    /**
     * GPU buffers the draw path created during the last frame [publish] closed, or null before the
     * first one and for a backend that never calls [bufferCreationTotals].
     */
    var latestBufferCreations: BufferCreationStats? = null
        private set

    /**
     * Draw work the last frame [publish] closed reused from the one before, or null before the first
     * one and for a backend that never calls [drawReuseTotals]. Built when read, from counts kept as
     * plain numbers, so a frame nobody reads allocates nothing for it.
     */
    val latestDrawReuse: DrawReuseStats?
        get() = if (reusePublished) DrawReuseStats(frameBuilt, frameReused, frameEncoded) else null

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

    /**
     * Sets how many GPU buffers the draw path has created so far, by site, over the renderer's life.
     * [publish] reports the growth since the frame before, so a backend keeps one running count per
     * site and calls this before each [publish].
     */
    fun bufferCreationTotals(argumentBuffers: Long, instanceBuffers: Long, uniformBuffers: Long) {
        this.argumentBuffers = argumentBuffers
        this.instanceBuffers = instanceBuffers
        this.uniformBuffers = uniformBuffers
        countsBuffers = true
    }

    /**
     * Sets how many draw packets the backend has built and reused, and how many bundles it has
     * encoded, over its life. [publish] reports the growth since the frame before, as
     * [bufferCreationTotals] does.
     */
    fun drawReuseTotals(packetsBuilt: Long, packetsReused: Long, bundlesEncoded: Long) {
        this.packetsBuilt = packetsBuilt
        this.packetsReused = packetsReused
        this.bundlesEncoded = bundlesEncoded
        countsReuse = true
    }

    /** Counts one render pass begun. */
    fun recordPass() {
        renderPasses += 1
    }

    /** Closes the frame being recorded and starts counting the next one. */
    fun publish(gpuTimeMs: Float? = null) {
        val presents = presentIntervals
        latest = RenderFrameStats(
            drawCalls,
            instances,
            triangles,
            gpuTimeMs,
            presents?.percentileNanos(MEDIAN),
            presents?.percentileNanos(P95),
            presents?.timing?.takeIf { presents.count > 0 },
        )
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
        if (countsBuffers) publishBufferCreations()
        if (countsReuse) publishDrawReuse()
    }

    private fun publishDrawReuse() {
        frameBuilt = (packetsBuilt - publishedPacketsBuilt).toInt()
        frameReused = (packetsReused - publishedPacketsReused).toInt()
        frameEncoded = (bundlesEncoded - publishedBundlesEncoded).toInt()
        publishedPacketsBuilt = packetsBuilt
        publishedPacketsReused = packetsReused
        publishedBundlesEncoded = bundlesEncoded
        reusePublished = true
    }

    private fun publishBufferCreations() {
        latestBufferCreations = BufferCreationStats(
            argumentBuffers = (argumentBuffers - publishedArgumentBuffers).toInt(),
            instanceBuffers = (instanceBuffers - publishedInstanceBuffers).toInt(),
            uniformBuffers = (uniformBuffers - publishedUniformBuffers).toInt(),
        )
        publishedArgumentBuffers = argumentBuffers
        publishedInstanceBuffers = instanceBuffers
        publishedUniformBuffers = uniformBuffers
    }

    private companion object {
        const val TRIANGLE_VERTICES = 3
        const val MEDIAN = 50
        const val P95 = 95
    }
}
