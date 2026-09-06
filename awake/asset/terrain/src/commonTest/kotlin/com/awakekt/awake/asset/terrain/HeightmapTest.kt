/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain

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
}
