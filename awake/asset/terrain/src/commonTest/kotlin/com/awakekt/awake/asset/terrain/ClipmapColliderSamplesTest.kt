/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain

import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.asset.terrain.clipmap.clipmapColliderFactor
import com.awakekt.awake.asset.terrain.clipmap.upsampledSamples
import com.awakekt.awake.core.math.Vec3f
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** A collider's samples at the clipmap's level-0 spacing, each the bilinear height drawn there (#1768). */
class ClipmapColliderSamplesTest {
    private val level0 = TerrainClipmapConfig()

    @Test
    fun aHeightmapTwiceAsCoarseAsLevelZeroIsUpsampledTwice() {
        assertEquals(2, map(scale = 4f).clipmapColliderFactor(level0))
        assertEquals(4, map(scale = 8f).clipmapColliderFactor(level0))
    }

    @Test
    fun aHeightmapAsFineAsLevelZeroOrNotAWholeMultipleKeepsItsSamples() {
        assertEquals(1, map(scale = 2f).clipmapColliderFactor(level0))
        assertEquals(1, map(scale = 1f).clipmapColliderFactor(level0))
        assertEquals(1, map(scale = 3f).clipmapColliderFactor(level0))
        assertEquals(1, map(scaleX = 4f, scaleZ = 8f).clipmapColliderFactor(level0), "x and z must agree on one factor")
    }

    @Test
    fun aFactorThatDoesNotFitTheBudgetIsLoweredToOneThatDoes() {
        val big = Heightmap(FloatArray(513 * 513), 513, 513, Vec3f(8f, 1f, 8f))

        assertEquals(4, big.clipmapColliderFactor(level0, maxSide = 2049))
        assertEquals(2, big.clipmapColliderFactor(level0, maxSide = 1025))
        assertEquals(1, big.clipmapColliderFactor(level0, maxSide = 1024))
    }

    @Test
    fun theSaddleCentreIsTheDrawnCentreVertexNotTheDiagonal() {
        // a = 0, b = 4, c = 4, d = 0: the raw heightfield's centre is (a + d) / 2 = 0, drawn it is 2.
        val saddle = Heightmap(floatArrayOf(0f, 4f, 0f, 4f, 0f, 4f, 0f, 4f, 0f), 3, 3, Vec3f(4f, 1f, 4f))

        val samples = saddle.upsampledSamples(2)

        assertEquals(25, samples.size)
        assertEquals(2f, samples[1 * 5 + 1], "the first quad's centre")
        assertEquals(2f, samples[0 * 5 + 1], "the midpoint of a and b")
        assertEquals(4f, samples[0 * 5 + 2], "an original sample is kept")
    }

    @Test
    fun everyUpsampledSampleIsTheBilinearHeightThere() {
        val random = Random(1768)
        val heightmap = Heightmap(FloatArray(9 * 9) { random.nextFloat() * 8f - 4f }, 9, 9, Vec3f(4f, 1.5f, 4f))
        val factor = 2
        val samples = heightmap.upsampledSamples(factor)
        val side = 8 * factor + 1

        for (z in 0 until side) {
            for (x in 0 until side) {
                val worldX = heightmap.minX + x * 4f / factor
                val worldZ = heightmap.minZ + z * 4f / factor
                assertEquals(heightmap.heightAtWorld(worldX, worldZ), samples[z * side + x] * 1.5f, 1e-4f, "at $x, $z")
            }
        }
    }

    @Test
    fun factorOneIsTheSamples() {
        val heightmap = map(scale = 1f)

        assertContentEquals(heightmap.copySamples(), heightmap.upsampledSamples(1))
    }

    private fun map(scale: Float = 1f, scaleX: Float = scale, scaleZ: Float = scale) =
        Heightmap(FloatArray(5 * 5) { it.toFloat() }, 5, 5, Vec3f(scaleX, 1f, scaleZ))
}
