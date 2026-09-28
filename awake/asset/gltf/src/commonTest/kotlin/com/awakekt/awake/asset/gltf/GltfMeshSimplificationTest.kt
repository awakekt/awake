/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GltfMeshSimplificationTest {

    private fun createTestMesh(): GltfMesh {
        val positions = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f,
            1f, 1f, 0f,
            0f, 1f, 0f,
        )
        val normals = floatArrayOf(
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f,
        )
        val indices = intArrayOf(0, 1, 2, 0, 2, 3)

        return GltfMesh(
            positions = positions,
            normals = normals,
            colors = null,
            uvs = FloatArray(8),
            indices = indices,
        )
    }

    @Test
    fun targetRatioOneReturnsSameMesh() {
        val mesh = createTestMesh()
        val simplified = mesh.simplified(1.0f)
        assertEquals(mesh, simplified)
    }

    @Test
    fun decimationProducesValidMesh() {
        val mesh = createTestMesh()
        val simplified = mesh.simplified(0.5f, lockBoundaries = false)
        assertTrue(simplified.positions.isNotEmpty())
    }
}
