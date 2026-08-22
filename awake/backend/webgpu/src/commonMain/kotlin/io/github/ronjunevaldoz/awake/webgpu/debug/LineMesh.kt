// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.debug

import io.github.ronjunevaldoz.awake.render.passes.debug.DebugLineLayout
import io.github.ronjunevaldoz.awake.webgpu.device.GraphicsDevice
import io.github.ronjunevaldoz.awake.webgpu.fastArrayBufferOf
import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuBufferHandle
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage

/**
 * A world-space `LINE_LIST`-equivalent (`GPUPrimitiveTopology.LineList`) vertex buffer
 * rewritten every frame -- mirrors Vulkan's `debug.LineMesh` (see that class's doc comment),
 * but no index buffer, same reason: each consecutive vertex pair already is one line
 * segment. WebGPU's `queue.writeBuffer` is already safe per-frame with no staging needed
 * (see `ui.DynamicMesh`'s identical rationale for this backend).
 */
class LineMesh(
    private val graphicsDevice: GraphicsDevice,
    private val maxLines: Int,
) {
    private val maxVertices = maxLines * VERTICES_PER_LINE
    private val vertexBuffer: GPUBuffer

    val vertexBinding: WebGpuBufferHandle get() = WebGpuBufferHandle(vertexBuffer)
    val vertexCount: Int get() = drawVertexCount

    /** How many vertices this frame's [update] actually wrote -- [draw] only draws this many. */
    var drawVertexCount: Int = 0
        private set

    init {
        val device = graphicsDevice.wgpuContext.device
        vertexBuffer = device.createBuffer(
            BufferDescriptor(
                size = (maxVertices.toLong() * FLOATS_PER_VERTEX.toLong() * 4L).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
            ),
        )
    }

    fun update(vertices: FloatArray) {
        require(vertices.size <= maxVertices * FLOATS_PER_VERTEX) {
            "Debug line vertex count exceeds LineMesh capacity ($maxLines lines = $maxVertices vertices) -- " +
                "raise maxLines or reduce lines drawn this frame."
        }
        val device = graphicsDevice.wgpuContext.device
        device.queue.writeBuffer(vertexBuffer, 0uL, fastArrayBufferOf(vertices))
        drawVertexCount = vertices.size / FLOATS_PER_VERTEX
    }

    fun vertexBufferRef(): GPUBuffer = vertexBuffer

    fun destroy() {
        vertexBuffer.close()
    }

    companion object {
        val FLOATS_PER_VERTEX = DebugLineLayout.FLOATS_PER_VERTEX
        val VERTICES_PER_LINE = DebugLineLayout.VERTICES_PER_LINE
    }
}
