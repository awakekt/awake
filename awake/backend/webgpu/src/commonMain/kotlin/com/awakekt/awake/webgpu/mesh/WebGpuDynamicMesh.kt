/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.mesh

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.mesh.DynamicMesh
import com.awakekt.awake.render.mesh.GpuMesh
import com.awakekt.awake.render.mesh.requireDynamicMeshShape
import com.awakekt.awake.render.mesh.requireVertexRange
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.pipeline.WebGpuBufferHandle
import com.awakekt.awake.webgpu.ui.MeshUploadStaging
import com.awakekt.awake.webgpu.ui.writeMeshFloatRange
import com.awakekt.awake.webgpu.ui.writeMeshInts
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBufferUsage

/**
 * A scene mesh whose vertices are rewritten in place: WebGPU's [DynamicMesh].
 *
 * One vertex buffer is enough. `queue.writeBuffer` runs on the queue after every command buffer
 * already submitted, so a frame in flight keeps reading what it was recorded against and the next
 * submission reads the new vertices. [writeVertices] writes its range straight away, through kept
 * host memory, so it allocates nothing once warm. WebGPU zeroes a new buffer, so the vertices start
 * as zeros. The index buffer never changes and is written once.
 */
internal class WebGpuDynamicMesh(
    graphicsDevice: GraphicsDevice,
    override val format: VertexFormat,
    override val vertexCapacity: Int,
    indices: IntArray,
) : DynamicMesh {
    private val queue = graphicsDevice.wgpuContext.device.queue
    private val stride = format.strideFloats
    private val staging = MeshUploadStaging()

    /** The vertex buffer as the shared recording path's handle. */
    val vertexBinding: WebGpuBufferHandle

    /** The 32-bit index buffer as the shared recording path's handle. */
    val indexBinding: WebGpuBufferHandle

    override val indexCapacity: Int = indices.size

    override var drawIndexCount: Int = 0
        set(value) {
            require(value in 0..indexCapacity) { "drawIndexCount must be in 0..$indexCapacity, not $value." }
            field = value
        }

    override val sizeBytes: Long = vertexCapacity.toLong() * format.strideBytes + indices.size.toLong() * Int.SIZE_BYTES

    init {
        requireDynamicMeshShape(vertexCapacity, indices)
        val device = graphicsDevice.wgpuContext.device
        vertexBinding = WebGpuBufferHandle(
            device.createBuffer(
                BufferDescriptor(
                    size = vertexCapacity.toULong() * format.strideBytes.toULong(),
                    usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                ),
            ),
        )
        indexBinding = WebGpuBufferHandle(
            device.createBuffer(
                BufferDescriptor(
                    size = indices.size.toULong() * Int.SIZE_BYTES.toULong(),
                    usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
                ),
            ),
        )
        queue.writeMeshInts(indexBinding.buffer, indices, staging)
    }

    override fun writeVertices(vertices: FloatArray, firstVertex: Int, vertexCount: Int) {
        requireVertexRange(vertices, firstVertex, vertexCount)
        if (vertexCount == 0) return
        queue.writeMeshFloatRange(vertexBinding.buffer, vertices, firstVertex * stride, vertexCount * stride, staging)
    }

    override fun destroy() {
        vertexBinding.buffer.close()
        indexBinding.buffer.close()
    }
}

/** The vertex buffer a scene draw of this mesh binds. */
internal fun GpuMesh.sceneVertexBinding(): WebGpuBufferHandle =
    if (this is WebGpuDynamicMesh) vertexBinding else (this as Mesh).vertexBinding

/**
 * The index buffer a scene draw of this mesh binds: its line indices for a line-list pipeline
 * ([lines]). A dynamic mesh has no line indices; its edges are not drawn as lines.
 */
internal fun GpuMesh.sceneIndexBinding(lines: Boolean): WebGpuBufferHandle = when {
    this is WebGpuDynamicMesh -> indexBinding
    lines -> (this as Mesh).lineIndexBinding
    else -> (this as Mesh).indexBinding
}

/** How many indices a scene draw of this mesh submits: a dynamic mesh's drawn count, or none as lines. */
internal fun GpuMesh.sceneElementCount(lines: Boolean): Int = when {
    this is WebGpuDynamicMesh -> if (lines) 0 else drawIndexCount
    lines -> (this as Mesh).lineIndexCount
    else -> (this as Mesh).indexCount
}
