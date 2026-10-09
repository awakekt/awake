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
import com.awakekt.awake.webgpu.pipeline.WebGpuBufferHandle
import com.awakekt.awake.webgpu.writeBufferData
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage

/**
 * The per-instance sprite-strip frame index behind one billboard-particle instanced draw call --
 * vertex buffer slot 3, alongside the mesh's own slot 0, [InstanceBuffer]'s model matrices at
 * slot 1, and [AlphaInstanceBuffer]'s color+alpha at slot 2 (see `RenderPipeline`'s
 * `instanceFrame` parameter). Mirrors [AlphaInstanceBuffer]'s shape with
 * `FLOATS_PER_INSTANCE = 1` (a lone `f32`, not a `vec4f`) and its own slot.
 */
class FrameInstanceBuffer(
    private val graphicsDevice: GraphicsDevice,
    private val maxInstances: Int = InstanceBuffer.DEFAULT_MAX_INSTANCES,
) {
    private val buffer: GPUBuffer = graphicsDevice.wgpuContext.device.createBuffer(
        BufferDescriptor(
            size = (maxInstances * FLOATS_PER_INSTANCE * Float.SIZE_BYTES).toULong(),
            usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
        ),
    )

    private val packer = InstancePacker<Float>(GpuDataShape.Float, "FrameInstanceBuffer") { out, offset, frame ->
        out[offset] = frame
    }

    /**
     * Uploads one sprite-strip frame index per instance for this frame, in the same order as the
     * instance matrices.
     *
     * An empty list uploads nothing and leaves the previous contents in place.
     *
     * @param frames Per-instance frame indices into the sprite strip, one entry per drawn instance.
     * @throws IllegalArgumentException If [frames] holds more entries than the buffer's instance
     * capacity.
     */
    fun update(frames: List<Float>) {
        val floats = packer.pack(frames, maxInstances) ?: return
        graphicsDevice.wgpuContext.device.queue.writeBufferData(buffer, 0uL, fastArrayBufferOf(floats))
    }

    /**
     * Returns the GPU vertex buffer bound at slot 3 for instanced billboard draws. The buffer is
     * allocated once at construction and never replaced.
     */
    fun bufferRef(): GPUBuffer = buffer

    /** This buffer as the shared render layer's opaque handle -- built once, not per draw. */
    val binding: WebGpuBufferHandle by lazy { WebGpuBufferHandle(buffer) }

    /** Closes the GPU buffer. Call once, after the last frame that draws with it. */
    fun destroy() {
        buffer.close()
    }

    /** Stride constant for the per-instance frame-index stream. */
    companion object {
        /** Floats per instance: 1, a lone `f32` frame index. */
        val FLOATS_PER_INSTANCE = GpuDataShape.Float.componentCount
    }
}
