/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.mesh

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.webgpu.WebGpuHandles
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.handles.BufferHandle
import com.awakekt.awake.webgpu.handles.DeviceMemoryHandle
import com.awakekt.awake.webgpu.pipeline.WebGpuBufferHandle
import com.awakekt.awake.webgpu.writeBufferData
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUIndexFormat
import com.awakekt.awake.render.mesh.Mesh as RenderMesh

/**
 * Phase 2.5 milestone 2 slice 1 (see docs/mvp-plan.md): real wgpu4k implementation.
 * `device.queue.writeBufferData()` uploads directly -- no HOST_VISIBLE staging buffer + one-time
 * command buffer + `vkCmdCopyBuffer` dance the way Vulkan's `Mesh.kt` needs, a real
 * simplification, not an oversight. [vertexBufferMemory]/[indexBufferMemory] have no WebGPU
 * equivalent (buffer memory is managed internally, no separate allocation object to hold a
 * handle to) and just mirror their buffer's handle. [runOneTimeCommands] is unused for the
 * same reason.
 *
 * [lineIndexBuffer]/[lineIndexCount] are a second, `LineList`-topology index buffer derived
 * from [indices] -- each triangle's 3 edges become 2-index line entries (shared edges between
 * adjacent triangles are emitted twice, same overdraw a Vulkan `VK_POLYGON_MODE_LINE` pipeline
 * already produces for the same reason: no half-edge/adjacency structure is built to dedupe
 * them). `Renderer.wireframe`'s WebGPU implementation binds these instead of [indexBuffer]
 * against a `LineList` pipeline -- see that class's own doc comment for why this approach (not
 * a barycentric-coordinate fragment shader) was chosen: WebGPU has no polygon-mode-line
 * equivalent, and this reuses the exact same vertex buffer/shader, only the index buffer and
 * pipeline topology differ. Built unconditionally (like [indexBuffer] itself), not lazily on
 * first wireframe use -- keeps this class's resource lifecycle a single, unconditional init
 * block instead of a second lazy-build path a mesh's caller would need to remember to trigger.
 */
