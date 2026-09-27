/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TerrainControlMapTest {

    @Test
    fun fourLayersOrFewerAreKeptExactlyStrongestFirst() {
        val weights = mapOf(2 to 1f, 0 to 3f)
        val reduction = TerrainControlMap.reduce(1, 1, layerCount = 3) { layer, _, _ -> weights[layer] ?: 0f }
        val map = reduction.controlMap

        assertEquals(listOf(0, 2), listOf(map.layerAt(0, 0, 0), map.layerAt(0, 0, 1)))
        assertEquals(255, (0 until CONTROL_SLOTS).sumOf { map.weightAt(0, 0, it) })
        // 3:1 is 191.25 : 63.75; rounding leftovers go to the strongest layer.
        assertEquals(192, map.weightAt(0, 0, 0))
        assertEquals(0f, reduction.maxDroppedWeight)
        assertEquals(0, reduction.texelsOverSlots)
    }

    /** Six layers at one texel: the two weakest go, and the report says how much that cost. */
    @Test
    fun moreThanFourLayersKeepTheStrongestAndReportWhatWasDropped() {
        val reduction = TerrainControlMap.reduce(1, 1, layerCount = 6) { layer, _, _ -> (layer + 1).toFloat() }
        val map = reduction.controlMap

        assertEquals(listOf(5, 4, 3, 2), (0 until CONTROL_SLOTS).map { map.layerAt(0, 0, it) })
        assertEquals(255, (0 until CONTROL_SLOTS).sumOf { map.weightAt(0, 0, it) })
        assertEquals(3f / 21f, reduction.maxDroppedWeight, 1e-6f)
        assertEquals(1, reduction.texelsOverSlots)
    }

    @Test
    fun aTexelWithNoWeightShowsTheFirstLayer() {
        val map = TerrainControlMap.reduce(2, 1, layerCount = 2) { _, _, _ -> 0f }.controlMap

        assertEquals(0, map.layerAt(1, 0, 0))
        assertEquals(255, map.weightAt(1, 0, 0))
    }

    @Test
    fun texelsAreRowMajorLikeAHeightmap() {
        val map = TerrainControlMap.reduce(3, 2, layerCount = 6) { layer, x, z -> if (layer == z * 3 + x) 1f else 0f }.controlMap

        assertEquals(5, map.layerAt(2, 1, 0))
        assertEquals(1, map.layerAt(1, 0, 0))
    }

    @Test
    fun negativeOrNonFiniteWeightsAreRejected() {
        assertFailsWith<IllegalArgumentException> { TerrainControlMap.reduce(1, 1, 1) { _, _, _ -> -1f } }
        assertFailsWith<IllegalArgumentException> { TerrainControlMap.reduce(1, 1, 1) { _, _, _ -> Float.NaN } }
    }

    @Test
    fun theBinaryCodecRoundTrips() {
        val map = TerrainControlMap.reduce(3, 2, layerCount = 5) { layer, x, z -> ((layer + x + z) % 3).toFloat() }.controlMap

        val decoded = TerrainControlMapCodec.decode(TerrainControlMapCodec.encode(map))

        assertEquals(3, decoded.width)
        assertEquals(2, decoded.depth)
        assertContentEquals(map.copyIndices(), decoded.copyIndices())
        assertContentEquals(map.copyWeights(), decoded.copyWeights())
    }

    @Test
    fun theCodecRejectsAForeignOrTruncatedFile() {
        val encoded = TerrainControlMapCodec.encode(TerrainControlMap.reduce(2, 2, 1) { _, _, _ -> 1f }.controlMap)

        assertFailsWith<IllegalArgumentException> { TerrainControlMapCodec.decode("PNG!".encodeToByteArray() + encoded.copyOfRange(4, encoded.size)) }
        assertFailsWith<IllegalArgumentException> { TerrainControlMapCodec.decode(encoded.copyOf(encoded.size - 1)) }
    }

    @Test
    fun highestLayerIgnoresSlotsWithoutWeight() {
        val map = TerrainControlMap.reduce(1, 1, layerCount = 9) { layer, _, _ -> if (layer == 3) 1f else 0f }.controlMap

        assertEquals(3, map.highestLayer())
        assertTrue(map.weightAt(0, 0, 1) == 0)
    }
}
