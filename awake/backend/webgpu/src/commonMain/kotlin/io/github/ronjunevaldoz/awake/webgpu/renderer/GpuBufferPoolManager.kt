// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.renderer

import io.github.ronjunevaldoz.awake.render.passes.uniforms.MaterialUniformLayouts
import io.github.ronjunevaldoz.awake.render.passes.GpuResourcePool
import io.github.ronjunevaldoz.awake.webgpu.device.GraphicsDevice
import io.github.ronjunevaldoz.awake.webgpu.mesh.AlphaInstanceBuffer
import io.github.ronjunevaldoz.awake.webgpu.mesh.FrameInstanceBuffer
import io.github.ronjunevaldoz.awake.webgpu.mesh.InstanceBuffer
import io.github.ronjunevaldoz.awake.webgpu.mesh.SkinnedInstanceBuffer
import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuBindGroupHandle
import io.github.ronjunevaldoz.awake.webgpu.ui.DynamicMesh
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPURenderPipeline

/**
 * Manages reusable, grow-on-demand dynamic mesh and instance buffer pools across frames in WebGPU.
 */
internal class GpuBufferPoolManager(
    private val graphicsDevice: GraphicsDevice,
) {
    // Each pool is (policy, factory): GpuResourcePool owns "grow on demand, index by run,
    // never shrink" once, this file names only what to build. Mirrors Vulkan's identical eight;
    // only the constructed type differs, and these are single-buffered where Vulkan's take a
    // frames-in-flight count.
    private val uiQuadMeshPool = GpuResourcePool { DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS) }
    private val uiGlyphMeshPool = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, DynamicMesh.GLYPH_FLOATS_PER_VERTEX)
    }
    private val uiRoundedQuadMeshPool = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, DynamicMesh.ROUNDED_QUAD_FLOATS_PER_VERTEX)
    }
    private val uiTextureMeshPool = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.MAX_UI_QUADS, DynamicMesh.GLYPH_FLOATS_PER_VERTEX)
    }

    private val instanceBufferPool = GpuResourcePool { InstanceBuffer(graphicsDevice) }
    private val skinnedInstanceBufferPool = GpuResourcePool { SkinnedInstanceBuffer(graphicsDevice) }
    private val alphaInstanceBufferPool = GpuResourcePool { AlphaInstanceBuffer(graphicsDevice) }
    private val frameInstanceBufferPool = GpuResourcePool { FrameInstanceBuffer(graphicsDevice) }
    private val uniformSlotPools = mutableMapOf<GPURenderPipeline, MutableList<UniformSlot>>()

    fun quadMeshForRun(index: Int): DynamicMesh = uiQuadMeshPool[index]
    fun roundedQuadMeshForRun(index: Int): DynamicMesh = uiRoundedQuadMeshPool[index]
    fun glyphMeshForRun(index: Int): DynamicMesh = uiGlyphMeshPool[index]

    /** One mesh per textured primitive, not one shared by all of them: queue.writeBuffer is
     * queue-scheduled rather than interleaved with encoding, so a run's later primitive writing a
     * shared mesh would land before an earlier, already-encoded draw reads it. */
    fun textureMeshForPrimitive(index: Int): DynamicMesh = uiTextureMeshPool[index]

    fun instanceBufferForRun(index: Int): InstanceBuffer = instanceBufferPool[index]
    fun skinnedInstanceBufferForRun(index: Int): SkinnedInstanceBuffer = skinnedInstanceBufferPool[index]
    fun alphaInstanceBufferForRun(index: Int): AlphaInstanceBuffer = alphaInstanceBufferPool[index]
    fun frameInstanceBufferForRun(index: Int): FrameInstanceBuffer = frameInstanceBufferPool[index]

    /** One draw's own uniform buffer + bind group, so draws sharing a pipeline don't overwrite
     * each other's MVP -- the WebGPU counterpart to Vulkan's per-draw uniform slots
     * (`Material.uniformSlot(frameIndex, drawSlotIndex)`).
     *
     * Pooled per [pipeline], not globally: WebGPU's "auto" layout mode derives a fresh
     * `GPUBindGroupLayout` per `createRenderPipeline` call, and a bind group is only valid
     * against the layout it was built from.
     *
     * ponytail: one buffer + bind group per concurrent draw. A single buffer with
     * `hasDynamicOffset` would need just one of each, but threading per-draw offsets through
     * the shared `CommandRecorder` port is a cross-backend change -- worth it only if draw
     * counts make the allocation count matter. */
    fun uniformSlotForDraw(pipeline: GPURenderPipeline, index: Int): UniformSlot {
        val slots = uniformSlotPools.getOrPut(pipeline) { mutableListOf() }
        while (slots.size <= index) {
            val device = graphicsDevice.wgpuContext.device
            val buffer = device.createBuffer(
                BufferDescriptor(
                    size = (UNIFORM_SLOT_FLOAT_COUNT * Float.SIZE_BYTES).toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                ),
            )
            val bindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    layout = pipeline.getBindGroupLayout(0u),
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = buffer)),
                    ),
                ),
            )
            slots += UniformSlot(buffer, WebGpuBindGroupHandle(bindGroup))
        }
        return slots[index]
    }

    fun destroy() {
        uiQuadMeshPool.forEach { it.destroy() }
        uiGlyphMeshPool.forEach { it.destroy() }
        uiRoundedQuadMeshPool.forEach { it.destroy() }
        uiTextureMeshPool.forEach { it.destroy() }
        instanceBufferPool.forEach { it.destroy() }
        skinnedInstanceBufferPool.forEach { it.destroy() }
        alphaInstanceBufferPool.forEach { it.destroy() }
        frameInstanceBufferPool.forEach { it.destroy() }
        uniformSlotPools.values.forEach { slots -> slots.forEach { it.buffer.close() } }
        uniformSlotPools.clear()
    }
}

/** One pooled draw's uniform buffer and the bind group naming it. */
internal class UniformSlot(
    val buffer: GPUBuffer,
    val binding: WebGpuBindGroupHandle,
)

/** What the primary pipeline's shader reads. Derived from the layout rather than mirroring
 * `RendererUiPipelines.kt`'s constant, which is how the two drifted apart in the first place. */
private val UNIFORM_SLOT_FLOAT_COUNT = MaterialUniformLayouts.Primary.total
