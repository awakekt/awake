/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.geometry.generate

import io.github.awakelab.awake.core.geometry.VertexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    fun generateCubeComputesTightBounds() {
        val geom = generate {
            cube(size = 2f, colored = true)
        }
        val bounds = geom.bounds
        assertEquals(-1f, bounds?.min?.x)
        assertEquals(1f, bounds?.max?.x)
    }

    @Test
    fun generateThrowsWhenEmpty() {
        assertFailsWith<IllegalArgumentException> {
            generate { }
        }
    }
}
