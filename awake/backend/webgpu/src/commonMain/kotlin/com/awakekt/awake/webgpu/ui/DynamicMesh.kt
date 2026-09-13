/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.ui

import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.webgpu.device.GraphicsDevice
import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUIndexFormat

/**
 * A vertex+index buffer whose live contents may change every frame -- mirrors Vulkan's
 * `DynamicMesh` (see that class's doc comment for the full rationale), but thinner: WebGPU's
 * `queue.writeBuffer` is already safe to call every frame with no staging/mapping ceremony.
 * Stable runs retain their upload data and avoid both the write and the conversion entirely. This
 * class still exists separately from `awake-backend-webgpu`'s own `Mesh` (not reused as-is)
 * because `Mesh`'s constructor sizes its buffer to the exact incoming array -- wrong for a buffer
 * whose live content size varies frame to frame while capacity ([maxQuads]) stays fixed.
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

    /**
     * Reusable CPU-side upload storage. `queue.writeBuffer` copies its input when called, so the
     * same storage can be overwritten on the next frame. Allocating a new ArrayBuffer for every
     * run made the Kotlin/Wasm GC and JS interop pay for the full UI upload even when the run's
     * geometry size was stable.
     */
    private var vertexUploadData = ArrayBuffer.allocate(vertexBufferByteSize(maxVertices))
    private var indexUploadData = ArrayBuffer.allocate(indexBufferByteSize(maxIndices))

    /** The last arrays uploaded to this pooled run. Coalesced UI creates fresh arrays per frame;
     * retaining the previous references lets us detect unchanged geometry without copying it. */
    private var lastUploadedVertices: FloatArray? = null
    private var lastUploadedIndices: IntArray? = null

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
                size = vertexBufferByteSize(maxVertices),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
            ),
        )
        indexBuffer = device.createBuffer(
            BufferDescriptor(
                size = indexBufferByteSize(maxIndices),
                usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
            ),
        )
    }

    fun update(vertices: FloatArray, indices: IntArray) {
        val buffersGrew = growTo(vertexFloats = vertices.size, indexCount = indices.size)
        drawIndexCount = indices.size
        if (indices.isEmpty()) return

        // The UI layer produces new arrays even when a retained panel has not changed. Comparing
        // in Wasm is considerably cheaper than crossing into JS once per element for both arrays.
        // Do not skip when the caller reuses the same mutable array: it may have changed in place.
        if (!buffersGrew && hasUnchangedUploadedData(vertices, indices)) {
            return
        }

        val device = graphicsDevice.wgpuContext.device
        vertexUploadData.setFloats(0uL, vertices)
        indexUploadData.setInts(0uL, indices)
        device.queue.writeBuffer(
            buffer = vertexBuffer,
            bufferOffset = 0uL,
            data = vertexUploadData,
            dataOffset = 0uL,
            size = (vertices.size * Float.SIZE_BYTES).toULong(),
        )
        device.queue.writeBuffer(
            buffer = indexBuffer,
            bufferOffset = 0uL,
            data = indexUploadData,
            dataOffset = 0uL,
            size = (indices.size * Int.SIZE_BYTES).toULong(),
        )
        lastUploadedVertices = vertices
        lastUploadedIndices = indices
    }

    private fun hasUnchangedUploadedData(vertices: FloatArray, indices: IntArray): Boolean {
        val previousVertices = lastUploadedVertices
        val previousIndices = lastUploadedIndices
        return when {
            previousVertices == null -> false
            previousIndices == null -> false
            previousVertices === vertices -> false
            previousIndices === indices -> false
            !previousVertices.contentEquals(vertices) -> false
            else -> previousIndices.contentEquals(indices)
        }
    }

    /**
     * Reallocates the buffers when a run does not fit, keeping the larger size.
     *
     * [maxQuads] is how many small primitives to batch, not a ceiling on one of them: a single
     * tessellated shape is handed over whole rather than split, because splitting re-indexes it
     * every frame. See the Vulkan DynamicMesh for the measurement.
     */
    private fun growTo(vertexFloats: Int, indexCount: Int): Boolean {
        val neededVertices = (vertexFloats + floatsPerVertex - 1) / floatsPerVertex
        if (neededVertices <= maxVertices && indexCount <= maxIndices) return false

        maxVertices = maxOf(maxVertices * 2, neededVertices)
        maxIndices = maxOf(maxIndices * 2, indexCount)
        growthCount += 1

        val device = graphicsDevice.wgpuContext.device
        vertexBuffer.close()
        indexBuffer.close()
        vertexUploadData = ArrayBuffer.allocate(vertexBufferByteSize(maxVertices))
        indexUploadData = ArrayBuffer.allocate(indexBufferByteSize(maxIndices))
        lastUploadedVertices = null
        lastUploadedIndices = null
        vertexBuffer = device.createBuffer(
            BufferDescriptor(
                size = vertexBufferByteSize(maxVertices),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
            ),
        )
        indexBuffer = device.createBuffer(
            BufferDescriptor(
                size = indexBufferByteSize(maxIndices),
                usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
            ),
        )
        drawIndexCount = 0
        return true
    }

    fun vertexBufferRef(): GPUBuffer = vertexBuffer
    fun indexBufferRef(): GPUBuffer = indexBuffer

    fun destroy() {
        vertexBuffer.close()
        indexBuffer.close()
    }

    private fun vertexBufferByteSize(vertices: Int): ULong =
        (vertices.toLong() * floatsPerVertex.toLong() * Float.SIZE_BYTES.toLong()).toULong()

    companion object {
        private fun indexBufferByteSize(maxIndices: Int): ULong =
            (maxIndices.toLong() * Int.SIZE_BYTES.toLong()).toULong()

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
