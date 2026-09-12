/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.passes.GpuResourcePool
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.mesh.AlphaInstanceBuffer
import com.awakekt.awake.webgpu.mesh.FrameInstanceBuffer
import com.awakekt.awake.webgpu.mesh.InstanceBuffer
import com.awakekt.awake.webgpu.mesh.SkinnedInstanceBuffer
import com.awakekt.awake.webgpu.pipeline.WebGpuBindGroupHandle
import com.awakekt.awake.webgpu.pipeline.WebGpuPipelineHandle
import com.awakekt.awake.webgpu.pipeline.hasBindingGroup
import com.awakekt.awake.webgpu.pipeline.uniformByteSize
import com.awakekt.awake.webgpu.ui.DynamicMesh
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPURenderPipeline

/**
 * Manages reusable, grow-on-demand dynamic mesh and instance buffer pools across frames in WebGPU.
 */
internal class GpuBufferPoolManager(
    internal val graphicsDevice: GraphicsDevice,
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

    /** Keyed by pipeline AND semantic: one pipeline can read both depth targets, and a single
     * key would let the second overwrite the first. */
    internal val shadowBindGroups =
        mutableMapOf<Pair<GPURenderPipeline, BindingSemantic>, MaterialBinding>()
    private val uniformSlotPools = mutableMapOf<GPURenderPipeline, MutableList<UniformSlot>>()

    private val instancedUniformResources = mutableMapOf<GPURenderPipeline, WebGpuUniformResources>()
    private val skinnedInstancedUniformResources = mutableMapOf<GPURenderPipeline, WebGpuUniformResources>()

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

    fun instancedUniformResources(pipeline: WebGpuPipelineHandle): WebGpuUniformResources {
        check(pipeline.hasBindingGroup(0)) {
            "Instanced pipeline must declare group 0 before allocating its uniform resources."
        }
        return instancedUniformResources.getOrPut(pipeline.pipeline) {
            val byteSize = maxOf(
                pipeline.uniformByteSize(0, 0),
                (MaterialUniformLayouts.LitShadow.total * Float.SIZE_BYTES).toLong(),
            )
            val device = graphicsDevice.wgpuContext.device
            val buffer = device.createBuffer(
                BufferDescriptor(
                    size = byteSize.toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                ),
            )
            val bindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    layout = pipeline.pipeline.getBindGroupLayout(0u),
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = buffer)),
                    ),
                ),
            )
            WebGpuUniformResources(buffer, bindGroup, WebGpuBindGroupHandle(bindGroup))
        }
    }

    fun skinnedInstancedUniformResources(pipeline: WebGpuPipelineHandle): WebGpuUniformResources {
        check(pipeline.hasBindingGroup(0)) {
            "Skinned instanced pipeline must declare group 0 before allocating its uniform resources."
        }
        return skinnedInstancedUniformResources.getOrPut(pipeline.pipeline) {
            val byteSize = maxOf(
                pipeline.uniformByteSize(0, 0),
                (MaterialUniformLayouts.LitShadow.total * Float.SIZE_BYTES).toLong(),
            )
            val device = graphicsDevice.wgpuContext.device
            val buffer = device.createBuffer(
                BufferDescriptor(
                    size = byteSize.toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                ),
            )
            val bindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    layout = pipeline.pipeline.getBindGroupLayout(0u),
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = buffer)),
                    ),
                ),
            )
            WebGpuUniformResources(buffer, bindGroup, WebGpuBindGroupHandle(bindGroup))
        }
    }

    /** One draw's own uniform buffer + bind group, so draws sharing a pipeline don't overwrite
     * each other's MVP -- the WebGPU counterpart to Vulkan's per-draw uniform slots
     * (`Material.uniformSlot(frameIndex, drawSlotIndex)`).
     *
     * Pooled per [pipeline], not globally: each render pipeline owns its declared
     * `GPUBindGroupLayout`, and a bind group is only valid against the layout it was built from.
     *
     * ponytail: one buffer + bind group per concurrent draw. A single buffer with
     * `hasDynamicOffset` would need just one of each, but threading per-draw offsets through
     * the shared `CommandRecorder` port is a cross-backend change -- worth it only if draw
     * counts make the allocation count matter. */
    /** Returns a draw slot only when the selected pipeline declares group 0. The handle carries
     * the shared shader metadata, so callers cannot accidentally create binding 0 for a
     * resource-free pipeline whose explicit layout is `[]`. */
    fun uniformSlotForDraw(pipeline: WebGpuPipelineHandle, index: Int): UniformSlot? {
        if (!pipeline.hasBindingGroup(0)) return null
        val slots = uniformSlotPools.getOrPut(pipeline.pipeline) { mutableListOf() }
        while (slots.size <= index) {
            val device = graphicsDevice.wgpuContext.device
            val byteSize = maxOf(
                pipeline.uniformByteSize(0, 0),
                (MaterialUniformLayouts.LitShadow.total * Float.SIZE_BYTES).toLong(),
            )
            val buffer = device.createBuffer(
                BufferDescriptor(
                    size = byteSize.toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                ),
            )
            val group0Entries = pipeline.bindingsByGroup[0]?.entries ?: pipeline.materialBindings?.entries
            val singleBufferOnly = group0Entries == null || (group0Entries.size == 1 && group0Entries[0].kind == com.awakekt.awake.render.pipeline.ResourceKind.UniformBuffer)
            val bindGroup = if (singleBufferOnly) {
                WebGpuBindGroupHandle(
                    device.createBindGroup(
                        BindGroupDescriptor(
                            layout = pipeline.pipeline.getBindGroupLayout(0u),
                            entries = listOf(
                                BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = buffer)),
                            ),
                        ),
                    ),
                )
            } else {
                null
            }
            slots += UniformSlot(buffer, bindGroup)
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
        uniformSlotPools.values.forEach { pool ->
            pool.forEach { it.buffer.close() }
        }
        instancedUniformResources.values.forEach { it.buffer.close() }
        instancedUniformResources.clear()
        skinnedInstancedUniformResources.values.forEach { it.buffer.close() }
        skinnedInstancedUniformResources.clear()
    }
}

