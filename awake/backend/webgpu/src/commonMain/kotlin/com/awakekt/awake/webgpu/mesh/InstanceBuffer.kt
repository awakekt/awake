/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.mesh

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.passes.InstancePacker
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.writeBufferData
import com.awakekt.awake.webgpu.pipeline.WebGpuBufferHandle
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage

/**
 * The per-instance model matrices behind one instanced draw call -- bound as vertex buffer slot
 * 1, alongside the mesh's own slot 0 (see `RenderPipeline`'s `instanced` parameter, which
 * declares the matching `GPUVertexStepMode.Instance` layout). Mirrors Vulkan's `InstanceBuffer`;
 * thinner for the same reason this backend's `ui.DynamicMesh` is thinner than Vulkan's --
 * `queue.writeBuffer` is already safe to call every frame with no mapping ceremony.
 *
 * Rewritten wholesale every frame -- unlike this module's `Mesh`, which sizes itself to the exact
 * array it was constructed with and never updates. Grows to the largest set it has held, in powers
 * of two, rather than starting at [maxInstances]: a frame of batched props has one of these per
 * instanced draw, mostly small.
 */
class InstanceBuffer(
    private val graphicsDevice: GraphicsDevice,
    /** Hard ceiling on instances per draw call -- [update] fails loudly (rather than silently
     * truncating) with a message naming this knob. */
    private val maxInstances: Int = DEFAULT_MAX_INSTANCES,
) {
    private var capacity = MIN_CAPACITY.coerceAtMost(maxInstances)
    private var buffer: GPUBuffer = createBuffer(capacity)

    // Reused across frames so a steady instance count allocates nothing per frame.

    /** Packs [models] into this buffer. `Mat4.data` is already column-major, which is the order
     * `instanced.wgsl`'s 4 `vec4` attributes reassemble into a `mat4x4` -- a straight copy, no
     * transpose. */
    private val packer = InstancePacker<Mat4>(GpuDataShape.Mat4, "InstanceBuffer") { out, offset, model ->
        model.data.copyInto(out, offset)
    }

    fun update(models: List<Mat4>) {
        val floats = packer.pack(models, maxInstances) ?: return
        if (models.size > capacity) {
            while (capacity < models.size) capacity *= 2
            capacity = capacity.coerceAtMost(maxInstances)
            // Work already submitted keeps the old buffer alive until it finishes.
            buffer.close()
            buffer = createBuffer(capacity)
            binding = WebGpuBufferHandle(buffer)
        }
        graphicsDevice.wgpuContext.device.queue.writeBufferData(buffer, 0uL, fastArrayBufferOf(floats))
    }

    fun bufferRef(): GPUBuffer = buffer

    /** This buffer as the shared render layer's opaque handle -- built once per size, not per draw. */
    var binding: WebGpuBufferHandle = WebGpuBufferHandle(buffer)
        private set

    private fun createBuffer(instances: Int): GPUBuffer = graphicsDevice.wgpuContext.device.createBuffer(
        BufferDescriptor(
            size = (instances * FLOATS_PER_INSTANCE * Float.SIZE_BYTES).toULong(),
            usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
        ),
    )

    fun destroy() {
        buffer.close()
    }

    companion object {
        /** One `mat4` per instance. */
        val FLOATS_PER_INSTANCE = GpuDataShape.Mat4.componentCount

        /** Same 4096 (256 KB) default as Vulkan's `InstanceBuffer` -- see its doc comment. */
        const val DEFAULT_MAX_INSTANCES = 4096

        private const val MIN_CAPACITY = 16
    }
}
