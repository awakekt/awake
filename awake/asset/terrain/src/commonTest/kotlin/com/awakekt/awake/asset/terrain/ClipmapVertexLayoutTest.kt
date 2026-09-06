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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Exactly what a clipmap vertex contains, slot by slot.
 *
 * The existing clipmap tests count vertices, check the hole is culled and read the colour channel;
 * none of them asserts that a position is in the position slot. That is the gap a packing change
 * slips through: the mesh keeps its vertex count and its triangle count, and renders with UVs read
 * as colours.
 *
 * Corners rather than an interior vertex, because a corner pins the extent, the UV range and the
 * spacing all at once.
 */
class ClipmapVertexLayoutTest {

    private val config = TerrainClipmapConfig()
    private val stride = VertexFormat.PositionNormalColorUv.strideFloats

    private fun vertexAt(mesh: com.awakekt.awake.core.geometry.MeshGeometry, index: Int) =
        mesh.vertices.copyOfRange(index * stride, (index + 1) * stride)

    @Test
    fun theCoreMeshFirstVertexIsItsNegativeCorner() {
        val mesh = TerrainClipmapGeometry.buildCoreMesh(config)
        val n = config.ringResolution
        val halfExtent = (n - 1) * config.baseSpacing * 0.5f

        assertEquals(VertexFormat.PositionNormalColorUv, mesh.format)
        // Position at the -x/-z corner, an up normal, white, UV origin -- in that order.
        assertContentEquals(
            floatArrayOf(-halfExtent, 0f, -halfExtent, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f),
            vertexAt(mesh, 0),
        )
    }

    @Test
    fun theCoreMeshLastVertexIsItsPositiveCornerWithUvOne() {
        val mesh = TerrainClipmapGeometry.buildCoreMesh(config)
        val n = config.ringResolution
        val halfExtent = (n - 1) * config.baseSpacing * 0.5f

        assertContentEquals(
            floatArrayOf(halfExtent, 0f, halfExtent, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f),
            vertexAt(mesh, n * n - 1),
        )
    }

    @Test
    fun aRingMeshUsesItsOwnLevelSpacingAndNothingElseChanges() {
        val level = 1
        val mesh = TerrainClipmapGeometry.buildRingMesh(level, config)
        val n = config.ringResolution
        val halfExtent = (n - 1) * config.spacingForLevel(level) * 0.5f

        // A ring differs from the core in spacing and in which cells it emits -- never in what a
        // vertex contains. This is the assertion that keeps those two builders honest about that.
        assertContentEquals(
            floatArrayOf(-halfExtent, 0f, -halfExtent, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f),
            vertexAt(mesh, 0),
        )
        assertEquals(n * n * stride, mesh.vertices.size)
    }

    @Test
    fun everyVertexCarriesAnUpNormalAndWhite() {
        val mesh = TerrainClipmapGeometry.buildCoreMesh(config)

        for (vertex in 0 until mesh.vertices.size / stride) {
            val base = vertex * stride
            assertContentEquals(
                floatArrayOf(0f, 1f, 0f, 1f, 1f, 1f),
                mesh.vertices.copyOfRange(base + 3, base + 9),
                "vertex $vertex has the wrong normal or colour",
            )
        }
    }
}
