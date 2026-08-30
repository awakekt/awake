/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.terrain

import io.github.awakelab.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeightmapWorldSamplingTest {
    /**
     * A 2x2 map whose corners are all different, so bilinear blending in X and in Z produce
     * distinguishable results. Unit scale except where a test overrides it.
     *
     *     z=1:  2 --- 4
     *     z=0:  0 --- 1
     */
    private fun corners(scale: Vec3f = Vec3f(1f, 1f, 1f)) =
        Heightmap(floatArrayOf(0f, 1f, 2f, 4f), width = 2, depth = 2, scale = scale)

    @Test
    fun returnsTheExactSampleAtGridPoints() {
        val heightmap = corners()

        assertEquals(0f, heightmap.heightAtWorld(0f, 0f))
        assertEquals(1f, heightmap.heightAtWorld(1f, 0f))
        assertEquals(2f, heightmap.heightAtWorld(0f, 1f))
        assertEquals(4f, heightmap.heightAtWorld(1f, 1f))
    }

    @Test
    fun interpolatesAlongEachAxisIndependently() {
        val heightmap = corners()

        assertEquals(0.5f, heightmap.heightAtWorld(0.5f, 0f), "Halfway between 0 and 1 in X.")
        assertEquals(1f, heightmap.heightAtWorld(0f, 0.5f), "Halfway between 0 and 2 in Z.")
    }

    @Test
    fun blendsAllFourSamplesInsideACell() {
        // (0 + 1 + 2 + 4) / 4 at the cell centre.
        assertEquals(1.75f, corners().heightAtWorld(0.5f, 0.5f))
    }

    @Test
    fun appliesTheVerticalScaleUnlikeHeightAt() {
        val heightmap = corners(Vec3f(1f, 10f, 1f))

        assertEquals(4f, heightmap.heightAt(1, 1), "heightAt returns the raw sample.")
        assertEquals(40f, heightmap.heightAtWorld(1f, 1f), "heightAtWorld returns a world Y.")
    }

    @Test
    fun mapsWorldSpaceThroughTheHorizontalScale() {
        // 4 metres per sample in X, 8 in Z, so sample (1, 1) sits at world (4, _, 8).
        val heightmap = corners(Vec3f(4f, 1f, 8f))

        assertEquals(1f, heightmap.heightAtWorld(4f, 0f))
        assertEquals(2f, heightmap.heightAtWorld(0f, 8f))
        assertEquals(0.5f, heightmap.heightAtWorld(2f, 0f), "Halfway across one 4m cell.")
    }

    @Test
    fun returnsNaNOutsideEveryEdge() {
        val heightmap = corners()

        assertTrue(heightmap.heightAtWorld(-0.01f, 0.5f).isNaN(), "Past the -X edge.")
        assertTrue(heightmap.heightAtWorld(1.01f, 0.5f).isNaN(), "Past the +X edge.")
        assertTrue(heightmap.heightAtWorld(0.5f, -0.01f).isNaN(), "Past the -Z edge.")
        assertTrue(heightmap.heightAtWorld(0.5f, 1.01f).isNaN(), "Past the +Z edge.")
    }

    /** The far edge is inside the map, and must not read the sample one past it. */
    @Test
    fun samplesExactlyOnTheFarEdge() {
        val heightmap = Heightmap(
            floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f),
            width = 3,
            depth = 2,
            scale = Vec3f(1f, 1f, 1f),
        )

        assertEquals(2f, heightmap.heightAtWorld(2f, 0f))
        assertEquals(5f, heightmap.heightAtWorld(2f, 1f))
    }

    /**
     * NaN fails the inside-range test rather than reaching `floor`, whose `toInt` would yield 0
     * and quietly return the origin's height as if the query had been valid.
     */
    @Test
    fun rejectsNonFiniteQueries() {
        val heightmap = corners()

        assertTrue(heightmap.heightAtWorld(Float.NaN, 0f).isNaN())
        assertTrue(heightmap.heightAtWorld(0f, Float.NaN).isNaN())
        assertTrue(heightmap.heightAtWorld(Float.POSITIVE_INFINITY, 0f).isNaN())
        assertTrue(heightmap.heightAtWorld(0f, Float.NEGATIVE_INFINITY).isNaN())
    }
}
