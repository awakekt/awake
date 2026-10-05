/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Rectangle

/**
 * A basic 2D indexed triangle mesh defined by a list of points and triangle index tuples.
 *
 * @property points The list of 2D vertex positions in the mesh.
 * @property indices The triangle indices referencing elements in [points]. Every three consecutive entries form a triangle.
 */
data class TriangleMesh(
    val points: List<DrawPoint>,
    val indices: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TriangleMesh) return false
        return points == other.points && indices.contentEquals(other.indices)
    }

    override fun hashCode(): Int = 31 * points.hashCode() + indices.contentHashCode()
}

typealias UiTriangleMesh = TriangleMesh

/**
 * A vertex with a 2D position and normalized texture coordinates.
 *
 * @property position The 2D screen or canvas coordinate of the vertex.
 * @property u The horizontal texture coordinate (U), typically normalized between 0 and 1.
 * @property v The vertical texture coordinate (V), typically normalized between 0 and 1.
 */
data class TexturedVertex(
    val position: DrawPoint,
    val u: Float,
    val v: Float,
)

typealias UiTexturedVertex = TexturedVertex

/**
 * An indexed 2D triangle mesh composed of textured vertices.
 *
 * @property vertices The list of [TexturedVertex] instances defining geometry and UV mappings.
 * @property indices The index array specifying triangle vertex connectivity.
 */
data class TexturedTriangleMesh(
    val vertices: List<TexturedVertex>,
    val indices: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TexturedTriangleMesh) return false
        return vertices == other.vertices && indices.contentEquals(other.indices)
    }

    override fun hashCode(): Int = 31 * vertices.hashCode() + indices.contentHashCode()
}

typealias UiTexturedTriangleMesh = TexturedTriangleMesh

/**
 * Per-vertex-colored analog of [TexturedVertex]/[TexturedTriangleMesh].
 *
 * @property position The 2D coordinate of the vertex.
 * @property color The color value assigned to the vertex.
 */
data class ColoredVertex(
    val position: DrawPoint,
    val color: Color,
)

typealias UiColoredVertex = ColoredVertex

/**
 * An indexed 2D triangle mesh where each vertex carries an explicit [Color].
 *
 * @property vertices The list of colored vertices defining geometry and vertex tints.
 * @property indices The index array specifying triangle vertex connectivity.
 */
data class ColoredTriangleMesh(
    val vertices: List<ColoredVertex>,
    val indices: IntArray,
) {
    /**
     * The box these triangles cover, including any anti-aliased fringe. Empty for no vertices.
     *
     * @return The bounding [Rectangle] enclosing all mesh vertices.
     */
    fun bounds(): Rectangle {
        if (vertices.isEmpty()) return Rectangle(0f, 0f, 0f, 0f)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        vertices.forEach {
            minX = minOf(minX, it.position.x)
            minY = minOf(minY, it.position.y)
            maxX = maxOf(maxX, it.position.x)
            maxY = maxOf(maxY, it.position.y)
        }
        return Rectangle(minX, minY, maxX - minX, maxY - minY)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ColoredTriangleMesh) return false
        return vertices == other.vertices && indices.contentEquals(other.indices)
    }

    override fun hashCode(): Int = 31 * vertices.hashCode() + indices.contentHashCode()
}

typealias UiColoredTriangleMesh = ColoredTriangleMesh

/**
 * Concatenates meshes into one, shifting each piece's indices past the vertices already taken.
 *
 * The inverse of [splitToCapacity]. A caller that tessellates several paths into one drawable --
 * an icon's fill and its outline -- keeps them as one mesh so the whole thing is cached, placed and
 * staged as a unit.
 *
 * @return The merged [ColoredTriangleMesh] containing all combined vertices and remapped indices.
 */
fun List<ColoredTriangleMesh>.merge(): ColoredTriangleMesh {
    if (size == 1) return this[0]
    val vertices = ArrayList<ColoredVertex>(sumOf { it.vertices.size })
    val indices = IntArray(sumOf { it.indices.size })
    var indexCursor = 0
    forEach { mesh ->
        val vertexOffset = vertices.size
        vertices += mesh.vertices
        mesh.indices.forEach { index ->
            indices[indexCursor] = vertexOffset + index
            indexCursor += 1
        }
    }
    return ColoredTriangleMesh(vertices, indices)
}

/**
 * Splits a mesh into pieces that each fit a backend's fixed per-draw buffer, cutting on triangle
 * boundaries and re-indexing each piece against its own vertex list.
 *
 * @param maxVertices The maximum number of vertices permitted in a single output mesh chunk.
 * @param maxIndices The maximum number of indices permitted in a single output mesh chunk.
 * @return A list of partitioned [ColoredTriangleMesh] chunks adhering to the given capacity limits.
 */
fun ColoredTriangleMesh.splitToCapacity(maxVertices: Int, maxIndices: Int): List<ColoredTriangleMesh> {
    if (vertices.size <= maxVertices && indices.size <= maxIndices) return listOf(this)
    if (maxVertices < 3 || maxIndices < 3) return listOf(this)

    val pieces = ArrayList<ColoredTriangleMesh>()
    var pieceVertices = ArrayList<ColoredVertex>()
    var pieceIndices = ArrayList<Int>()
    var remap = HashMap<Int, Int>()

    fun flush() {
        if (pieceIndices.isEmpty()) return
        pieces += ColoredTriangleMesh(pieceVertices.toList(), pieceIndices.toIntArray())
        pieceVertices = ArrayList()
        pieceIndices = ArrayList()
        remap = HashMap()
    }

    var at = 0
    while (at + 3 <= indices.size) {
        val triangle = intArrayOf(indices[at], indices[at + 1], indices[at + 2])
        val newVertices = triangle.distinct().count { it !in remap }
        if (pieceIndices.isNotEmpty() &&
            (pieceVertices.size + newVertices > maxVertices || pieceIndices.size + 3 > maxIndices)
        ) {
            flush()
        }
        triangle.forEach { source ->
            val mapped = remap.getOrPut(source) {
                pieceVertices += vertices[source]
                pieceVertices.size - 1
            }
            pieceIndices += mapped
        }
        at += 3
    }
    flush()
    return pieces
}
