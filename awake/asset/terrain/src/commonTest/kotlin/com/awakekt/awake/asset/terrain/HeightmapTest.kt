/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain

import com.awakekt.awake.core.math.GridOrigin
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HeightmapTest {
    @Test
    fun storesSamplesRowMajorAndOwnsMutableInputs() {
        val samples = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f)
        val scale = Vec3f(2f, 3f, 4f)
        val heightmap = Heightmap(samples, width = 3, depth = 2, scale = scale)

        samples[4] = 99f
        scale.y = 99f

        assertEquals(4f, heightmap.heightAt(1, 1))
        assertEquals(3f, heightmap.scale.y)
        assertEquals(floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f).toList(), heightmap.copySamples().toList())
    }

    @Test
    fun rejectsInvalidDimensionsSamplesAndScale() {
        assertFailsWith<IllegalArgumentException> {
            Heightmap(floatArrayOf(0f), width = 1, depth = 1, scale = Vec3f(1f, 1f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            Heightmap(floatArrayOf(0f, 1f, 2f, 3f), width = 3, depth = 2, scale = Vec3f(1f, 1f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            Heightmap(FloatArray(4), width = 2, depth = 2, scale = Vec3f(1f, 0f, 1f))
        }
    }

    @Test
    fun mutableCopyDoesNotMutateTheImmutableHeightmap() {
        val heightmap = Heightmap(FloatArray(4), width = 2, depth = 2, scale = Vec3f(1f, 1f, 1f))
        val mutable = heightmap.mutableCopy()

        mutable.setHeightAt(x = 1, z = 1, height = 5f)

        assertEquals(0f, heightmap.heightAt(1, 1))
        assertEquals(5f, mutable.heightAt(1, 1))
    }

    @Test
    fun aCornerAnchoredMapKeepsItsOriginThroughAnEditedCopy() {
        // A 3 x 3 ramp rising along X, 2m between samples: corner-anchored it spans 0..4 on X and Z,
        // where a centred copy would span -2..2 and put every world point a sample off.
        val samples = floatArrayOf(0f, 1f, 2f, 0f, 1f, 2f, 0f, 1f, 2f)
        val corner = Heightmap(samples, width = 3, depth = 3, scale = Vec3f(2f, 1f, 2f), origin = GridOrigin.Corner)
        val before = corner.heightAtWorld(worldX = 1f, worldZ = 3f)

        val mutable = corner.mutableCopy()
        assertEquals(GridOrigin.Corner, mutable.origin)
        // An edit away from the sampled cell, as a sculpt stroke elsewhere on the map would be.
        mutable.setHeightAt(x = 2, z = 0, height = 9f)
        val edited = mutable.snapshot()

        assertEquals(GridOrigin.Corner, edited.origin)
        assertEquals(0f, edited.minX)
        assertEquals(0f, edited.minZ)
        assertEquals(0.5f, before)
        assertEquals(before, edited.heightAtWorld(worldX = 1f, worldZ = 3f))
        assertEquals(9f, edited.heightAtWorld(worldX = 4f, worldZ = 0f))
    }

    @Test
    fun aCentredMapStaysCentredThroughAnEditedCopy() {
        val centred = Heightmap(FloatArray(4), width = 2, depth = 2, scale = Vec3f(1f, 1f, 1f))

        val edited = centred.mutableCopy().also { it.setHeightAt(x = 0, z = 0, height = 1f) }.snapshot()

        assertEquals(GridOrigin.Centered, edited.origin)
        assertEquals(-0.5f, edited.minX)
    }
}
