/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.mesh

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.passes.InstancePacker
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.pipeline.WebGpuBindGroupHandle
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPURenderPipeline

/**
 * The per-instance JOINT PALETTES behind one animated instanced draw call -- [InstanceBuffer]'s
 * animated companion, mirroring Vulkan's `SkinnedInstanceBuffer` (see its doc comment for why
 * this is a storage buffer rather than a vertex attribute or a uniform array). Read by
 * `skinned_instanced.wgsl` as `array<JointPalette>` indexed by `@builtin(instance_index)`.
 *
 * Bound as bind group 1 (`@group(1)`), not group 0: group 0 holds the shared
 * `viewProjection` + light uniform, which every animated instanced draw call can share, while
 * this buffer is per draw call.
 */
class SkinnedInstanceBuffer(
    private val graphicsDevice: GraphicsDevice,
    /** Hard ceiling on animated instances per draw call. Each one costs [FLOATS_PER_INSTANCE]
     * floats = 4 KB, 16x what a static instance costs in [InstanceBuffer] -- raising this is a
     * real memory decision. [update] fails loudly naming it rather than silently truncating. */
    private val maxInstances: Int = DEFAULT_MAX_INSTANCES,
) {
    private val buffer: GPUBuffer = graphicsDevice.wgpuContext.device.createBuffer(
        BufferDescriptor(
            size = (maxInstances.toLong() * FLOATS_PER_INSTANCE * Float.SIZE_BYTES).toULong(),
            usage = GPUBufferUsage.Storage or GPUBufferUsage.CopyDst,
        ),
    )

    // Cached per pipeline object for the same reason Renderer's uniform bind groups are: an
    // "auto"-layout bind group is only valid against the exact pipeline layout it came from.
    private var bindGroup: GPUBindGroup? = null
    private var bindGroupPipeline: GPURenderPipeline? = null

    // Reused across frames so a steady instance count allocates nothing per frame.

    /** Packs [palettes] at a fixed [FLOATS_PER_INSTANCE] stride -- see Vulkan's
     * `SkinnedInstanceBuffer.update` for why a shorter palette is fine. */
    private val packer = InstancePacker<FloatArray>(
        GpuDataShape.Mat4,
        "SkinnedInstanceBuffer",
        repeat = MAX_JOINTS,
    ) { out, offset, palette ->
        require(palette.size <= MAX_JOINTS * GpuDataShape.Mat4.componentCount) {
            "Joint palette ${palette.size} floats exceeds MAX_JOINTS ($MAX_JOINTS) * 16."
        }
        palette.copyInto(out, offset)
    }

    fun update(palettes: List<FloatArray>) {
        val floats = packer.pack(palettes, maxInstances) ?: return
        graphicsDevice.wgpuContext.device.queue.writeBuffer(buffer, 0uL, fastArrayBufferOf(floats))
    }

    /** This buffer as [pipeline]'s group-1 bind group, built once per pipeline object. */
    fun bindGroupFor(pipeline: GPURenderPipeline): GPUBindGroup {
        val cached = bindGroup
        if (cached != null && bindGroupPipeline === pipeline) return cached
        val created = graphicsDevice.wgpuContext.device.createBindGroup(
            BindGroupDescriptor(
                layout = pipeline.getBindGroupLayout(PALETTE_GROUP),
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = buffer)),
                ),
            ),
        )
        bindGroup = created
        bindGroupPipeline = pipeline
        bindGroupHandle = null
        return created
    }

    /** [bindGroupFor] as the shared render layer's opaque handle, cached the same way. */
    fun bindingFor(pipeline: GPURenderPipeline): WebGpuBindGroupHandle {
        val group = bindGroupFor(pipeline)
        return bindGroupHandle ?: WebGpuBindGroupHandle(group).also { bindGroupHandle = it }
    }

    private var bindGroupHandle: WebGpuBindGroupHandle? = null

    fun destroy() {
        buffer.close()
    }

    companion object {
        /** Alias of the engine-wide constant that also sizes the WGSL palette arrays. */
        const val MAX_JOINTS = com.awakekt.awake.render.renderer.MAX_JOINTS

        /** One fixed-size `JointPalette` struct: 64 `mat4` = 1024 floats = 4 KB per instance. */
        val FLOATS_PER_INSTANCE = MAX_JOINTS * GpuDataShape.Mat4.componentCount

        /** Same 256 (1 MB) default as Vulkan's `SkinnedInstanceBuffer` -- see its doc comment. */
        const val DEFAULT_MAX_INSTANCES = 256

        /** `@group(1)` -- see this class's own doc comment. */
        const val PALETTE_GROUP = 1u
    }
}
