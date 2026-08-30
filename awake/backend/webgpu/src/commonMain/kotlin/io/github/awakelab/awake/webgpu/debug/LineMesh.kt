/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu.debug

import io.github.awakelab.awake.render.passes.debug.DebugLineLayout
import io.github.awakelab.awake.webgpu.device.GraphicsDevice
import io.github.awakelab.awake.webgpu.fastArrayBufferOf
import io.github.awakelab.awake.webgpu.pipeline.WebGpuBufferHandle
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
    initialLines: Int = DebugLineLayout.INITIAL_LINES,
) {
    private var capacityVertices = initialLines * VERTICES_PER_LINE
    private var vertexBuffer: GPUBuffer

    val vertexBinding: WebGpuBufferHandle get() = WebGpuBufferHandle(vertexBuffer)
    val vertexCount: Int get() = drawVertexCount

    /** How many vertices this frame's [update] actually wrote -- [draw] only draws this many. */
    var drawVertexCount: Int = 0
        private set

    init {
        require(initialLines > 0) { "initialLines must be positive; was $initialLines." }
        vertexBuffer = allocate(capacityVertices)
    }

    /** Current buffer capacity in whole lines. Grows; never shrinks. */
    val capacityLines: Int get() = capacityVertices / VERTICES_PER_LINE

    private fun allocate(vertices: Int): GPUBuffer = graphicsDevice.wgpuContext.device.createBuffer(
        BufferDescriptor(
            size = (vertices.toLong() * FLOATS_PER_VERTEX.toLong() * 4L).toULong(),
            usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
        ),
    )

    /**
     * Replaces the buffer with one large enough for [neededVertices], by the shared rule in
     * [DebugLineLayout.grownVertexCapacity] rather than a second copy of it here.
     *
     * `writeBuffer` is queue-ordered against the frames already submitted, so closing the old
     * buffer after the swap does not race work in flight the way a mapped Vulkan allocation would.
     */
    private fun growTo(neededVertices: Int) {
        val capacity = DebugLineLayout.grownVertexCapacity(capacityVertices, neededVertices)
        val previous = vertexBuffer
        vertexBuffer = allocate(capacity)
        capacityVertices = capacity
        previous.close()
    }

    fun update(vertices: FloatArray) {
        val neededVertices = vertices.size / FLOATS_PER_VERTEX
        if (neededVertices > capacityVertices) growTo(neededVertices)
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
