/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

import com.awakekt.awake.core.geometry.MeshSimplifier

/**
 * Simplifies a [GltfMesh] to a target triangle ratio using Garland-Heckbert Quadric Error Metrics (QEM).
 *
 * This function performs per-part mesh decimation in the model's native coordinate space,
 * preserving all vertex attributes (normals, vertex colors, local UVs, joint indices, and bone weights).
 *
 * [lockBoundaries] keeps open boundary edges from collapsing inward: a mesh built from separately
 * modelled parts has open loops where the parts meet, and collapsing them opens gaps between parts.
 *
 * @param targetRatio The target ratio of triangles to retain (e.g. `0.75f` retains 75% of triangles).
 * @param lockBoundaries When `true`, open boundary loop edges are constrained from collapsing (defaults to `true`).
 * @return A new [GltfMesh] containing the simplified geometry and remapped vertex attributes.
 */
fun GltfMesh.simplified(targetRatio: Float, lockBoundaries: Boolean = true): GltfMesh {
    val result = if (targetRatio < 1f) MeshSimplifier.simplify(positions, indices, targetRatio, lockBoundaries = lockBoundaries) else null
    val count = (result?.positions?.size ?: 0) / 3
    if (result == null || count == 0 || result.indices.isEmpty()) return this
    val source = sourceVertices(result.vertexRemap, count)
    return copy(
        positions = result.positions,
        indices = result.indices,
        normals = normals?.remapped(source, 3),
        colors = colors?.remapped(source, 3),
        uvs = uvs?.remapped(source, 2),
        jointIndices = jointIndices?.remapped(source, 4),
        jointWeights = jointWeights?.remapped(source, 4),
    )
}

/** For each kept vertex, the first original vertex that collapsed into it (-1 when none did). */
private fun sourceVertices(vertexRemap: IntArray, count: Int): IntArray {
    val source = IntArray(count) { -1 }
    for (original in vertexRemap.indices) {
        val kept = vertexRemap[original]
        if (kept in 0 until count && source[kept] == -1) source[kept] = original
    }
    return source
}

/** A per-vertex attribute of [width] components, carried over from each kept vertex's source. */
private fun FloatArray.remapped(source: IntArray, width: Int) =
    FloatArray(source.size * width) { i -> this[source[i / width].coerceAtLeast(0) * width + i % width] }

private fun IntArray.remapped(source: IntArray, width: Int) =
    IntArray(source.size * width) { i -> this[source[i / width].coerceAtLeast(0) * width + i % width] }
