/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain

import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The merge exists so a clipmap is one draw against one uniform block. Two things make it wrong
 * in ways that render rather than throw: an index left local to its own level silently draws the
 * wrong triangle, and a missing ring tag reads ring 0's parameters for every vertex.
 */
class MergedClipmapMeshTest {

    private val config = TerrainClipmapConfig(ringCount = 3, ringResolution = 16, baseSpacing = 1f)

    @Test
    fun theMergeKeepsEveryVertexAndIndexOfEveryLevel() {
        val levels = TerrainClipmapGeometry.buildAllClipmapMeshes(config)
        val merged = TerrainClipmapGeometry.buildMergedClipmapMesh(config)

        assertEquals(levels.sumOf { it.vertices.size }, merged.vertices.size)
        assertEquals(levels.sumOf { it.indices.size }, merged.indices.size)
        assertEquals(VertexFormat.PositionNormalColorUv, merged.format)
    }

    /** Every level's tag present, in level order, and none left at the constant white the
     * generators write. */
    @Test
    fun eachVertexCarriesItsOwnRingLevelInTheColourChannel() {
        val levels = TerrainClipmapGeometry.buildAllClipmapMeshes(config)
        val merged = TerrainClipmapGeometry.buildMergedClipmapMesh(config)

        var vertex = 0
        levels.forEachIndexed { level, mesh ->
            repeat(mesh.vertices.size / STRIDE) {
                assertEquals(
                    level.toFloat(),
                    merged.vertices[vertex * STRIDE + COLOR_OFFSET],
                    "Vertex $vertex should be tagged with ring level $level.",
                )
                vertex++
            }
        }
        assertEquals(merged.vertices.size / STRIDE, vertex)
    }

    /**
     * The failure that renders instead of throwing. Level 1's indices are local to level 1's own
     * vertices, so without rebasing they address level 0's block and draw its triangles twice.
     */
    @Test
    fun indicesAreRebasedPastEachPrecedingLevel() {
        val levels = TerrainClipmapGeometry.buildAllClipmapMeshes(config)
        val merged = TerrainClipmapGeometry.buildMergedClipmapMesh(config)

        val firstLevelVertexCount = levels[0].vertices.size / STRIDE
        val secondLevelIndices = merged.indices.copyOfRange(
            levels[0].indices.size,
            levels[0].indices.size + levels[1].indices.size,
        )

        assertTrue(
            secondLevelIndices.all { it >= firstLevelVertexCount },
            "Every index of level 1 must point past level 0's $firstLevelVertexCount vertices.",
        )
        assertTrue(
            merged.indices.all { it < merged.vertices.size / STRIDE },
            "No index may address past the merged vertex count.",
        )
    }

    private companion object {
        const val STRIDE = 11
        const val COLOR_OFFSET = 6
    }
}
