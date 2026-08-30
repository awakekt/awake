/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.terrain

import io.github.awakelab.awake.asset.terrain.clipmap.ClipmapRingState
import io.github.awakelab.awake.asset.terrain.clipmap.TerrainClipmapConfig
import io.github.awakelab.awake.asset.terrain.clipmap.TerrainClipmapGeometry
import io.github.awakelab.awake.asset.terrain.clipmap.TerrainClipmapTracker
import io.github.awakelab.awake.asset.terrain.splat.TerrainSplatWeightMap
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainClipmapGeometryTest {

    @Test
    fun clipmapConfigComputesConsistentExtents() {
        val config = TerrainClipmapConfig(ringCount = 5, ringResolution = 64, baseSpacing = 2.0f)
        assertEquals(5, config.ringCount)
        assertEquals(64, config.ringResolution)
        assertEquals(2.0f, config.baseSpacing)
        assertEquals(126.0f, config.coreExtent) // (64 - 1) * 2.0 = 126m

        assertEquals(2.0f, config.spacingForLevel(0))
        assertEquals(4.0f, config.spacingForLevel(1))
        assertEquals(8.0f, config.spacingForLevel(2))
        assertEquals(16.0f, config.spacingForLevel(3))
        assertEquals(32.0f, config.spacingForLevel(4))

        assertEquals(126.0f, config.extentForLevel(0))
        assertEquals(252.0f, config.extentForLevel(1))
        assertEquals(504.0f, config.extentForLevel(2))
        assertEquals(1008.0f, config.extentForLevel(3))
        assertEquals(2016.0f, config.extentForLevel(4))
    }

    @Test
    fun coreMeshEmitsFullWatertightGrid() {
        val config = TerrainClipmapConfig(ringCount = 4, ringResolution = 32, baseSpacing = 1.0f)
        val core = TerrainClipmapGeometry.buildCoreMesh(config)

        assertEquals(VertexFormat.PositionNormalColorUv, core.format)
        val expectedVertices = 32 * 32
        val stride = VertexFormat.PositionNormalColorUv.strideFloats
        assertEquals(expectedVertices * stride, core.vertices.size)

        val expectedQuads = (32 - 1) * (32 - 1)
        val expectedIndices = expectedQuads * 6
        assertEquals(expectedIndices, core.indices.size)

        // All indices are in valid range
        for (index in core.indices) {
            assertTrue(index in 0 until expectedVertices, "Index $index out of bounds [0, $expectedVertices)")
        }
    }

    @Test
    fun ringMeshCullsInnerHoleProperly() {
        val config = TerrainClipmapConfig(ringCount = 4, ringResolution = 32, baseSpacing = 1.0f)
        val ring1 = TerrainClipmapGeometry.buildRingMesh(1, config)

        assertEquals(VertexFormat.PositionNormalColorUv, ring1.format)
        val expectedVertices = 32 * 32
        val stride = VertexFormat.PositionNormalColorUv.strideFloats
        assertEquals(expectedVertices * stride, ring1.vertices.size)

        // Total quads: (31 * 31) = 961. Inner hole quads: (16 * 16) = 256. Ring quads = 705.
        val totalQuads = (32 - 1) * (32 - 1)
        val holeQuads = (32 / 2) * (32 / 2)
        val expectedQuads = totalQuads - holeQuads
        val expectedIndices = expectedQuads * 6
        assertEquals(expectedIndices, ring1.indices.size)
    }

    @Test
    fun allClipmapMeshesBuildExpectedSet() {
        val config = TerrainClipmapConfig(ringCount = 6, ringResolution = 32, baseSpacing = 2.0f)
        val meshes = TerrainClipmapGeometry.buildAllClipmapMeshes(config)
        assertEquals(6, meshes.size)
    }

    @Test
    fun clipmapTrackerSnapsCameraToGridSteps() {
        val config = TerrainClipmapConfig(ringCount = 4, ringResolution = 64, baseSpacing = 2.0f)
        val tracker = TerrainClipmapTracker(config)

        // Move camera to arbitrary world coordinates
        val rings = tracker.update(Vec3f(15.7f, 100.0f, -33.2f))
        assertEquals(4, rings.size)

        // Level 0: spacing = 2.0 -> snapped to multiple of 2.0 (15.7 -> 16.0, -33.2 -> -34.0)
        assertEquals(16.0f, rings[0].snappedCenter.x)
        assertEquals(-34.0f, rings[0].snappedCenter.z)

        // Level 1: spacing = 4.0 -> snapped to multiple of 4.0 (15.7 -> 16.0, -33.2 -> -32.0)
        assertEquals(16.0f, rings[1].snappedCenter.x)
        assertEquals(-32.0f, rings[1].snappedCenter.z)

        // Level 2: spacing = 8.0 -> snapped to multiple of 8.0 (15.7 -> 16.0, -33.2 -> -32.0)
        assertEquals(16.0f, rings[2].snappedCenter.x)
        assertEquals(-32.0f, rings[2].snappedCenter.z)

        // Level 3: spacing = 16.0 -> snapped to multiple of 16.0 (15.7 -> 16.0, -33.2 -> -32.0)
        assertEquals(16.0f, rings[3].snappedCenter.x)
        assertEquals(-32.0f, rings[3].snappedCenter.z)
    }

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
