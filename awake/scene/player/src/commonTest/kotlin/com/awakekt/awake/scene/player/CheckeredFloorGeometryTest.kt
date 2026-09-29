/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CheckeredFloorGeometryTest {
    @Test
    fun oneGroutPlaneAndOneQuadPerTileCoverTheFloor() {
        val floor = checkeredFloorGeometry(size = 4f, tiles = 2)
        val stride = floor.format.strideFloats

        assertEquals((1 + 2 * 2) * 4, floor.vertices.size / stride)
        assertEquals((1 + 2 * 2) * 6, floor.indices.size)
        val xs = (0 until floor.vertices.size / stride).map { floor.vertices[it * stride] }
        assertEquals(-2f, xs.min())
        assertEquals(2f, xs.max())
    }

    @Test
    fun neighbouringTilesAlternateShade() {
        val floor = checkeredFloorGeometry(size = 4f, tiles = 2)
        val stride = floor.format.strideFloats
        // Colour follows position and normal; quad 0 is the grout, quads 1 and 2 are neighbours.
        fun redOfQuad(quad: Int) = floor.vertices[quad * 4 * stride + COLOR_OFFSET]

        assertNotEquals(redOfQuad(1), redOfQuad(2))
        assertNotEquals(redOfQuad(0), redOfQuad(1))
    }

    private companion object {
        const val COLOR_OFFSET = 6
    }
}
