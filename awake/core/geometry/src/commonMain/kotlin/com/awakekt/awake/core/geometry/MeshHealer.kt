/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry

import kotlin.math.floor

/**
 * Spatial grid-accelerated mesh welding and seam repair.
 *
 * Merges coincident or near-coincident vertices within a spatial [tolerance] distance into a
 * single canonical vertex index and removes degenerate collapsed triangles. Works on single
 * meshes or multi-part modular character meshes before or after decimation.
 *
 * ### Example Usage
 * ```kotlin
 * // Weld coincident vertices across multiple modular slot meshes
 * val welded = MeshHealer.weldMeshes(
 *     positionsList = listOf(headPositions, torsoPositions, legsPositions),
 *     indicesList = listOf(headIndices, torsoIndices, legsIndices),
 *     tolerance = 1e-4f
 * )
 *
 * val unifiedPositions = welded.positions
 * val unifiedIndices = welded.indices
 * val remap = welded.vertexRemap
 * ```
 */
@Suppress("LongParameterList", "NestedBlockDepth")
object MeshHealer {
    private const val POSITION_COMPONENTS = 3
    private const val VERTICES_PER_TRIANGLE = 3
    private const val HASH_MULTIPLIER = 31L
    private const val HASH_SEED = 1125899906842597L

    /**
     * The result of welding coincident vertices.
     *
     * @property positions The deduplicated vertex positions.
     * @property indices The updated triangle indices referencing [positions].
     * @property vertexRemap Map from each original vertex index to its corresponding index in [positions].
     */
    data class Result(
        val positions: FloatArray,
        val indices: IntArray,
        val vertexRemap: IntArray,
    )

    /**
     * Welds coincident vertices in [positions] within [tolerance] distance.
     *
     * @param positions Flat (x, y, z) vertex positions.
     * @param indices Flat triangle vertex index buffer.
     * @param tolerance Maximum Euclidean distance between vertices to be considered coincident.
     */
    fun weld(
        positions: FloatArray,
        indices: IntArray,
        tolerance: Float = 1e-4f,
    ): Result {
        val vertexCount = positions.size / POSITION_COMPONENTS
        if (vertexCount == 0 || indices.isEmpty()) {
            return Result(positions.copyOf(), indices.copyOf(), IntArray(vertexCount) { it })
        }

        val tolSq = (tolerance * tolerance).toDouble()
        val cellSize = if (tolerance > 0f) tolerance.toDouble() else 1e-4
        val invCellSize = 1.0 / cellSize

        val grid = HashMap<Long, MutableList<Int>>()
        val vertexOwner = IntArray(vertexCount) { it }
        val canonicalVertices = ArrayList<Int>()

        for (i in 0 until vertexCount) {
            val px = positions[i * POSITION_COMPONENTS].toDouble()
            val py = positions[i * POSITION_COMPONENTS + 1].toDouble()
            val pz = positions[i * POSITION_COMPONENTS + 2].toDouble()

            val cx = floor(px * invCellSize).toLong()
            val cy = floor(py * invCellSize).toLong()
            val cz = floor(pz * invCellSize).toLong()

            val matched = findCoincident(grid, positions, cx, cy, cz, px, py, pz, tolSq)
            if (matched != -1) {
                vertexOwner[i] = matched
            } else {
                canonicalVertices.add(i)
                val key = spatialHash(cx, cy, cz)
                grid.getOrPut(key) { ArrayList(4) }.add(i)
            }
        }

        return buildWeldResult(positions, indices, canonicalVertices, vertexOwner)
    }

    private fun findCoincident(
        grid: Map<Long, List<Int>>,
        positions: FloatArray,
        cx: Long,
        cy: Long,
        cz: Long,
        px: Double,
        py: Double,
        pz: Double,
        tolSq: Double,
    ): Int {
        for (dx in -1L..1L) {
            for (dy in -1L..1L) {
                for (dz in -1L..1L) {
                    val key = spatialHash(cx + dx, cy + dy, cz + dz)
                    val bucket = grid[key] ?: continue
                    for (other in bucket) {
                        val ox = positions[other * POSITION_COMPONENTS].toDouble()
                        val oy = positions[other * POSITION_COMPONENTS + 1].toDouble()
                        val oz = positions[other * POSITION_COMPONENTS + 2].toDouble()
                        val dSq = (px - ox) * (px - ox) + (py - oy) * (py - oy) + (pz - oz) * (pz - oz)
                        if (dSq <= tolSq) return other
                    }
                }
            }
        }
        return -1
    }

    private fun buildWeldResult(
        positions: FloatArray,
        indices: IntArray,
        canonicalVertices: List<Int>,
        vertexOwner: IntArray,
    ): Result {
        val vertexCount = vertexOwner.size
        val newIndexOf = IntArray(vertexCount) { -1 }
        val newPositions = FloatArray(canonicalVertices.size * POSITION_COMPONENTS)

        for ((newIdx, origIdx) in canonicalVertices.withIndex()) {
            newIndexOf[origIdx] = newIdx
            newPositions[newIdx * POSITION_COMPONENTS] = positions[origIdx * POSITION_COMPONENTS]
            newPositions[newIdx * POSITION_COMPONENTS + 1] = positions[origIdx * POSITION_COMPONENTS + 1]
            newPositions[newIdx * POSITION_COMPONENTS + 2] = positions[origIdx * POSITION_COMPONENTS + 2]
        }

        val vertexRemap = IntArray(vertexCount) { newIndexOf[vertexOwner[it]] }
        val newIndices = ArrayList<Int>(indices.size)
        val triangleCount = indices.size / VERTICES_PER_TRIANGLE
        for (t in 0 until triangleCount) {
            val i0 = vertexRemap[indices[t * VERTICES_PER_TRIANGLE]]
            val i1 = vertexRemap[indices[t * VERTICES_PER_TRIANGLE + 1]]
            val i2 = vertexRemap[indices[t * VERTICES_PER_TRIANGLE + 2]]
            if (i0 != i1 && i1 != i2 && i0 != i2) {
                newIndices.add(i0)
                newIndices.add(i1)
                newIndices.add(i2)
            }
        }

        return Result(newPositions, newIndices.toIntArray(), vertexRemap)
    }

    private fun spatialHash(cx: Long, cy: Long, cz: Long): Long {
        var h = HASH_SEED
        h = HASH_MULTIPLIER * h + cx
        h = HASH_MULTIPLIER * h + cy
        h = HASH_MULTIPLIER * h + cz
        return h
    }

    /**
     * Welds multiple independent mesh buffers together into a single unified topology.
     */
    fun weldMeshes(
        positionsList: List<FloatArray>,
        indicesList: List<IntArray>,
        tolerance: Float = 1e-4f,
    ): Result {
        val totalVertexFloats = positionsList.sumOf { it.size }
        val totalIndexCount = indicesList.sumOf { it.size }

        val combinedPositions = FloatArray(totalVertexFloats)
        val combinedIndices = IntArray(totalIndexCount)

        var vertexOffset = 0
        var indexOffset = 0

        for (i in positionsList.indices) {
            val pos = positionsList[i]
            val ind = indicesList[i]
            pos.copyInto(combinedPositions, destinationOffset = vertexOffset * POSITION_COMPONENTS)
            for (j in ind.indices) {
                combinedIndices[indexOffset + j] = ind[j] + vertexOffset
            }
            vertexOffset += pos.size / POSITION_COMPONENTS
            indexOffset += ind.size
        }

        return weld(combinedPositions, combinedIndices, tolerance)
    }
}
