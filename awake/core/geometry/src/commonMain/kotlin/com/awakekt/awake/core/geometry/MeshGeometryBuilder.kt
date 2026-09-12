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
 */
class MeshGeometryBuilder(
    val format: VertexFormat,
    vertexCount: Int,
) {
    private val vertices = InterleavedVertices(format, vertexCount)
    private val indices = mutableListOf<Int>()

    val vertexCount: Int get() = vertices.vertexCount

    fun has(semantic: VertexSemantic): Boolean = vertices.has(semantic)

    fun vertex(index: Int, position: Vec3f, normal: Vec3f? = null, color: Vec3f? = null) {
        vertices.vertex(index, position, normal, color)
    }

    fun put(vertex: Int, semantic: VertexSemantic, value: Vec3f) = vertices.put(vertex, semantic, value)

    fun put(vertex: Int, semantic: VertexSemantic, x: Float, y: Float, z: Float) =
        vertices.put(vertex, semantic, x, y, z)

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

    fun build(): MeshGeometry {
        indices.forEach { index ->
            require(index in 0 until vertexCount) {
                "index $index is outside vertex range 0 until $vertexCount"
            }
        }
        return vertices.build(indices.toIntArray())
    }
}
