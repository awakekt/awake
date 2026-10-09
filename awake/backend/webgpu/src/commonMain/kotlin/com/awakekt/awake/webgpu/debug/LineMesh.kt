/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.debug

import com.awakekt.awake.render.passes.debug.DebugLineLayout
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.pipeline.WebGpuBufferHandle
import com.awakekt.awake.webgpu.writeBufferData
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

    /**
     * This mesh's vertex buffer as the shared render layer's opaque buffer handle. A new wrapper is
     * created on every read, and the buffer behind it is replaced when [update] grows the mesh, so
     * read it after [update] instead of caching it across frames.
     */
    val vertexBinding: WebGpuBufferHandle get() = WebGpuBufferHandle(vertexBuffer)

    /**
     * Number of vertices the last [update] wrote, which is how many the line pass draws; the same
     * value as [drawVertexCount].
     */
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

    /**
     * Replaces this frame's line list with [vertices], growing the buffer first when it is too
     * small.
     *
     * Growth follows [DebugLineLayout.grownVertexCapacity], never shrinks, and swaps in a new
     * buffer, so a [vertexBinding] or [vertexBufferRef] read before the call must not be reused
     * afterwards. Passing an empty array draws nothing this frame.
     *
     * @param vertices Interleaved line vertices, [FLOATS_PER_VERTEX] floats each (position, then
     * colour); every two consecutive vertices form one segment.
     * @throws IllegalArgumentException If the required line count exceeds
     * [DebugLineLayout.MAX_LINES_CEILING].
     */
    fun update(vertices: FloatArray) {
        val neededVertices = vertices.size / FLOATS_PER_VERTEX
        if (neededVertices > capacityVertices) growTo(neededVertices)
        val device = graphicsDevice.wgpuContext.device
        device.queue.writeBufferData(vertexBuffer, 0uL, fastArrayBufferOf(vertices))
        drawVertexCount = vertices.size / FLOATS_PER_VERTEX
    }

    /**
     * Returns the GPU vertex buffer the line pass binds. It is replaced when [update] grows the
     * mesh, so fetch it per draw rather than caching it.
     */
    fun vertexBufferRef(): GPUBuffer = vertexBuffer

    /**
     * Closes the GPU vertex buffer. Call once, after the last frame that draws this mesh; the mesh
     * must not be updated or drawn afterwards.
     */
    fun destroy() {
        vertexBuffer.close()
    }

    /**
     * Vertex-stream constants for the debug line list, re-exported from [DebugLineLayout] so
     * callers size arrays without importing the render-passes module.
     */
    companion object {
        /** Floats in one line vertex: a `vec3` position followed by a `vec4` colour, 7 in total. */
        val FLOATS_PER_VERTEX = DebugLineLayout.FLOATS_PER_VERTEX

        /**
         * Vertices per line segment: 2, since the topology is a plain line list with no index
         * buffer.
         */
        val VERTICES_PER_LINE = DebugLineLayout.VERTICES_PER_LINE
    }
}
