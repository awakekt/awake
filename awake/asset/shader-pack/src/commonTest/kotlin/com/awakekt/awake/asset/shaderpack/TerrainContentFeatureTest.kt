/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.PagedHeightmap
import com.awakekt.awake.asset.terrain.TerrainPageLayout
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.terrain.PagedTerrain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class TerrainContentFeatureTest {

    @Test
    fun terrainsWithSameClipmapConfigDeclareEqualGeometryKeys() {
        val config = TerrainClipmapConfig(ringCount = 3, ringResolution = 16, baseSpacing = 2f)
        val first = terrainContentFeature(PackShaderSets.Terrain, heightmap(1f), config).resolve(RenderBackend.Vulkan)
        val second = terrainContentFeature(PackShaderSets.Terrain, heightmap(5f), config.copy()).resolve(RenderBackend.Vulkan)

        val firstKey = assertNotNull(first.geometryKey)
        val secondKey = assertNotNull(second.geometryKey)
        assertEquals(firstKey, secondKey)
        assertEquals(firstKey.hashCode(), secondKey.hashCode())
    }

    @Test
    fun terrainsWithDifferentClipmapConfigsDeclareDistinctGeometryKeys() {
        val small = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f)
        val wide = TerrainClipmapConfig(ringCount = 3, ringResolution = 16, baseSpacing = 2f)

        val first = terrainContentFeature(PackShaderSets.Terrain, heightmap(1f), small).resolve(RenderBackend.Vulkan)
        val second = terrainContentFeature(PackShaderSets.Terrain, heightmap(1f), wide).resolve(RenderBackend.Vulkan)

        assertNotEquals(assertNotNull(first.geometryKey), assertNotNull(second.geometryKey))
    }

    @Test
    fun pagedAndStandardTerrainsWithSameConfigShareGeometryKey() {
        val config = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f)
        val layout = TerrainPageLayout(0, 0, 1, 1, 4f, 4)
        val paged = PagedTerrain(
            PagedHeightmap(layout, Heightmap(FloatArray(4), 2, 2, Vec3f(4f, 1f, 4f))),
            capacity = 2,
        )

        val standardFeature = terrainContentFeature(PackShaderSets.Terrain, heightmap(1f), config).resolve(RenderBackend.WebGpu)
        val pagedFeature = pagedTerrainContentFeature(paged, config = config).resolve(RenderBackend.WebGpu)

        assertEquals(assertNotNull(standardFeature.geometryKey), assertNotNull(pagedFeature.geometryKey))
    }

    private fun heightmap(peak: Float) = Heightmap(
        samples = FloatArray(4 * 4) { index -> (index % 4) * peak },
        width = 4,
        depth = 4,
        scale = Vec3f(1f, 1f, 1f),
    )
}
