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
import com.awakekt.awake.webgpu.ui.DynamicMesh
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
// Sized for the largest block a primary-format draw can write: LitShadow when the renderer
// has a depth target, Primary otherwise. One size for the pool rather than two pools --
// a 104-float buffer holding a 24-float write is legal and costs 416 bytes per slot.
private val UNIFORM_SLOT_FLOAT_COUNT = MaterialUniformLayouts.LitShadow.total

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
