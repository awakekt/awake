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

class ClipmapBlockPartitionTest {
    @Test
    fun presetsProvideProgressiveTriangleBudgets() {
        val defaultMesh = TerrainClipmapGeometry.buildMergedClipmapMesh(TerrainClipmapConfig.Default)
        val mobileMesh = TerrainClipmapGeometry.buildMergedClipmapMesh(TerrainClipmapConfig.Mobile)
        val compactMesh = TerrainClipmapGeometry.buildMergedClipmapMesh(TerrainClipmapConfig.Compact)

        assertTrue(
            mobileMesh.indices.size * 4 < defaultMesh.indices.size,
            "Mobile preset (${mobileMesh.indices.size} indices) must cut >4x indices vs Default (${defaultMesh.indices.size})",
        )
        assertTrue(
            compactMesh.indices.size < mobileMesh.indices.size,
            "Compact preset (${compactMesh.indices.size}) must be smaller than Mobile (${mobileMesh.indices.size})",
        )
    }

    @Test
    fun mergedBlocksContiguouslyPartitionEveryTriangleWithExactLocalBounds() {
        val configs = listOf(
            TerrainClipmapConfig.Default,
            TerrainClipmapConfig.Mobile,
            TerrainClipmapConfig.Compact,
            TerrainClipmapConfig(ringCount = 3, ringResolution = 18, baseSpacing = 1.5f),
        )
        val stride = VertexFormat.PositionNormalColorUv.strideFloats

        for (config in configs) {
            val mesh = TerrainClipmapGeometry.buildMergedClipmapMesh(config)
            val blocks = TerrainClipmapGeometry.mergedClipmapBlocks(config)
            val coreIndexCount = TerrainClipmapGeometry.coreIndexCount(config)

            assertTrue(blocks.isNotEmpty())
            var expectedCursor = 0
            for (block in blocks) {
                assertEquals(expectedCursor, block.firstIndex, "blocks must be contiguous with no gaps or overlaps")
                assertTrue(block.indexCount > 0 && block.indexCount % 6 == 0, "block indexCount must be positive multiple of 6")
                assertTrue(block.maxLocalX > block.minLocalX && block.maxLocalZ > block.minLocalZ)

                // Every vertex referenced by this block must lie strictly inside its declared local AABB.
                for (i in block.firstIndex until block.firstIndex + block.indexCount) {
                    val v = mesh.indices[i]
                    val x = mesh.vertices[v * stride]
                    val z = mesh.vertices[v * stride + 2]
                    val level = mesh.vertices[v * stride + 6].toInt()
                    assertEquals(block.level, level, "block vertex ring tag mismatch")
                    assertTrue(
                        x in (block.minLocalX - 1e-4f)..(block.maxLocalX + 1e-4f),
                        "vertex x=$x outside [${block.minLocalX}, ${block.maxLocalX}]",
                    )
                    assertTrue(
                        z in (block.minLocalZ - 1e-4f)..(block.maxLocalZ + 1e-4f),
                        "vertex z=$z outside [${block.minLocalZ}, ${block.maxLocalZ}]",
                    )
                }
                expectedCursor += block.indexCount
            }
            assertEquals(mesh.indices.size, expectedCursor, "blocks must sum to exact merged index count")

            val coreBlocks = blocks.filter { it.level == 0 }
            assertEquals(16, coreBlocks.size, "core grid must partition into 4x4 = 16 blocks")
            assertEquals(coreIndexCount, coreBlocks.sumOf { it.indexCount }, "core blocks must exactly span coreIndexCount")
            assertEquals(0, coreBlocks.first().firstIndex)
            assertEquals(coreIndexCount, coreBlocks.last().firstIndex + coreBlocks.last().indexCount)
        }
    }

    @Test
    fun blockPartitioningPreservesMainDiagonalJoltWindingOnEveryCell() {
        val config = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f)
        val core = TerrainClipmapGeometry.buildCoreMesh(config)
        val side = config.ringResolution + 1
        var i = 0
        while (i < core.indices.size) {
            val topLeft = core.indices[i]
            val bottomLeft = core.indices[i + 1]
            val bottomRight = core.indices[i + 2]
            val topLeft2 = core.indices[i + 3]
            val bottomRight2 = core.indices[i + 4]
            val topRight = core.indices[i + 5]
            assertEquals(topLeft, topLeft2)
            assertEquals(bottomLeft, topLeft + side)
            assertEquals(bottomRight, bottomLeft + 1)
            assertEquals(bottomRight, bottomRight2)
            assertEquals(topRight, topLeft + 1)
            i += 6
        }
    }
}