class Mesh @Suppress("LongParameterList") private constructor(
    private val graphicsDevice: GraphicsDevice,
    private val runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
    vertices: FloatArray,
    indices: IntArray,
    override val format: VertexFormat,
    override val localBounds: Aabb?,
    /** The mesh whose vertex buffer this one draws instead of uploading its own; see [sharingVertices]. */
    sharedFrom: Mesh?,
) : RenderMesh {
    constructor(
        graphicsDevice: GraphicsDevice,
        runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
        vertices: FloatArray,
        indices: IntArray,
        format: VertexFormat = VertexFormat.PositionColorUv,
        /** The box these vertices occupy -- Vulkan's twin, same reason. */
        localBounds: Aabb? = null,
    ) : this(graphicsDevice, runOneTimeCommands, vertices, indices, format, localBounds, null)

    /**
     * Handle of the GPU vertex buffer, as a [WebGpuHandles] table id. Fixed at construction: a mesh
     * never re-uploads its geometry.
     */
    var vertexBuffer: BufferHandle
    var vertexBufferMemory: DeviceMemoryHandle
    var indexBuffer: BufferHandle
    var indexBufferMemory: DeviceMemoryHandle

    /**
     * Number of indices in the triangle-list index buffer, which is how many indices a solid draw
     * submits.
     */
    val indexCount: Int = indices.size

    override val sizeBytes: Long =
        (if (sharedFrom == null) vertices.size.toLong() * Float.SIZE_BYTES else 0L) + indices.size.toLong() * Int.SIZE_BYTES

    /** Counts the meshes drawing [vertexBuffer], so the last one destroyed closes it. */
    private val vertexOwner: SharedVertices
    var lineIndexBuffer: BufferHandle
    val lineIndexCount: Int

    /** The same three buffers above, as the shared render layer's opaque handle type -- built
     * once here so the per-draw recording path allocates nothing per frame. */
    val vertexBinding: WebGpuBufferHandle

    /** The triangle-list index buffer as the shared render layer's opaque buffer handle. */
    val indexBinding: WebGpuBufferHandle

    /**
     * The derived wireframe index buffer as the shared render layer's opaque buffer handle, bound
     * against a `LineList` pipeline when the mesh is drawn as wireframe.
     */
    val lineIndexBinding: WebGpuBufferHandle

    init {
        val device = graphicsDevice.wgpuContext.device

        if (sharedFrom != null) {
            vertexOwner = sharedFrom.vertexOwner.also { it.meshes++ }
            vertexBuffer = sharedFrom.vertexBuffer
            vertexBufferMemory = sharedFrom.vertexBufferMemory
            vertexBinding = sharedFrom.vertexBinding
        } else {
            vertexOwner = SharedVertices()
            val rawVertexBuffer: GPUBuffer = device.createBuffer(
                BufferDescriptor(
                    size = (vertices.size * Float.SIZE_BYTES).toULong(),
                    usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                ),
            )
            device.queue.writeBufferData(rawVertexBuffer, 0uL, fastArrayBufferOf(vertices))
            val vertexHandle = WebGpuHandles.register(rawVertexBuffer)
            vertexBuffer = BufferHandle(vertexHandle)
            vertexBufferMemory = DeviceMemoryHandle(vertexHandle)
            vertexBinding = WebGpuBufferHandle(rawVertexBuffer)
        }

        val rawIndexBuffer: GPUBuffer = device.createBuffer(
            BufferDescriptor(
                size = (indices.size * Int.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
            ),
        )
        device.queue.writeBufferData(rawIndexBuffer, 0uL, fastArrayBufferOf(indices))
        val indexHandle = WebGpuHandles.register(rawIndexBuffer)
        indexBuffer = BufferHandle(indexHandle)
        indexBufferMemory = DeviceMemoryHandle(indexHandle)

        val lineIndices = triangleIndicesToLineIndices(indices)
        lineIndexCount = lineIndices.size
        val rawLineIndexBuffer: GPUBuffer = device.createBuffer(
            BufferDescriptor(
                size = (lineIndices.size * Int.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
            ),
        )
        device.queue.writeBufferData(rawLineIndexBuffer, 0uL, fastArrayBufferOf(lineIndices))
        lineIndexBuffer = BufferHandle(WebGpuHandles.register(rawLineIndexBuffer))

        indexBinding = WebGpuBufferHandle(rawIndexBuffer)
        lineIndexBinding = WebGpuBufferHandle(rawLineIndexBuffer)
    }

    /** Releases the handle-table entries and closes the three buffers behind them. */
    override fun destroy() {
        if (--vertexOwner.meshes == 0) {
            WebGpuHandles.release(vertexBuffer.handle)
            vertexBinding.buffer.close()
        }
        WebGpuHandles.release(indexBuffer.handle)
        WebGpuHandles.release(lineIndexBuffer.handle)
        indexBinding.buffer.close()
        lineIndexBinding.buffer.close()
    }

    /**
     * A mesh drawing this one's vertex buffer with [indices] instead of its own, or null when this
     * mesh has been destroyed and its vertex buffer with it.
     */
    internal fun sharingVertices(indices: IntArray, bounds: Aabb?): Mesh? = if (vertexOwner.meshes == 0) {
        null
    } else {
        Mesh(graphicsDevice, runOneTimeCommands, EMPTY_VERTICES, indices, format, bounds, this)
    }

    /** How many meshes still draw one vertex buffer. */
    private class SharedVertices {
        var meshes = 1
    }

    private companion object {
        val EMPTY_VERTICES = FloatArray(0)
    }
}

/** One (start, end) index pair per triangle edge, 3 edges per triangle -- see [Mesh]'s own
 * `lineIndexBuffer` doc comment for the overdraw/no-dedup rationale. */
internal fun triangleIndicesToLineIndices(indices: IntArray): IntArray {
    val triangleCount = indices.size / 3
    val lineIndices = IntArray(triangleCount * 6)
    var triangle = 0
    while (triangle < triangleCount) {
        val base = triangle * 3
        val i0 = indices[base]
        val i1 = indices[base + 1]
        val i2 = indices[base + 2]
        val out = triangle * 6
        lineIndices[out] = i0
        lineIndices[out + 1] = i1
        lineIndices[out + 2] = i1
        lineIndices[out + 3] = i2
        lineIndices[out + 4] = i2
        lineIndices[out + 5] = i0
        triangle += 1
    }
    return lineIndices
}

// Referenced by Renderer.kt (same source set) -- GPUIndexFormat isn't derivable from an
// IntArray-typed index buffer alone, so this constant documents the assumption both files
// share: indices are always UInt32 here (matching Vulkan's VK_INDEX_TYPE_UINT32 usage).
internal val meshIndexFormat = GPUIndexFormat.Uint32
