/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry

import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class MeshGeometryBuilderTest {
    @Test
    fun buildsSemanticVerticesAndTopologyWithoutPackedBufferBoilerplate() {
        val builder = MeshGeometryBuilder(VertexFormat.PositionNormalColor, vertexCount = 4)
        builder.vertex(0, Vec3f(-1f, 0f, -1f), Vec3f.UP, Vec3f(1f, 1f, 1f))
        builder.vertex(1, Vec3f(1f, 0f, -1f), Vec3f.UP, Vec3f(1f, 1f, 1f))
        builder.vertex(2, Vec3f(1f, 0f, 1f), Vec3f.UP, Vec3f(1f, 1f, 1f))
        builder.vertex(3, Vec3f(-1f, 0f, 1f), Vec3f.UP, Vec3f(1f, 1f, 1f))
        builder.quad(0, 2, 1, 3)

        val mesh = builder.build()

        assertContentEquals(intArrayOf(0, 2, 1, 2, 0, 3), mesh.indices)
        assertContentEquals(
            floatArrayOf(
                -1f, 0f, -1f, 0f, 1f, 0f, 1f, 1f, 1f,
                1f, 0f, -1f, 0f, 1f, 0f, 1f, 1f, 1f,
                1f, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f,
                -1f, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f,
            ),
            mesh.vertices,
        )
    }

    @Test
    fun rejectsTopologyOutsideTheDeclaredVertexRange() {
        val builder = MeshGeometryBuilder(VertexFormat.PositionColor, vertexCount = 1)
        builder.triangle(0, 1, 0)

        assertFailsWith<IllegalArgumentException> { builder.build() }
    }
}
