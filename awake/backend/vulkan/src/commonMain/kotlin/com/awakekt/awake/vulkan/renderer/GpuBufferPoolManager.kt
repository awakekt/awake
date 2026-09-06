/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.passes.GpuResourcePool
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.mesh.AlphaInstanceBuffer
import com.awakekt.awake.vulkan.mesh.FrameInstanceBuffer
import com.awakekt.awake.vulkan.mesh.InstanceBuffer
import com.awakekt.awake.vulkan.mesh.SkinnedInstanceBuffer
import com.awakekt.awake.vulkan.ui.DynamicMesh

/**
 * Manages reusable, grow-on-demand dynamic mesh and instance buffer pools across frames.
 */
internal class GpuBufferPoolManager(
    private val graphicsDevice: GraphicsDevice,
    private val maxFramesInFlight: Int,
) {
    // Each pool is (policy, factory): GpuResourcePool owns "grow on demand, index by run,
    // never shrink" once, this file names only what to build. Both backends declare the same
    // eight; only the constructed type differs.
    private val uiQuadMeshPool = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, framesInFlight = maxFramesInFlight)
    }
    private val uiGlyphMeshPool = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, DynamicMesh.GLYPH_FLOATS_PER_VERTEX, maxFramesInFlight)
    }
    private val uiRoundedQuadMeshPool = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, DynamicMesh.ROUNDED_QUAD_FLOATS_PER_VERTEX, maxFramesInFlight)
    }
    private val uiTextureMeshPool = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, DynamicMesh.GLYPH_FLOATS_PER_VERTEX, maxFramesInFlight)
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
            listOf(uiQuadMeshPool, uiGlyphMeshPool, uiRoundedQuadMeshPool, uiTextureMeshPool)
                .forEach { pool -> pool.forEach { total += it.growthCount } }
            return total
        }

    fun quadMeshForRun(index: Int): DynamicMesh = uiQuadMeshPool[index]
    fun roundedQuadMeshForRun(index: Int): DynamicMesh = uiRoundedQuadMeshPool[index]
    fun glyphMeshForRun(index: Int): DynamicMesh = uiGlyphMeshPool[index]

    /** One mesh per textured primitive, not one shared by all of them -- a run's later primitive
     * would otherwise overwrite geometry an earlier, not-yet-submitted draw still points at. */
    fun textureMeshForPrimitive(index: Int): DynamicMesh = uiTextureMeshPool[index]

    fun instanceBufferForRun(index: Int): InstanceBuffer = instanceBufferPool[index]
    fun skinnedInstanceBufferForRun(index: Int): SkinnedInstanceBuffer = skinnedInstanceBufferPool[index]
    fun alphaInstanceBufferForRun(index: Int): AlphaInstanceBuffer = alphaInstanceBufferPool[index]
    fun frameInstanceBufferForRun(index: Int): FrameInstanceBuffer = frameInstanceBufferPool[index]

    fun destroy() {
        uiQuadMeshPool.forEach { it.destroy() }
        uiGlyphMeshPool.forEach { it.destroy() }
        uiRoundedQuadMeshPool.forEach { it.destroy() }
        uiTextureMeshPool.forEach { it.destroy() }
        instanceBufferPool.forEach { it.destroy() }
        skinnedInstanceBufferPool.forEach { it.destroy() }
        alphaInstanceBufferPool.forEach { it.destroy() }
        frameInstanceBufferPool.forEach { it.destroy() }
    }
}
