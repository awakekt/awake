/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

class MeshHealerTest {
    @Test
    fun weldsCoincidentVerticesAndRemovesDegenerates() {
        // Two adjacent quads with duplicated boundary vertices at x = 1.0
        // Quad 1: (0,0), (1,0), (0,1), (1,1)
        // Quad 2: (1,0), (2,0), (1,1), (2,1) [duplicated seam at (1,0) and (1,1)]
        val positions = floatArrayOf(
            0f, 0f, 0f, // 0
            1f, 0f, 0f, // 1
            0f, 1f, 0f, // 2
            1f, 1f, 0f, // 3
            1f, 0f, 0f, // 4 (dup of 1)
            2f, 0f, 0f, // 5
            1f, 1f, 0f, // 6 (dup of 3)
            2f, 1f, 0f, // 7
        )
        val indices = intArrayOf(
            0, 1, 2,
            1, 3, 2,
            4, 5, 6,
            5, 7, 6,
        )

        val result = MeshHealer.weld(positions, indices, tolerance = 1e-4f)

        // 8 original vertices welded down to 6 distinct vertices
        assertEquals(6, result.positions.size / 3)
        assertEquals(4, result.indices.size / 3) // 4 triangles remain intact

        // Duplicates 4 and 6 should map to the same indices as 1 and 3
        assertEquals(result.vertexRemap[1], result.vertexRemap[4])
        assertEquals(result.vertexRemap[3], result.vertexRemap[6])
    }

    @Test
    fun weldMeshesCombinesDisjointSubmeshesSeamlessly() {
        val mesh1Positions = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f,
        )
        val mesh1Indices = intArrayOf(0, 1, 2)

        val mesh2Positions = floatArrayOf(
            1f, 0f, 0f, // coincident with mesh1 vertex 1
            0f, 1f, 0f, // coincident with mesh1 vertex 2
            1f, 1f, 0f,
        )
        val mesh2Indices = intArrayOf(0, 2, 1)

        val result = MeshHealer.weldMeshes(
            listOf(mesh1Positions, mesh2Positions),
            listOf(mesh1Indices, mesh2Indices),
            tolerance = 1e-4f,
        )

        // 3 + 3 = 6 vertices welded to 4 unique vertices
        assertEquals(4, result.positions.size / 3)
        assertEquals(2, result.indices.size / 3)
    }
}
