/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry.generate

import com.awakekt.awake.core.geometry.VertexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MeshGeometryGeneratorsTest {

    @Test
    fun generateCubeProduces24VerticesAnd36Indices() {
        val geom = generate {
            cube(size = 2f, colored = true)
        }

        assertEquals(24 * 9, geom.vertices.size)
        assertEquals(36, geom.indices.size)
        assertEquals(VertexFormat.PositionNormalColor, geom.format)
        // Verify extent of size 2f (-1f to 1f)
        assertEquals(-1f, geom.vertices[0])
    }

    @Test
    fun generatePlaneProduces4VerticesAnd6Indices() {
        val geom = generate {
            plane(size = 10f, colored = false)
        }

        assertEquals(4 * 9, geom.vertices.size)
        assertEquals(6, geom.indices.size)
        assertEquals(VertexFormat.PositionNormalColor, geom.format)
        assertEquals(-5f, geom.vertices[0])
    }

    @Test
    fun generatePlaneWindsUpwardForBackFaceCulling() {
        val plane = generate { plane(size = 2f, colored = false) }
        val a = plane.indices[0] * 9
        val b = plane.indices[1] * 9
        val c = plane.indices[2] * 9
        val abx = plane.vertices[b] - plane.vertices[a]
        val abz = plane.vertices[b + 2] - plane.vertices[a + 2]
        val acx = plane.vertices[c] - plane.vertices[a]
        val acz = plane.vertices[c + 2] - plane.vertices[a + 2]
        assertTrue(abz * acx - abx * acz > 0f, "plane triangles must face +Y")
    }

    @Test
    fun generateCubeComputesTightBounds() {
        val geom = generate {
            cube(size = 2f, colored = true)
        }
        val bounds = geom.bounds
        assertEquals(-1f, bounds?.min?.x)
        assertEquals(1f, bounds?.max?.x)
    }

    @Test
    fun generateCubeWindsEveryFaceOutwardForBackFaceCulling() {
        val cube = generate { cube(size = 2f, colored = true) }
        cube.indices.toList().chunked(TRIANGLE_INDICES).forEach { triangle ->
            val a = triangle[0] * FLOATS_PER_VERTEX
            val b = triangle[1] * FLOATS_PER_VERTEX
            val c = triangle[2] * FLOATS_PER_VERTEX
            val abx = cube.vertices[b] - cube.vertices[a]
            val aby = cube.vertices[b + 1] - cube.vertices[a + 1]
            val abz = cube.vertices[b + 2] - cube.vertices[a + 2]
            val acx = cube.vertices[c] - cube.vertices[a]
            val acy = cube.vertices[c + 1] - cube.vertices[a + 1]
            val acz = cube.vertices[c + 2] - cube.vertices[a + 2]
            val crossX = aby * acz - abz * acy
            val crossY = abz * acx - abx * acz
            val crossZ = abx * acy - aby * acx
            val outwardDot = crossX * cube.vertices[a + NORMAL_OFFSET] +
                crossY * cube.vertices[a + NORMAL_OFFSET + 1] +
                crossZ * cube.vertices[a + NORMAL_OFFSET + 2]
            assertTrue(outwardDot > 0f, "triangle $triangle is not outward-wound")
        }
    }

    @Test
    fun generateThrowsWhenEmpty() {
        assertFailsWith<IllegalArgumentException> {
            generate { }
        }
    }

    private companion object {
        const val TRIANGLE_INDICES = 3
        const val FLOATS_PER_VERTEX = 9
        const val NORMAL_OFFSET = 3
    }
}
