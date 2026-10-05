/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry

import com.awakekt.awake.core.math.Vec3f

/**
 * Format-driven builder for procedural meshes.
 *
 * Attribute packing is delegated to [InterleavedVertices], while this type owns the index list.
 * Generators therefore describe vertices and topology instead of repeatedly allocating packed
 * float buffers or counting a cursor by hand.
 *
 * @property format Target vertex format layout.
 * @param vertexCount Total number of vertices to preallocate.
 */
class MeshGeometryBuilder(
    val format: VertexFormat,
    vertexCount: Int,
) {
    private val vertices = InterleavedVertices(format, vertexCount)
    private val indices = mutableListOf<Int>()

    /** Number of vertices allocated in the underlying vertex buffer. */
    val vertexCount: Int get() = vertices.vertexCount

    /**
     * Returns whether this builder's format carries [semantic].
     *
     * @param semantic Vertex semantic to check.
     * @return `true` if the semantic is present in the format, `false` otherwise.
     */
    fun has(semantic: VertexSemantic): Boolean = vertices.has(semantic)

    /**
     * Sets position and optional normal and color attributes for the vertex at [index].
     *
     * @param index Target vertex index.
     * @param position 3D position vector.
     * @param normal Optional surface normal vector.
     * @param color Optional RGB color vector.
     */
    fun vertex(index: Int, position: Vec3f, normal: Vec3f? = null, color: Vec3f? = null) {
        vertices.vertex(index, position, normal, color)
    }

    /**
     * Writes a three-component vector value for [semantic] at the given [vertex] index.
     *
     * @param vertex Target vertex index.
     * @param semantic Vertex semantic to write.
     * @param value Vector value to write.
     */
    fun put(vertex: Int, semantic: VertexSemantic, value: Vec3f) = vertices.put(vertex, semantic, value)

    /**
     * Writes scalar components for [semantic] at the given [vertex] index.
     *
     * @param vertex Target vertex index.
     * @param semantic Vertex semantic to write.
     * @param x First vector component.
     * @param y Second vector component.
     * @param z Third vector component.
     */
    fun put(vertex: Int, semantic: VertexSemantic, x: Float, y: Float, z: Float) =
        vertices.put(vertex, semantic, x, y, z)

    /**
     * Fills [semantic] across all vertices with the given constant [values].
     *
     * @param semantic Vertex semantic to fill.
     * @param values Constant component values to write across all vertices.
     */
    fun fill(semantic: VertexSemantic, vararg values: Float) = vertices.fill(semantic, *values)

    /** Adds one triangle in the winding order supplied by the generator. */
    fun triangle(a: Int, b: Int, c: Int) {
        indices += a
        indices += b
        indices += c
    }

    /** Adds a quad as two triangles while keeping the caller's winding explicit. */
    fun quad(a: Int, b: Int, c: Int, d: Int) {
        triangle(a, b, c)
        triangle(b, a, d)
    }

    /**
     * Validates indices and builds the immutable [MeshGeometry].
     *
     * @return Assembled mesh geometry instance.
     */
    fun build(): MeshGeometry {
        indices.forEach { index ->
            require(index in 0 until vertexCount) {
                "index $index is outside vertex range 0 until $vertexCount"
            }
        }
        return vertices.build(indices.toIntArray())
    }
}
