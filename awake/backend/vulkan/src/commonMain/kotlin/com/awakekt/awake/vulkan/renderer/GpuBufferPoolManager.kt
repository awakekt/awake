/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.render.passes.GpuResourcePool
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.mesh.AlphaInstanceBuffer
import com.awakekt.awake.vulkan.mesh.FrameInstanceBuffer
import com.awakekt.awake.vulkan.mesh.InstanceBuffer
import com.awakekt.awake.vulkan.mesh.SkinnedInstanceBuffer
import com.awakekt.awake.vulkan.pipeline.VulkanBufferBinding
import com.awakekt.awake.vulkan.ui.DynamicMesh

/**
 * Manages reusable, grow-on-demand dynamic mesh and instance buffer pools across frames.
 */
@Suppress("TooManyFunctions") // One typed accessor per managed GPU buffer pool plus lifecycle hooks.
internal class GpuBufferPoolManager(
    private val graphicsDevice: GraphicsDevice,
    private val maxFramesInFlight: Int,
) {
    // Each pool is (policy, factory): GpuResourcePool owns "grow on demand, index by run,
    // never shrink" once, this file names only what to build. Both backends declare the same
    // eight; only the constructed type differs.
    /** The overlay's UI meshes; each offscreen target has its own in [targetUiSlots]. */
    private val overlayUiSlots = UiMeshSlots(graphicsDevice, maxFramesInFlight)
    private val targetUiSlots = HashMap<RenderTarget, UiMeshSlots>()
    private val retiredUiSlots = RetiredResources<UiMeshSlots>(maxFramesInFlight, UiMeshSlots::destroy)

    private val uiTextureMeshPool = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.TEXTURE_PRIMITIVE_QUADS, DynamicMesh.TEXTURE_FLOATS_PER_VERTEX, maxFramesInFlight)
    }
    private val imagePassMeshPool = GpuResourcePool {
        DynamicMesh(graphicsDevice, 1, DynamicMesh.IMAGE_PASS_FLOATS_PER_VERTEX, maxFramesInFlight)
    }

    // +1 frame over the swapchain's own count -- see InstanceBuffer's doc comment.
    private val instanceBufferPool = GpuResourcePool {
        InstanceBuffer(graphicsDevice, framesInFlight = maxFramesInFlight + 1)
    }
    private val skinnedInstanceBufferPool = GpuResourcePool {
        SkinnedInstanceBuffer(graphicsDevice, framesInFlight = maxFramesInFlight + 1)
    }
    private val alphaInstanceBufferPool = GpuResourcePool {
        AlphaInstanceBuffer(graphicsDevice, framesInFlight = maxFramesInFlight + 1)
    }
    private val frameInstanceBufferPool = GpuResourcePool {
        FrameInstanceBuffer(graphicsDevice, framesInFlight = maxFramesInFlight + 1)
    }

    /** Immutable read-only fallback vertex buffers shared by untinted and frame-less instanced draws. */
    private val sharedFallbacks = SharedInstanceFallbackBuffers(graphicsDevice)

    /**
     * Buffer reallocations across every UI mesh pool.
     *
     * Converges: each pooled mesh grows to the largest run it has carried and then stops. A count
     * that keeps climbing after warmup means content grows every frame, which is a defect --
     * `UiUploadCostBenchmark` asserts it stays put.
     */
    val uiMeshGrowthCount: Int
        get() {
            var total = 0
            listOf(uiTextureMeshPool, imagePassMeshPool).forEach { pool -> pool.forEach { total += it.growthCount } }
            total += overlayUiSlots.growthCount
            targetUiSlots.values.forEach { total += it.growthCount }
            return total
        }

    /** The UI meshes a pass into [target] stages into, or the overlay's for `null`. */
    fun uiSlotsFor(target: RenderTarget?): UiMeshSlots =
        if (target == null) overlayUiSlots else targetUiSlots.getOrPut(target) { UiMeshSlots(graphicsDevice, maxFramesInFlight) }

    /** Retires [target]'s UI meshes when it is destroyed; they are freed once no frame in flight can read them. */
    fun releaseUiSlots(target: RenderTarget) {
        targetUiSlots.remove(target)?.let(retiredUiSlots::retire)
    }

    /** UI mesh sets retired and not yet freed, for tests. */
    val retiredUiSlotCount: Int get() = retiredUiSlots.pending

    /** The GPU finished frame slot [frame]: frees retired UI meshes no frame in flight can still read. */
    fun frameSlotIdle(frame: Int) = retiredUiSlots.frameSlotIdle(frame)

    /** Whether a prepared batch must first wait for frames reused from another slot; see [ReusedFrameGuard]. */
    val reusedFrames = ReusedFrameGuard()

    /** The frame recorded into slot [frame] was submitted; see [RetiredResources]. */
    fun frameSubmitted(frame: Int) {
        retiredUiSlots.frameSubmitted(frame)
        reusedFrames.frameSubmitted()
    }

    /** One mesh per textured primitive, not one shared by all of them -- a run's later primitive
     * would otherwise overwrite geometry an earlier, not-yet-submitted draw still points at. */
    fun textureMeshForPrimitive(index: Int): DynamicMesh = uiTextureMeshPool[index]

    fun imagePassMeshForStep(index: Int): DynamicMesh = imagePassMeshPool[index]

    fun instanceBufferForRun(index: Int): InstanceBuffer = instanceBufferPool[index]

    /** Instance-buffer uploads so far, across the pool: for tests. */
    val instanceUploads: Long
        get() {
            var total = 0L
            instanceBufferPool.forEach { total += it.uploads }
            return total
        }

    fun skinnedInstanceBufferForRun(index: Int): SkinnedInstanceBuffer = skinnedInstanceBufferPool[index]
    fun alphaInstanceBufferForRun(index: Int): AlphaInstanceBuffer = alphaInstanceBufferPool[index]
    fun frameInstanceBufferForRun(index: Int): FrameInstanceBuffer = frameInstanceBufferPool[index]

    /**
     * Binding of the single shared all-white per-instance colour buffer, allocated lazily on first
     * untinted instanced draw so untinted runs never allocate per-run [AlphaInstanceBuffer]s.
     */
    val whiteInstanceColorBinding: VulkanBufferBinding
        get() = sharedFallbacks.whiteColorBinding()

    /**
     * Binding of the single shared all-zero per-instance frame buffer, allocated lazily when a
     * particle draw omits sprite-strip frame indices.
     */
    val zeroInstanceFrameBinding: VulkanBufferBinding
        get() = sharedFallbacks.zeroFrameBinding()

    internal val instanceBufferPoolSize: Int get() = instanceBufferPool.size
    internal val skinnedInstanceBufferPoolSize: Int get() = skinnedInstanceBufferPool.size
    internal val alphaInstanceBufferPoolSize: Int get() = alphaInstanceBufferPool.size
    internal val frameInstanceBufferPoolSize: Int get() = frameInstanceBufferPool.size
    internal val hasWhiteAlphaInstanceBuffer: Boolean get() = sharedFallbacks.hasWhiteColorBuffer

    /**
     * Drains retired UI mesh slots and releases pooled transient GPU buffers to reclaim high-water
     * memory on trim. Caller must have waited for device idle and cleared any draw caches
     * referencing the trimmed pools.
     */
    fun trimPools(trimUiMeshes: Boolean, trimInstanceBuffers: Boolean) {
        retiredUiSlots.destroyAll()
        if (trimUiMeshes) {
            overlayUiSlots.destroy()
            targetUiSlots.values.forEach(UiMeshSlots::destroy)
            targetUiSlots.clear()
            uiTextureMeshPool.forEach { it.destroy() }
            imagePassMeshPool.forEach { it.destroy() }
            uiTextureMeshPool.clear()
            imagePassMeshPool.clear()
        }
        if (trimInstanceBuffers) {
            instanceBufferPool.forEach { it.destroy() }
            skinnedInstanceBufferPool.forEach { it.destroy() }
            alphaInstanceBufferPool.forEach { it.destroy() }
            frameInstanceBufferPool.forEach { it.destroy() }
            instanceBufferPool.clear()
            skinnedInstanceBufferPool.clear()
            alphaInstanceBufferPool.clear()
            frameInstanceBufferPool.clear()
            sharedFallbacks.destroy()
        }
    }

    /** Total live pooled dynamic meshes and instance buffers currently held across all pools. */
    val liveBufferCount: Int
        get() {
            var total = overlayUiSlots.liveMeshCount +
                uiTextureMeshPool.size +
                imagePassMeshPool.size +
                instanceBufferPool.size +
                skinnedInstanceBufferPool.size +
                alphaInstanceBufferPool.size +
                frameInstanceBufferPool.size +
                sharedFallbacks.liveCount
            targetUiSlots.values.forEach { total += it.liveMeshCount }
            return total
        }

    fun destroy() {
        overlayUiSlots.destroy()
        targetUiSlots.values.forEach(UiMeshSlots::destroy)
        targetUiSlots.clear()
        retiredUiSlots.destroyAll()
        uiTextureMeshPool.forEach { it.destroy() }
        imagePassMeshPool.forEach { it.destroy() }
        instanceBufferPool.forEach { it.destroy() }
        skinnedInstanceBufferPool.forEach { it.destroy() }
        alphaInstanceBufferPool.forEach { it.destroy() }
        frameInstanceBufferPool.forEach { it.destroy() }
        sharedFallbacks.destroy()
        uiTextureMeshPool.clear()
        imagePassMeshPool.clear()
        instanceBufferPool.clear()
        skinnedInstanceBufferPool.clear()
        alphaInstanceBufferPool.clear()
        frameInstanceBufferPool.clear()
    }
}

private class SharedInstanceFallbackBuffers(private val graphicsDevice: GraphicsDevice) {
    private var whiteAlpha: AlphaInstanceBuffer? = null
    private var zeroFrame: FrameInstanceBuffer? = null

    val hasWhiteColorBuffer: Boolean get() = whiteAlpha != null
    val liveCount: Int get() = (if (whiteAlpha != null) 1 else 0) + (if (zeroFrame != null) 1 else 0)

    fun whiteColorBinding(): VulkanBufferBinding {
        val existing = whiteAlpha
        if (existing != null) return existing.binding(0)
        return AlphaInstanceBuffer(graphicsDevice, framesInFlight = 1).also {
            it.fillWhite(0)
            whiteAlpha = it
        }.binding(0)
    }

    fun zeroFrameBinding(): VulkanBufferBinding {
        val existing = zeroFrame
        if (existing != null) return existing.binding(0)
        return FrameInstanceBuffer(graphicsDevice, framesInFlight = 1).also {
            it.fillZero(0)
            zeroFrame = it
        }.binding(0)
    }

    fun destroy() {
        whiteAlpha?.destroy()
        whiteAlpha = null
        zeroFrame?.destroy()
        zeroFrame = null
    }
}
