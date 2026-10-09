/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.mesh

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.passes.InstancePacker
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.pipeline.WebGpuBufferHandle
import com.awakekt.awake.webgpu.writeBufferData
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage

/**
 * The per-instance RGBA color+alpha behind one billboard-particle instanced draw call --
 * vertex buffer slot 2, alongside the mesh's own slot 0 and [InstanceBuffer]'s model matrices
 * at slot 1 (see `RenderPipeline`'s `instanceAlpha` parameter). Mirrors [InstanceBuffer]'s
 * shape with `FLOATS_PER_INSTANCE = 4` (one `vec4f` per instance, not a generalization of
 * [InstanceBuffer]'s own `mat4` stride), its own slot -- not a generalization of [InstanceBuffer]
 * itself, since widening its stride would ripple into every other instanced format.
 */
class AlphaInstanceBuffer(
    private val graphicsDevice: GraphicsDevice,
    private val maxInstances: Int = InstanceBuffer.DEFAULT_MAX_INSTANCES,
) {
    private val buffer: GPUBuffer = graphicsDevice.wgpuContext.device.createBuffer(
        BufferDescriptor(
            size = (maxInstances * FLOATS_PER_INSTANCE * Float.SIZE_BYTES).toULong(),
            usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
        ),
    )

    private val packer = InstancePacker<Vec4>(GpuDataShape.Vec4, "AlphaInstanceBuffer") { out, offset, color ->
        out[offset] = color.x
        out[offset + 1] = color.y
        out[offset + 2] = color.z
        out[offset + 3] = color.w
    }

    /**
     * Uploads one RGBA tint per instance for this frame, in the same order as the instance
     * matrices.
     *
     * An empty list uploads nothing and leaves the previous contents in place.
     *
     * @param colors Per-instance colour and alpha, one entry per drawn instance.
     * @throws IllegalArgumentException If [colors] holds more entries than the buffer's instance
     * capacity.
     */
    fun update(colors: List<Vec4>) {
        val floats = packer.pack(colors, maxInstances) ?: return
        graphicsDevice.wgpuContext.device.queue.writeBufferData(buffer, 0uL, fastArrayBufferOf(floats))
    }

    /**
     * Returns the GPU vertex buffer bound at slot 2 for instanced billboard draws. The buffer is
     * allocated once at construction and never replaced.
     */
    fun bufferRef(): GPUBuffer = buffer

    /** This buffer as the shared render layer's opaque handle -- built once, not per draw. */
    val binding: WebGpuBufferHandle by lazy { WebGpuBufferHandle(buffer) }

    /** Closes the GPU buffer. Call once, after the last frame that draws with it. */
    fun destroy() {
        buffer.close()
    }

    /** Stride constant for the per-instance colour stream. */
    companion object {
        /** Derived from the shape, not hand-counted -- see [InstancePacker]. */
        val FLOATS_PER_INSTANCE = GpuDataShape.Vec4.componentCount
    }
}
