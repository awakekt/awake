/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.terrain

import com.awakekt.awake.core.geometry.VertexFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class TerrainExampleAssetTest {
    @Test
    fun visualMeshUsesTheCollisionShapeSamplesAtTheSameOrigin() {
        val stride = VertexFormat.PositionNormalColor.strideFloats
        val x = 3
        val z = 5
        val vertexOffset = (z * TerrainExampleAsset.SAMPLE_COUNT + x) * stride
        val shape = TerrainExampleAsset.collisionShape

        // Through the map's own origin rather than assuming one: what matters is that the mesh
        // and the collider place sample (x, z) at the SAME world position, which is what stops a
        // prop resting on terrain that is not drawn where it collides.
        assertEquals(
            TerrainExampleAsset.heightmap.minX + x * TerrainExampleAsset.scale.x,
            TerrainExampleAsset.geometry.vertices[vertexOffset],
        )
        assertEquals(
            shape.heightAt(x, z) * TerrainExampleAsset.scale.y,
            TerrainExampleAsset.geometry.vertices[vertexOffset + 1],
        )
        assertEquals(
            TerrainExampleAsset.heightmap.minZ + z * TerrainExampleAsset.scale.z,
            TerrainExampleAsset.geometry.vertices[vertexOffset + 2],
        )
        assertEquals(
            (TerrainExampleAsset.SAMPLE_COUNT - 1) * (TerrainExampleAsset.SAMPLE_COUNT - 1) * 6,
            TerrainExampleAsset.geometry.indices.size,
        )
    }
}
