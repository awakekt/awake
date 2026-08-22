// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.mesh

import io.github.ronjunevaldoz.awake.render.passes.InstancePacker
import io.github.ronjunevaldoz.awake.core.geometry.GpuDataShape
import io.github.ronjunevaldoz.awake.webgpu.device.GraphicsDevice
import io.github.ronjunevaldoz.awake.webgpu.fastArrayBufferOf
import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuBufferHandle
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

    fun update(frames: List<Float>) {
        val floats = packer.pack(frames, maxInstances) ?: return
        graphicsDevice.wgpuContext.device.queue.writeBuffer(buffer, 0uL, fastArrayBufferOf(floats))
    }

    fun bufferRef(): GPUBuffer = buffer

    /** This buffer as the shared render layer's opaque handle -- built once, not per draw. */
    val binding: WebGpuBufferHandle by lazy { WebGpuBufferHandle(buffer) }

    fun destroy() {
        buffer.close()
    }

    companion object {
        val FLOATS_PER_INSTANCE = GpuDataShape.Float.componentCount
    }
}
