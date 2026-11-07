/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.mesh

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.GpuCapability
import com.awakekt.awake.render.pipeline.GpuCapabilityKind

/**
 * A mesh whose vertices are rewritten in place, frame after frame, without a new GPU allocation:
 * geometry whose shape changes every frame, such as a trail.
 *
 * Its vertex buffer holds [vertexCapacity] vertices and its index buffer the indices it was created
 * with; both are allocated once, when the mesh is created. [writeVertices] replaces a range of
 * vertices and uploads that range only, and [drawIndexCount] says how many of the indices, from the
 * first, a draw submits, so changing how much of the mesh is drawn uploads nothing.
 *
 * Draws see a write from the next frame that draws the mesh. A frame already submitted keeps the
 * vertices it was recorded with: a backend whose frames overlap keeps one buffer per frame in flight
 * and brings each up to date before it is drawn.
 */
interface DynamicMesh : Mesh {
    /** The most vertices [writeVertices] can write, fixed when the mesh is created. */
    val vertexCapacity: Int

    /** How many indices the mesh was created with; [drawIndexCount] is at most this. */
    val indexCapacity: Int

    /**
     * How many indices, from the first, a draw of this mesh submits: 0 draws nothing. Setting it
     * uploads nothing.
     */
    var drawIndexCount: Int

    /**
     * Copies [vertexCount] vertices, from vertex [firstVertex], of [vertices] into this mesh.
     *
     * [vertices] is laid out in [format] and indexed as the mesh is: vertex `i` starts at float
     * `i * format.strideFloats`, so a caller keeps one array the size of the whole mesh and writes
     * the part of it that changed. The floats are copied before this returns.
     *
     * @throws IllegalArgumentException If the range is not within [vertexCapacity] or [vertices].
     */
    fun writeVertices(vertices: FloatArray, firstVertex: Int, vertexCount: Int)
}

/**
 * Optional capability: a device that creates [DynamicMesh]es.
 *
 * Both GPU backends have it. A device without it, such as a test double, draws no geometry that
 * changes from frame to frame, and whatever asks for one draws nothing.
 */
interface GpuDynamicMeshes : GpuCapability {
    /**
     * Creates a mesh of [vertexCapacity] vertices in [format], drawn through [indices], which never
     * change. Its vertices start as zeros and [DynamicMesh.drawIndexCount] as 0. The caller owns the
     * result and destroys it.
     *
     * @throws IllegalArgumentException If [vertexCapacity] is not positive, [indices] is empty, or an
     * index is outside the vertices.
     */
    fun createDynamicMesh(format: VertexFormat, vertexCapacity: Int, indices: IntArray): DynamicMesh

    /** The key [com.awakekt.awake.render.pipeline.GpuDevice.capability] answers with this device's dynamic meshes. */
    companion object : GpuCapabilityKind<GpuDynamicMeshes>
}

/** Checks a [DynamicMesh.writeVertices] range: shared so every backend refuses the same calls. */
fun DynamicMesh.requireVertexRange(vertices: FloatArray, firstVertex: Int, vertexCount: Int) {
    require(firstVertex >= 0 && vertexCount >= 0 && firstVertex + vertexCount <= vertexCapacity) {
        "Vertices $firstVertex until ${firstVertex + vertexCount} are outside the mesh's $vertexCapacity."
    }
    require((firstVertex + vertexCount) * format.strideFloats <= vertices.size) {
        "Vertices $firstVertex until ${firstVertex + vertexCount} are outside the ${vertices.size} floats given."
    }
}

/** Checks [GpuDynamicMeshes.createDynamicMesh]'s arguments: shared so every backend refuses the same calls. */
fun requireDynamicMeshShape(vertexCapacity: Int, indices: IntArray) {
    require(vertexCapacity > 0) { "A dynamic mesh needs at least one vertex, not $vertexCapacity." }
    require(indices.isNotEmpty()) { "A dynamic mesh needs at least one index." }
    require(indices.all { it in 0 until vertexCapacity }) { "Every index must be below the $vertexCapacity vertices." }
}
