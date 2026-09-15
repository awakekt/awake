/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain.splat

import com.awakekt.awake.core.image.DefaultBitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SplatWeightMapCodecTest {

    @Test
    fun decodesAndNormalizesRgbaWeights() {
        val width = 2
        val height = 1
        // Pixel 0: unnormalized weights [100, 100, 0, 0] -> sum = 200 -> should normalize to sum 255
        // Pixel 1: exact [255, 0, 0, 0]
        val rawBytes = byteArrayOf(
            100,
            100,
            0,
            0,
            255.toByte(),
            0,
            0,
            0,
        )

        val splatMap = SplatWeightMapCodec.decodeRgba(rawBytes, width, height, normalize = true)

        val p0Weights = splatMap.sampleWeights(0f, 0f)
        val p0Sum = p0Weights[0] + p0Weights[1] + p0Weights[2] + p0Weights[3]
        assertEquals(1.0f, p0Sum, 0.01f)

        val p1Weights = splatMap.sampleWeights(1f, 0f)
        assertEquals(1.0f, p1Weights[0], 0.01f)
        assertEquals(0.0f, p1Weights[1], 0.01f)
    }

    @Test
    fun createDefaultSetsTargetLayerTo255() {
        val splatMap = SplatWeightMapCodec.createDefault(width = 4, height = 4, defaultLayer = 1)
        val weights = splatMap.sampleWeights(0.5f, 0.5f)

        assertEquals(0.0f, weights[0], 0.001f)
        assertEquals(1.0f, weights[1], 0.001f)
        assertEquals(0.0f, weights[2], 0.001f)
        assertEquals(0.0f, weights[3], 0.001f)
    }

    @Test
    fun decodesFromBitmap() {
        // Red pixel (layer 0) and Green pixel (layer 1), Alpha = 0
        val pixels = intArrayOf(
            0x00FF0000,
            0x0000FF00,
        )
        val bitmap = DefaultBitmap(width = 2, height = 1, channel = 4, pixels = pixels)
        val splatMap = SplatWeightMapCodec.decodeFromBitmap(bitmap)

        val w0 = splatMap.sampleWeights(0f, 0f)
        assertEquals(1.0f, w0[0], 0.01f)

        val w1 = splatMap.sampleWeights(1f, 0f)
        assertEquals(1.0f, w1[1], 0.01f)
    }

    @Test
    fun rejectsInsufficientBufferSize() {
        assertFailsWith<IllegalArgumentException> {
            SplatWeightMapCodec.decodeRgba(ByteArray(3), width = 1, height = 1)
        }
    }
}
