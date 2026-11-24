/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.passes.GpuResourcePool
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.ResourceBinding
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.mesh.AlphaInstanceBuffer
import com.awakekt.awake.webgpu.mesh.FrameInstanceBuffer
import com.awakekt.awake.webgpu.mesh.InstanceBuffer
import com.awakekt.awake.webgpu.mesh.SkinnedInstanceBuffer
import com.awakekt.awake.webgpu.pipeline.WebGpuBindGroupHandle
import com.awakekt.awake.webgpu.pipeline.WebGpuPipelineHandle
import com.awakekt.awake.webgpu.pipeline.createBindGroupReleasingLayout
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
    /** The overlay's UI meshes; each offscreen target has its own in [targetUiSlots]. */
    private val overlayUiSlots = UiMeshSlots(graphicsDevice)
    private val targetUiSlots = HashMap<RenderTarget, UiMeshSlots>()

    private val uiTextureMeshPool = GpuResourcePool {
        DynamicMesh(graphicsDevice, Renderer.TEXTURE_PRIMITIVE_QUADS, DynamicMesh.TEXTURE_FLOATS_PER_VERTEX)
    }
    private val imagePassMeshPool = GpuResourcePool {
        DynamicMesh(graphicsDevice, 1, DynamicMesh.IMAGE_PASS_FLOATS_PER_VERTEX)
    }

    /** Buffers the draw path created, by site: a warm frame creates none (#1555). */
    val creations = GpuBufferCreations()

    private val instanceBufferPool = GpuResourcePool {
        // Its first buffer is created in the constructor: count it here, then each growth.
        creations.instanceBuffers += 1
        InstanceBuffer(graphicsDevice).also { it.creations = creations }
    }
    private val skinnedInstanceBufferPool = GpuResourcePool { SkinnedInstanceBuffer(graphicsDevice) }
    private val alphaInstanceBufferPool = GpuResourcePool { AlphaInstanceBuffer(graphicsDevice) }
    private val frameInstanceBufferPool = GpuResourcePool { FrameInstanceBuffer(graphicsDevice) }

    /** Keyed by pipeline AND semantic: one pipeline can read both depth targets, and a single
     * key would let the second overwrite the first. */
    internal val shadowBindGroups =
        mutableMapOf<BindingSemantic, MutableMap<GPURenderPipeline, WebGpuBindGroupHandle>>()

    /** The group slot each cached depth group was built at, so a reallocated target rebuilds them. */
    internal val shadowBindGroupSlots = mutableMapOf<GPURenderPipeline, Int>()

    /** [shadowBindGroupSlots] for the mask's groups, which a resized mask target rebuilds (#1709). */
    internal val maskBindGroupSlots = mutableMapOf<GPURenderPipeline, Int>()

    /** [shadowBindGroupSlots] for the scene-depth target's groups, which a resized scene-depth target rebuilds (#1709). */
    internal val sceneDepthBindGroupSlots = mutableMapOf<GPURenderPipeline, Int>()
    private val uniformSlotPools = mutableMapOf<GPURenderPipeline, MutableList<UniformSlot>>()

    private val skinnedInstancedUniformResources = mutableMapOf<GPURenderPipeline, WebGpuUniformResources>()

    /** The UI meshes a pass into [target] stages into, or the overlay's for `null`. */
    fun uiSlotsFor(target: RenderTarget?): UiMeshSlots =
        if (target == null) overlayUiSlots else targetUiSlots.getOrPut(target) { UiMeshSlots(graphicsDevice) }

    /** Frees [target]'s UI meshes, when it is destroyed. */
    fun releaseUiSlots(target: RenderTarget) {
        targetUiSlots.remove(target)?.destroy()
    }

    /** One mesh per textured primitive, not one shared by all of them: queue.writeBuffer is
     * queue-scheduled rather than interleaved with encoding, so a run's later primitive writing a
     * shared mesh would land before an earlier, already-encoded draw reads it. */
    fun textureMeshForPrimitive(index: Int): DynamicMesh = uiTextureMeshPool[index]

    /** Pooled 1-quad dynamic mesh for an image-pass step at [index] within a frame. */
    fun imagePassMeshForStep(index: Int): DynamicMesh = imagePassMeshPool[index]

    fun instanceBufferForRun(index: Int): InstanceBuffer = instanceBufferPool[index]

    /** Instance-buffer uploads so far, across the pool: for tests. */
    val instanceUploads: Long get() {
        var total = 0L
        instanceBufferPool.forEach { total += it.uploads }
        return total
    }
    fun skinnedInstanceBufferForRun(index: Int): SkinnedInstanceBuffer = skinnedInstanceBufferPool[index]
    fun alphaInstanceBufferForRun(index: Int): AlphaInstanceBuffer = alphaInstanceBufferPool[index]
    fun frameInstanceBufferForRun(index: Int): FrameInstanceBuffer = frameInstanceBufferPool[index]

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
            creations.uniformSlots += 1
            val buffer = device.createBuffer(
                BufferDescriptor(
                    size = byteSize.toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                ),
            )
            val bindGroup = device.createBindGroupReleasingLayout(
                BindGroupDescriptor(
                    layout = pipeline.pipeline.getBindGroupLayout(0u),
                    entries = uniformEntries(pipeline.bindingsByGroup[0]?.entries, buffer),
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
     * Known limit: one buffer + bind group per concurrent draw. A single buffer with
     * `hasDynamicOffset` would need just one of each, but threading per-draw offsets through
     * the shared `CommandRecorder` port is a cross-backend change -- worth it only if draw
     * counts make the allocation count matter. */

    /** Returns a draw slot only when the selected pipeline declares group 0. The handle carries
     * the shared shader metadata, so callers cannot accidentally create binding 0 for a
     * resource-free pipeline whose explicit layout is `[]`. */

    /** Uniform slots pooled across every pipeline, each a buffer created once: for tests and probes. */
    internal val uniformSlotCount: Int get() = uniformSlotPools.values.sumOf { it.size }

    /** Calls [action] with the uniform buffer of slot [index] in each pipeline's pool that has one. */
    fun forEachUniformBufferAt(index: Int, action: (GPUBuffer) -> Unit) {
        for (slots in uniformSlotPools.values) if (index < slots.size) action(slots[index].buffer)
    }

    /** Draw [index]'s slot in [pipeline]'s pool. Wireframe-overlay [edges]' slots bind the edges' frame block. */
    fun uniformSlotForDraw(pipeline: WebGpuPipelineHandle, index: Int, edges: Boolean = false): UniformSlot? {
        if (!pipeline.hasBindingGroup(0)) return null
        val slots = uniformSlotPools.getOrPut(pipeline.pipeline) { mutableListOf() }
        while (slots.size <= index) {
            val device = graphicsDevice.wgpuContext.device
            val byteSize = maxOf(
                pipeline.uniformByteSize(0, 0),
                (MaterialUniformLayouts.LitShadow.total * Float.SIZE_BYTES).toLong(),
            )
            creations.uniformSlots += 1
            val buffer = device.createBuffer(
                BufferDescriptor(
                    size = byteSize.toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                ),
            )
            val group0Entries = pipeline.bindingsByGroup[0]?.entries ?: pipeline.materialBindings?.entries
            val buffersOnly = group0Entries == null || group0Entries.all { it.kind == ResourceKind.UniformBuffer }
            val bindGroup = if (buffersOnly) {
                WebGpuBindGroupHandle(
                    device.createBindGroupReleasingLayout(
                        BindGroupDescriptor(
                            layout = pipeline.pipeline.getBindGroupLayout(0u),
                            entries = uniformEntries(group0Entries, buffer, edges),
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

    /** Releases and evicts all buffers and bind groups cached against [pipeline]. */

    /** A group of uniform buffers only: the [draw]'s block, and the frame block where declared. */
    private fun uniformEntries(declared: List<ResourceBinding>?, draw: GPUBuffer, edges: Boolean = false): List<BindGroupEntry> =
        declared?.map { BindGroupEntry(binding = it.binding.toUInt(), resource = BufferBinding(buffer = graphicsDevice.uniformBufferAt(it.binding, draw, edges))) }
            ?: listOf(BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = draw)))

    fun invalidatePipeline(pipeline: GPURenderPipeline) {
        uniformSlotPools.remove(pipeline)?.forEach(UniformSlot::close)
        skinnedInstancedUniformResources.remove(pipeline)?.close()
        shadowBindGroups.values.forEach { groups -> groups.remove(pipeline)?.bindGroup?.close() }
        shadowBindGroupSlots.remove(pipeline)
        maskBindGroupSlots.remove(pipeline)
        sceneDepthBindGroupSlots.remove(pipeline)
    }

    /** Total live pooled dynamic meshes, instance buffers, and per-draw uniform buffers currently held. */
    val liveBufferCount: Int
        get() {
            var total = overlayUiSlots.liveMeshCount +
                uiTextureMeshPool.size +
                imagePassMeshPool.size +
                instanceBufferPool.size +
                skinnedInstanceBufferPool.size +
                alphaInstanceBufferPool.size +
                frameInstanceBufferPool.size +
                uniformSlotCount +
                skinnedInstancedUniformResources.size
            targetUiSlots.values.forEach { total += it.liveMeshCount }
            return total
        }

    fun destroy() {
        overlayUiSlots.destroy()
        targetUiSlots.values.forEach(UiMeshSlots::destroy)
        targetUiSlots.clear()
        uiTextureMeshPool.forEach { it.destroy() }
        imagePassMeshPool.forEach { it.destroy() }
        instanceBufferPool.forEach { it.destroy() }
        skinnedInstanceBufferPool.forEach { it.destroy() }
        alphaInstanceBufferPool.forEach { it.destroy() }
        frameInstanceBufferPool.forEach { it.destroy() }
        uiTextureMeshPool.clear()
        imagePassMeshPool.clear()
        instanceBufferPool.clear()
        skinnedInstanceBufferPool.clear()
        alphaInstanceBufferPool.clear()
        frameInstanceBufferPool.clear()
        uniformSlotPools.values.forEach { pool -> pool.forEach(UniformSlot::close) }
        uniformSlotPools.clear()
        skinnedInstancedUniformResources.values.forEach(WebGpuUniformResources::close)
        skinnedInstancedUniformResources.clear()
        shadowBindGroups.values.forEach { groups -> groups.values.forEach { it.bindGroup.close() } }
        shadowBindGroups.clear()
        shadowBindGroupSlots.clear()
        maskBindGroupSlots.clear()
        sceneDepthBindGroupSlots.clear()
    }
}

/** One pooled draw's uniform buffer and the bind group naming it. */
internal class UniformSlot(
    val buffer: GPUBuffer,
    val binding: WebGpuBindGroupHandle?,
) {
    /**
     * The block [buffer] holds, as last staged through [UniformUploads.stageChanged], or null
     * before the first. A draw whose block is unchanged is not uploaded again (#1452).
     */
    internal var resident: FloatArray? = null

    /** What [resident] was packed from, so a draw packed from the same is not packed again (#1452). */
    internal val inputs = com.awakekt.awake.render.passes.DrawUniformInputs()

    /** Closes the bind group, then the buffer it names. */
    fun close() {
        binding?.bindGroup?.close()
        buffer.close()
    }
}

internal class WebGpuUniformResources(
    val buffer: GPUBuffer,
    val bindGroup: GPUBindGroup,
    val binding: WebGpuBindGroupHandle,
) {
    /** Closes the bind group, then the buffer it names. */
    fun close() {
        bindGroup.close()
        buffer.close()
    }
}

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

/** [shadowBindingFor] for the mask target, read whole as an array. Cached under its own semantic. */
internal fun GpuBufferPoolManager.maskBindingFor(
    pipeline: WebGpuPipelineHandle,
    depthTarget: com.awakekt.awake.webgpu.texture.DepthTarget,
): MaterialBinding = depthBindingFor(pipeline, BindingSemantic.MaskDepth, depthTarget)

private fun GpuBufferPoolManager.depthBindingFor(
    pipeline: WebGpuPipelineHandle,
    semantic: BindingSemantic,
    depthTarget: com.awakekt.awake.webgpu.texture.DepthTarget,
): MaterialBinding = shadowBindGroups.getOrPut(semantic) { mutableMapOf() }.getOrPut(pipeline.pipeline) {
    val slot = pipeline.bindingLayout.slot(semantic)
    if (semantic == BindingSemantic.ShadowDepth) shadowBindGroupSlots[pipeline.pipeline] = slot
    if (semantic == BindingSemantic.MaskDepth) maskBindGroupSlots[pipeline.pipeline] = slot
    if (semantic == BindingSemantic.SceneDepth) sceneDepthBindGroupSlots[pipeline.pipeline] = slot
    WebGpuBindGroupHandle(depthBindGroup(pipeline.pipeline, slot, depthTarget))
}

/** [depthTarget]'s view and sampler as group [slot] of [pipeline]. */
internal fun GpuBufferPoolManager.depthBindGroup(
    pipeline: GPURenderPipeline,
    slot: Int,
    depthTarget: com.awakekt.awake.webgpu.texture.DepthTarget,
) = graphicsDevice.wgpuContext.device.createBindGroupReleasingLayout(
    BindGroupDescriptor(
        layout = pipeline.getBindGroupLayout(slot.toUInt()),
        entries = listOf(
            BindGroupEntry(binding = 0u, resource = depthTarget.depthView),
            BindGroupEntry(binding = 1u, resource = depthTarget.sampler),
        ),
    ),
)
