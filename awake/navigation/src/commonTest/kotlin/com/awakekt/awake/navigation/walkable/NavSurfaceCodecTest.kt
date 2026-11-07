/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NavSurfaceCodecTest {
    private val tile = bakeNavSurface(
        NavRegion(-7.5f, 3.25f, 31, 17, 0.5f),
        NavTestWorlds.FLAT,
        NavAgent(),
        NavAreas(obstacles = listOf(NavTestWorlds.BUILDING)),
    )

    @Test
    fun aTileSurvivesTheRoundTrip() {
        val decoded = NavSurfaceCodec.decode(NavSurfaceCodec.encode(tile))
        assertEquals(tile.width, decoded.width)
        assertEquals(tile.depth, decoded.depth)
        assertEquals(tile.sampleSize, decoded.sampleSize)
        assertEquals(tile.originX, decoded.originX)
        assertEquals(tile.originZ, decoded.originZ)
        assertContentEquals(tile.copyClearance(), decoded.copyClearance())
    }

    @Test
    fun theSameTileEncodesToTheSameBytes() {
        assertContentEquals(NavSurfaceCodec.encode(tile), NavSurfaceCodec.encode(NavSurfaceCodec.decode(NavSurfaceCodec.encode(tile))))
    }

    @Test
    fun malformedBytesAreRejected() {
        val bytes = NavSurfaceCodec.encode(tile)
        assertFailsWith<IllegalArgumentException> { NavSurfaceCodec.decode(bytes.copyOf(bytes.size - 1)) }
        assertFailsWith<IllegalArgumentException> { NavSurfaceCodec.decode(bytes.copyOf(10)) }
        assertFailsWith<IllegalArgumentException> { NavSurfaceCodec.decode(bytes.copyOf().also { it[0] = 0 }) }
        assertFailsWith<IllegalArgumentException> { NavSurfaceCodec.decode(bytes.copyOf().also { it[4] = 9 }) }
    }
}
