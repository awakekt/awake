/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain

import com.awakekt.awake.asset.terrain.clipmap.ClipmapRingState
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapGeometry
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapTracker
import com.awakekt.awake.asset.terrain.splat.TerrainSplatWeightMap
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainClipmapGeometryTest {

    private companion object {
        const val CAMERA_STEPS = 400
    }

    @Test
    fun clipmapConfigComputesConsistentExtents() {
        val config = TerrainClipmapConfig(ringCount = 5, ringResolution = 64, baseSpacing = 2.0f)
        assertEquals(5, config.ringCount)
        assertEquals(64, config.ringResolution)
        assertEquals(2.0f, config.baseSpacing)
        assertEquals(128.0f, config.coreExtent) // 64 cells * 2.0 = 128m

        assertEquals(2.0f, config.spacingForLevel(0))
        assertEquals(4.0f, config.spacingForLevel(1))
        assertEquals(8.0f, config.spacingForLevel(2))
        assertEquals(16.0f, config.spacingForLevel(3))
        assertEquals(32.0f, config.spacingForLevel(4))

        assertEquals(128.0f, config.extentForLevel(0))
        assertEquals(256.0f, config.extentForLevel(1))
        assertEquals(512.0f, config.extentForLevel(2))
        assertEquals(1024.0f, config.extentForLevel(3))
        assertEquals(2048.0f, config.extentForLevel(4))
    }

    @Test
    fun coreMeshEmitsFullWatertightGrid() {
        val config = TerrainClipmapConfig(ringCount = 4, ringResolution = 32, baseSpacing = 1.0f)
        val core = TerrainClipmapGeometry.buildCoreMesh(config)

        assertEquals(VertexFormat.PositionNormalColorUv, core.format)
        val expectedVertices = 33 * 33
        val stride = VertexFormat.PositionNormalColorUv.strideFloats
        assertEquals(expectedVertices * stride, core.vertices.size)

        val expectedIndices = 32 * 32 * 6
        assertEquals(expectedIndices, core.indices.size)

        for (index in core.indices) {
            assertTrue(index in 0 until expectedVertices, "Index $index out of bounds [0, $expectedVertices)")
        }
    }

    @Test
    fun ringMeshCullsOnlyTheCellsTheFinerLevelAlwaysCovers() {
        val config = TerrainClipmapConfig(ringCount = 4, ringResolution = 32, baseSpacing = 1.0f)
        val ring1 = TerrainClipmapGeometry.buildRingMesh(1, config)

        assertEquals(VertexFormat.PositionNormalColorUv, ring1.format)
        val stride = VertexFormat.PositionNormalColorUv.strideFloats
        assertEquals(33 * 33 * stride, ring1.vertices.size)

        // 32 x 32 cells, less the 12 x 12 middle (cells 10 until 22) that the finer level covers
        // however both have snapped.
        assertEquals((32 * 32 - 12 * 12) * 6, ring1.indices.size)
    }

    @Test
    fun allClipmapMeshesBuildExpectedSet() {
        val config = TerrainClipmapConfig(ringCount = 6, ringResolution = 32, baseSpacing = 2.0f)
        val meshes = TerrainClipmapGeometry.buildAllClipmapMeshes(config)
        assertEquals(6, meshes.size)
    }

    /**
     * What makes the rings watertight, wherever the camera is: each level's border lies on the next
     * level's grid (so the two share vertices), every level contains the one inside it, and the
     * cells a ring's mesh leaves out are always under the finer level.
     */
    @Test
    fun levelsNestOnSharedGridLinesForEveryCameraPosition() {
        val config = TerrainClipmapConfig(ringCount = 5, ringResolution = 16, baseSpacing = 1f)
        val tracker = TerrainClipmapTracker(config)
        val holes = (1 until config.ringCount).associateWith { missingCells(TerrainClipmapGeometry.buildRingMesh(it, config), config.ringResolution) }

        repeat(CAMERA_STEPS) { step ->
            val rings = tracker.update(Vec3f(step * 0.37f - 70f, 0f, 55f - step * 0.61f))
            rings.forEach { ring ->
                listOf(ring.minX, ring.maxX, ring.minZ, ring.maxZ).forEach { edge ->
                    assertTrue(onGrid(edge, ring.spacing * 2f), "level ${ring.level} border $edge is off the coarser grid")
                }
            }
            rings.zipWithNext { finer, coarser ->
                assertTrue(
                    finer.minX >= coarser.minX && finer.maxX <= coarser.maxX && finer.minZ >= coarser.minZ && finer.maxZ <= coarser.maxZ,
                    "level ${coarser.level} does not contain level ${finer.level} at step $step",
                )
                holes.getValue(coarser.level).forEach { (x, z) ->
                    val cellMinX = coarser.minX + x * coarser.spacing
                    val cellMinZ = coarser.minZ + z * coarser.spacing
                    assertTrue(
                        cellMinX >= finer.minX && cellMinX + coarser.spacing <= finer.maxX &&
                            cellMinZ >= finer.minZ && cellMinZ + coarser.spacing <= finer.maxZ,
                        "cell ($x, $z) cut from level ${coarser.level} is not under level ${finer.level} at step $step",
                    )
                }
            }
        }
    }

    /** Cells of a `cells` x `cells` grid mesh that no triangle covers. */
    private fun missingCells(mesh: com.awakekt.awake.core.geometry.MeshGeometry, cells: Int): Set<Pair<Int, Int>> {
        val side = cells + 1
        val covered = mesh.indices.toList().chunked(3).mapTo(HashSet()) { triangle ->
            triangle.minOf { it % side } to triangle.minOf { it / side }
        }
        return (0 until cells).flatMap { x -> (0 until cells).map { z -> x to z } }.filterNot { it in covered }.toSet()
    }

    private fun onGrid(value: Float, step: Float): Boolean = kotlin.math.abs(value / step - kotlin.math.round(value / step)) < 1e-4f

    @Test
    fun clipmapTrackerComputesMorphFactorCorrectly() {
        val ring = ClipmapRingState(
            level = 1,
            spacing = 4.0f,
            snappedCenter = Vec3f(0.0f, 0.0f, 0.0f),
            halfExtent = 100.0f,
        )

        // Center vertex has 0 morph
        assertEquals(0.0f, ring.computeMorphFactor(0.0f, 0.0f, morphWidth = 0.25f))

        // Inside inner 75% has 0 morph
        assertEquals(0.0f, ring.computeMorphFactor(50.0f, 0.0f, morphWidth = 0.25f))
        assertEquals(0.0f, ring.computeMorphFactor(75.0f, 0.0f, morphWidth = 0.25f))

        // In outer 25% (between 75.0m and 100.0m) morph ramps 0 -> 1
        val midMorph = ring.computeMorphFactor(87.5f, 0.0f, morphWidth = 0.25f)
        assertTrue(midMorph in 0.49f..0.51f, "Mid morph factor should be ~0.5, got $midMorph")

        // At perimeter (100.0m) morph is 1.0
        assertEquals(1.0f, ring.computeMorphFactor(100.0f, 0.0f, morphWidth = 0.25f))
    }

    @Test
    fun splatWeightMapSamplesNormalizedValues() {
        val width = 4
        val height = 4
        val bytes = ByteArray(width * height * 4) { index ->
            when (index % 4) {
                0 -> 128.toByte()
                1 -> 64.toByte()
                2 -> 63.toByte()
                3 -> 0.toByte()
                else -> 0.toByte()
            }
        }
        val splat = TerrainSplatWeightMap(width, height, bytes)
        val sampled = splat.sampleWeights(0.5f, 0.5f)
        assertEquals(4, sampled.size)
        val sum = sampled[0] + sampled[1] + sampled[2] + sampled[3]
        assertTrue(sum in 0.99f..1.01f, "Sampled sum must be ~1.0; was $sum.")
    }
}
