/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.ui

import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUIndexFormat

/**
 * A vertex+index buffer rewritten every frame -- mirrors Vulkan's `DynamicMesh` (see that
 * class's doc comment for the full rationale), but thinner: WebGPU's `queue.writeBuffer` is
 * already safe to call every frame with no staging/mapping ceremony. This class still exists
 * separately from `awake-backend-webgpu`'s own `Mesh` (not reused as-is) because `Mesh`'s
 * constructor sizes its buffer to the exact incoming array -- wrong for a buffer whose live
 * content size varies frame to frame while capacity ([maxQuads]) stays fixed.
 */
class DynamicMesh(
    private val graphicsDevice: GraphicsDevice,
    private val maxQuads: Int,
    private val floatsPerVertex: Int = FLOATS_PER_VERTEX,
) {
    // Current capacity, not a bound: raised by [growTo] when a run does not fit.
    private var maxVertices = maxQuads * VERTICES_PER_QUAD
    private var maxIndices = maxQuads * INDICES_PER_QUAD

    private var vertexBuffer: GPUBuffer
    private var indexBuffer: GPUBuffer

    /** Reallocations so far. Steady state is zero; see the Vulkan DynamicMesh for why. */
    var growthCount: Int = 0
        private set

    /** How many indices this frame's [update] actually wrote -- [draw] only draws this many. */
    var drawIndexCount: Int = 0
        private set

    init {
        val device = graphicsDevice.wgpuContext.device
        vertexBuffer = device.createBuffer(
            BufferDescriptor(
                size = (maxVertices.toLong() * floatsPerVertex.toLong() * 4L).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
            ),
        )
        indexBuffer = device.createBuffer(
            BufferDescriptor(
                size = (maxIndices.toLong() * 4L).toULong(),
                usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
            ),
        )
    }

    fun update(vertices: FloatArray, indices: IntArray) {
        growTo(vertexFloats = vertices.size, indexCount = indices.size)
        val device = graphicsDevice.wgpuContext.device
        device.queue.writeBuffer(vertexBuffer, 0uL, fastArrayBufferOf(vertices))
        device.queue.writeBuffer(indexBuffer, 0uL, fastArrayBufferOf(indices))
        drawIndexCount = indices.size
    }

    /**
     * Reallocates the buffers when a run does not fit, keeping the larger size.
     *
     * [maxQuads] is how many small primitives to batch, not a ceiling on one of them: a single
     * tessellated shape is handed over whole rather than split, because splitting re-indexes it
     * every frame. See the Vulkan DynamicMesh for the measurement.
     */
    private fun growTo(vertexFloats: Int, indexCount: Int) {
        val neededVertices = (vertexFloats + floatsPerVertex - 1) / floatsPerVertex
        if (neededVertices <= maxVertices && indexCount <= maxIndices) return

        maxVertices = maxOf(maxVertices * 2, neededVertices)
        maxIndices = maxOf(maxIndices * 2, indexCount)
        growthCount += 1

        val device = graphicsDevice.wgpuContext.device
        vertexBuffer.close()
        indexBuffer.close()
        vertexBuffer = device.createBuffer(
            BufferDescriptor(
                size = (maxVertices.toLong() * floatsPerVertex.toLong() * 4L).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
            ),
        )
        indexBuffer = device.createBuffer(
            BufferDescriptor(
                size = (maxIndices.toLong() * 4L).toULong(),
                usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
            ),
        )
        drawIndexCount = 0
    }

    fun vertexBufferRef(): GPUBuffer = vertexBuffer
    fun indexBufferRef(): GPUBuffer = indexBuffer

    fun destroy() {
        vertexBuffer.close()
        indexBuffer.close()
    }

    companion object {
        /** pos (vec2) + color (vec4) + transform (vec4: scale.xy + pivot.xy, see
         * `UiPrimitiveTransform`) -- see `ui_quad.wgsl`'s input layout. */
        const val FLOATS_PER_VERTEX = VertexFormats2D.FLOATS_PER_VERTEX

        /** pos (vec2) + uv (vec2) + color (vec4) + transform (vec4) -- see `ui_glyph.wgsl`'s
         * input layout. */
        const val GLYPH_FLOATS_PER_VERTEX = VertexFormats2D.GLYPH_FLOATS_PER_VERTEX

        /** pos (vec2) + localPos (vec2) + halfSize (vec2) + radius (float) + smoothing (float) +
         * color (vec4) + transform (vec4) -- see `ui_rounded_quad.wgsl`'s input layout. */
        const val ROUNDED_QUAD_FLOATS_PER_VERTEX = VertexFormats2D.ROUNDED_QUAD_FLOATS_PER_VERTEX
        const val VERTICES_PER_QUAD = VertexFormats2D.VERTICES_PER_QUAD
        const val INDICES_PER_QUAD = VertexFormats2D.INDICES_PER_QUAD
        val indexFormat = GPUIndexFormat.Uint32
    }
}
