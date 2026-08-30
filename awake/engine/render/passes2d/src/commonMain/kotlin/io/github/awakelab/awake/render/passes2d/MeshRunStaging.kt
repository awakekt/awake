/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.passes2d

import io.github.awakelab.awake.core.geometry.VertexFormats2D
import io.github.awakelab.awake.core.graphics2d.DrawCommand
import io.github.awakelab.awake.core.graphics2d.writeVertex

/**
 * Staging for [DrawCommand.Mesh]: triangles the caller tessellated once and keeps redrawing.
 *
 * Its own file rather than another branch inside [DrawRunCoalescer], which is already the largest
 * class in this module. The split is real and not cosmetic: every other primitive is tessellated
 * here and staged in the same breath, while a mesh arrives finished and only has to be placed.
 */

/** [chunkColoredVertexTriangleMeshes] for meshes still carrying their placement. */
internal fun chunkPlacedMeshes(runs: MutableList<StagedDrawRun>, meshes: List<DrawCommand.Mesh>, maxQuads: Int) {
    val maxVertices = maxQuads * VertexFormats2D.VERTICES_PER_QUAD
    val maxIndices = maxQuads * VertexFormats2D.INDICES_PER_QUAD
    // A shape wider than the batching budget becomes its own run rather than being split. The
    // split was never cheap -- it re-indexes each piece through a hash map, every frame -- and it
    // is no longer needed: DynamicMesh grows its buffers to fit whatever a run carries and keeps
    // the larger size, so maxQuads is a hint for how many small primitives to batch together, not
    // a ceiling on one of them.
    val pieces = meshes

    var chunk = mutableListOf<DrawCommand.Mesh>()
    var chunkVertices = 0
    var chunkIndices = 0

    fun flushChunk() {
        if (chunk.isEmpty()) return
        runs += stagePlacedMeshesToRun(chunk)
        chunk = mutableListOf()
        chunkVertices = 0
        chunkIndices = 0
    }

    for (piece in pieces) {
        val vertexCount = piece.mesh.vertices.size
        val indexCount = piece.mesh.indices.size
        if (chunk.isNotEmpty() && (chunkVertices + vertexCount > maxVertices || chunkIndices + indexCount > maxIndices)) {
            flushChunk()
        }
        chunk += piece
        chunkVertices += vertexCount
        chunkIndices += indexCount
    }
    flushChunk()
}

private fun stagePlacedMeshesToRun(meshes: List<DrawCommand.Mesh>): StagedDrawRun.QuadRun {
    val totalVertices = meshes.sumOf { it.mesh.vertices.size }
    val totalIndices = meshes.sumOf { it.mesh.indices.size }
    val vertices = FloatArray(totalVertices * VertexFormats2D.FLOATS_PER_VERTEX)
    val indices = IntArray(totalIndices)
    var vertexCursor = 0
    var indexCursor = 0
    var vertexOffset = 0

    meshes.forEach { placed ->
        placed.mesh.vertices.forEach { vertex ->
            writeVertex(
                vertices,
                vertexCursor,
                vertex.position.x * placed.scaleX + placed.offsetX,
                vertex.position.y * placed.scaleY + placed.offsetY,
                if (placed.alpha >= 1f) vertex.color else vertex.color.withAlpha(vertex.color.a * placed.alpha),
            )
            vertexCursor += VertexFormats2D.FLOATS_PER_VERTEX
        }
        placed.mesh.indices.forEach { index ->
            indices[indexCursor] = vertexOffset + index
            indexCursor += 1
        }
        vertexOffset += placed.mesh.vertices.size
    }
    return StagedDrawRun.QuadRun(vertices, indices)
}
