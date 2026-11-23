/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.asset.gltf.LoadedPrimitive
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GltfGeometryNormalsTest {
    @Test
    fun staticGeometryPreservesAuthoredSmoothNormalsAcrossUnsharedVertices() {
        // Two angled triangles with unshared vertex indices (6 vertices, 6 indices) sharing a smooth authored +Y ridge normal.
        val vertices = floatArrayOf(
            // Tri 0: (-1,0,0), (0,0.5,0), (0,0.5,1)
            -1f, 0f, 0f, 1f, 1f, 1f, 0f, 0f,
            0f, 0.5f, 0f, 1f, 1f, 1f, 0.5f, 0f,
            0f, 0.5f, 1f, 1f, 1f, 1f, 0.5f, 1f,
            // Tri 1: (0,0.5,0), (1,0,0), (0,0.5,1)
            0f, 0.5f, 0f, 1f, 1f, 1f, 0.5f, 0f,
            1f, 0f, 0f, 1f, 1f, 1f, 1f, 0f,
            0f, 0.5f, 1f, 1f, 1f, 1f, 0.5f, 1f,
        )
        val authoredNormals = floatArrayOf(
            -0.4472f, 0.8944f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f,
            0.4472f, 0.8944f, 0f,
            0f, 1f, 0f,
        )
        val primitive = LoadedPrimitive(
            vertices = vertices,
            indices = intArrayOf(0, 1, 2, 3, 4, 5),
            localTransform = Mat4(),
            normals = authoredNormals,
        )

        val geometry = listOf(primitive).toStaticGeometry(textured = true)
        val stride = VertexFormat.PositionNormalColorUv.strideFloats
        val nOff = VertexFormat.PositionNormalColorUv.floatOffsetOf(VertexSemantic.Normal)
        // Ridge vertices 1 and 3 must keep their smooth authored (0, 1, 0) normal instead of flat face normals.
        for (vi in listOf(1, 2, 3, 5)) {
            val base = vi * stride + nOff
            assertTrue(abs(geometry.vertices[base]) < 1e-4f, "Expected smooth nx=0 at vertex $vi, got ${geometry.vertices[base]}")
            assertTrue(abs(geometry.vertices[base + 1] - 1f) < 1e-4f, "Expected smooth ny=1 at vertex $vi, got ${geometry.vertices[base + 1]}")
            assertTrue(abs(geometry.vertices[base + 2]) < 1e-4f, "Expected smooth nz=0 at vertex $vi, got ${geometry.vertices[base + 2]}")
        }
    }

    @Test
    fun staticGeometryTransformsAuthoredNormalsWithInverseTransposeUnderNonUniformScale() {
        val vertices = floatArrayOf(
            0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f,
            1f, 0f, 0f, 1f, 1f, 1f, 1f, 0f,
            0f, 1f, 0f, 1f, 1f, 1f, 0f, 1f,
        )
        val authored = floatArrayOf(
            0.70710677f, 0.70710677f, 0f,
            0.70710677f, 0.70710677f, 0f,
            0.70710677f, 0.70710677f, 0f,
        )
        // Scale X by 2, Y by 1: inverse-transpose scales nx by 0.5 and ny by 1 -> normalized (1/sqrt(5), 2/sqrt(5), 0).
        val transform = Mat4.fromTrs(Vec3f(0f, 0f, 0f), Quat(0f, 0f, 0f, 1f), Vec3f(2f, 1f, 1f))
        val geometry = listOf(
            LoadedPrimitive(vertices = vertices, indices = intArrayOf(0, 1, 2), localTransform = transform, normals = authored),
        ).toStaticGeometry(textured = false)
        val nOff = VertexFormat.PositionNormalColor.floatOffsetOf(VertexSemantic.Normal)
        val expectedX = (1.0 / kotlin.math.sqrt(5.0)).toFloat()
        val expectedY = (2.0 / kotlin.math.sqrt(5.0)).toFloat()
        assertEquals(expectedX, geometry.vertices[nOff], 1e-4f)
        assertEquals(expectedY, geometry.vertices[nOff + 1], 1e-4f)
        assertEquals(0f, geometry.vertices[nOff + 2], 1e-4f)
    }

    @Test
    fun staticGeometryFallsBackToFaceNormalsWhenAuthoredNormalsOmitted() {
        val vertices = floatArrayOf(
            0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f,
            1f, 0f, 0f, 1f, 1f, 1f, 1f, 0f,
            0f, 1f, 0f, 1f, 1f, 1f, 0f, 1f,
        )
        val geometry = listOf(
            LoadedPrimitive(vertices = vertices, indices = intArrayOf(0, 1, 2), localTransform = Mat4(), normals = null),
        ).toStaticGeometry(textured = false)
        val nOff = VertexFormat.PositionNormalColor.floatOffsetOf(VertexSemantic.Normal)
        assertEquals(0f, geometry.vertices[nOff], 1e-4f)
        assertEquals(0f, geometry.vertices[nOff + 1], 1e-4f)
        assertEquals(1f, geometry.vertices[nOff + 2], 1e-4f)
    }
}
