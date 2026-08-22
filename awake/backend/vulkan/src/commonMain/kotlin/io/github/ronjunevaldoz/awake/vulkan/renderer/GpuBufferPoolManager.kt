// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.vulkan.renderer

import io.github.ronjunevaldoz.awake.render.passes.GpuResourcePool
import io.github.ronjunevaldoz.awake.vulkan.device.GraphicsDevice
import io.github.ronjunevaldoz.awake.vulkan.mesh.AlphaInstanceBuffer
import io.github.ronjunevaldoz.awake.vulkan.mesh.FrameInstanceBuffer
import io.github.ronjunevaldoz.awake.vulkan.mesh.InstanceBuffer
import io.github.ronjunevaldoz.awake.vulkan.mesh.SkinnedInstanceBuffer
import io.github.ronjunevaldoz.awake.vulkan.ui.DynamicMesh

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
