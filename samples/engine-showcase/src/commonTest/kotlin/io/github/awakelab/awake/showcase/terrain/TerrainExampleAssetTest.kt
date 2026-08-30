/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.terrain

import io.github.awakelab.awake.core.geometry.VertexFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class TerrainExampleAssetTest {
    @Test
    fun visualMeshUsesTheCollisionShapeSamplesAtTheSameCornerOrigin() {
        val stride = VertexFormat.PositionNormalColor.strideFloats
        val x = 3
        val z = 5
        val vertexOffset = (z * TerrainExampleAsset.SAMPLE_COUNT + x) * stride
        val shape = TerrainExampleAsset.collisionShape

        assertEquals(x * TerrainExampleAsset.scale.x, TerrainExampleAsset.geometry.vertices[vertexOffset])
        assertEquals(
            shape.heightAt(x, z) * TerrainExampleAsset.scale.y,
            TerrainExampleAsset.geometry.vertices[vertexOffset + 1],
        )
        assertEquals(z * TerrainExampleAsset.scale.z, TerrainExampleAsset.geometry.vertices[vertexOffset + 2])
        assertEquals(
            (TerrainExampleAsset.SAMPLE_COUNT - 1) * (TerrainExampleAsset.SAMPLE_COUNT - 1) * 6,
            TerrainExampleAsset.geometry.indices.size,
        )
    }
}
