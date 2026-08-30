/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.terrain

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class HeightmapMeshBuilderTest {
    @Test
    fun buildsRectangularGridWithCornerCoordinatesUpwardNormalsAndDerivedStride() {
        val heightmap = Heightmap(
            samples = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f),
            width = 3,
            depth = 2,
            scale = Vec3f(2f, 3f, 4f),
        )

        val mesh = heightmap.toPositionNormalColorMesh(Color.White)
        val stride = VertexFormat.PositionNormalColor.strideFloats
        val lastOffset = 5 * stride

        assertEquals(6 * stride, mesh.vertices.size)
        assertEquals(4f, mesh.vertices[lastOffset])
        assertEquals(0f, mesh.vertices[lastOffset + 1])
        assertEquals(4f, mesh.vertices[lastOffset + 2])
        assertEquals(0f, mesh.vertices[lastOffset + 3], absoluteTolerance = 0.00001f)
        assertEquals(1f, mesh.vertices[lastOffset + 4])
        assertEquals(0f, mesh.vertices[lastOffset + 5], absoluteTolerance = 0.00001f)
        assertContentEquals(intArrayOf(0, 3, 1, 1, 3, 4, 1, 4, 2, 2, 4, 5), mesh.indices)
    }

    @Test
    fun usesScaleAwareSlopesAndTheProvidedColourPolicy() {
        val heightmap = Heightmap(
            samples = floatArrayOf(0f, 1f, 2f, 3f),
            width = 2,
            depth = 2,
            scale = Vec3f(2f, 2f, 2f),
        )

        val mesh = heightmap.toPositionNormalColorMesh { x, z, _ -> Color(x.toFloat(), z.toFloat(), 0f) }

        assertEquals(-0.40824828f, mesh.vertices[3], absoluteTolerance = 0.00001f)
        assertEquals(0.40824828f, mesh.vertices[4], absoluteTolerance = 0.00001f)
        assertEquals(-0.81649655f, mesh.vertices[5], absoluteTolerance = 0.00001f)
        assertEquals(1f, mesh.vertices[VertexFormat.PositionNormalColor.strideFloats + 6])
    }
}