/** One pooled draw's uniform buffer and the bind group naming it. */
internal class UniformSlot(
    val buffer: GPUBuffer,
    val binding: WebGpuBindGroupHandle?,
)

internal class WebGpuUniformResources(
    val buffer: GPUBuffer,
    val bindGroup: GPUBindGroup,
    val binding: WebGpuBindGroupHandle,
)

/** The depth target's own bind group for [pipeline], created once per pipeline: the group is
 * immutable and the target is shared by every shadowed draw in the frame. An extension rather
 * than a member only to keep the pool class under the per-class function budget. */
internal fun GpuBufferPoolManager.shadowBindingFor(
    pipeline: WebGpuPipelineHandle,
    depthTarget: com.awakekt.awake.webgpu.texture.DepthTarget,
): MaterialBinding = depthBindingFor(pipeline, BindingSemantic.ShadowDepth, depthTarget)

/** [shadowBindingFor] for the camera-space depth target. Cached under its own semantic, so a
 * pipeline reading both gets two groups rather than one overwriting the other. */
internal fun GpuBufferPoolManager.sceneDepthBindingFor(
    pipeline: WebGpuPipelineHandle,
    depthTarget: com.awakekt.awake.webgpu.texture.DepthTarget,
): MaterialBinding = depthBindingFor(pipeline, BindingSemantic.SceneDepth, depthTarget)

private fun GpuBufferPoolManager.depthBindingFor(
    pipeline: WebGpuPipelineHandle,
    semantic: BindingSemantic,
    depthTarget: com.awakekt.awake.webgpu.texture.DepthTarget,
): MaterialBinding = shadowBindGroups.getOrPut(pipeline.pipeline to semantic) {
    val device = graphicsDevice.wgpuContext.device
    WebGpuBindGroupHandle(
        device.createBindGroup(
            BindGroupDescriptor(
                layout = pipeline.pipeline.getBindGroupLayout(
                    pipeline.bindingLayout.slot(semantic).toUInt(),
                ),
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = depthTarget.depthView),
                    BindGroupEntry(binding = 1u, resource = depthTarget.sampler),
                ),
            ),
        ),
    )
}
