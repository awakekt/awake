// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.core.graphics2d

import io.github.ronjunevaldoz.awake.core.color.Color

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

data class TexturedVertex(
    val position: DrawPoint,
    val u: Float,
    val v: Float,
)

typealias UiTexturedVertex = TexturedVertex

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

/** Per-vertex-colored analog of [TexturedVertex]/[TexturedTriangleMesh]. */
data class ColoredVertex(
    val position: DrawPoint,
    val color: Color,
)

typealias UiColoredVertex = ColoredVertex

data class ColoredTriangleMesh(
    val vertices: List<ColoredVertex>,
    val indices: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ColoredTriangleMesh) return false
        return vertices == other.vertices && indices.contentEquals(other.indices)
    }

    override fun hashCode(): Int = 31 * vertices.hashCode() + indices.contentHashCode()
}

typealias UiColoredTriangleMesh = ColoredTriangleMesh

/**
 * Splits a mesh into pieces that each fit a backend's fixed per-draw buffer, cutting on triangle
 * boundaries and re-indexing each piece against its own vertex list.
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
