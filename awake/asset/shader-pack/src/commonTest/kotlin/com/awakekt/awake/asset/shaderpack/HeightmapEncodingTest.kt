/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.TexturePixelFormat
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The heightmap is split 16-bit across two 8-bit channels, so this asserts the precision that
 * buys. The shader's own half of the contract is `decodeHeight`; the reconstruction below is
 * that formula in Kotlin, which is what keeps the two honest.
 */
class HeightmapEncodingTest {

    /** `decodeHeight`'s arithmetic: channels arrive already divided by 255. */
    private fun decode(red: Int, green: Int): Float =
        (red / BYTE_MAX * BYTE_STEP + green / BYTE_MAX) / DECODE_DIVISOR

    private fun rampHeightmap(size: Int) = Heightmap(
        samples = FloatArray(size * size) { it.toFloat() / (size * size - 1) },
        width = size,
        depth = size,
        scale = Vec3f(1f, 1f, 1f),
    )

    @Test
    fun aSmoothRampRoundTripsWellInsideAnEightBitStep() {
        val size = 32
        val heightmap = rampHeightmap(size)
        val encoded = heightmap.encodeForSampling()
        val samples = heightmap.copySamples()

        var worstError = 0f
        samples.forEachIndexed { index, expected ->
            val red = encoded.texture.data[index * RG_BYTES].toInt() and 0xFF
            val green = encoded.texture.data[index * RG_BYTES + GREEN].toInt() and 0xFF
            worstError = maxOf(worstError, abs(decode(red, green) - expected))
        }

        assertEquals(TexturePixelFormat.Rg8Unorm, encoded.texture.pixelFormat)
        assertEquals(size * size * RG_BYTES, encoded.texture.data.size)
        assertTrue(
            worstError < EIGHT_BIT_STEP,
            "Worst round-trip error was $worstError, which an 8-bit encoding ($EIGHT_BIT_STEP " +
                "per step) would also manage -- the second channel is not carrying anything.",
        )
    }

    /** The point of the split: more than 256 reachable states. */
    @Test
    fun aRampUsesMoreDistinctStatesThanOneChannelCouldHold() {
        val encoded = rampHeightmap(32).encodeForSampling()

        val states = mutableSetOf<Int>()
        var index = 0
        while (index < encoded.texture.data.size) {
            val red = encoded.texture.data[index].toInt() and 0xFF
            val green = encoded.texture.data[index + GREEN].toInt() and 0xFF
            states += (red shl Byte.SIZE_BITS) or green
            index += RG_BYTES
        }

        assertEquals(32 * 32, states.size, "A strictly increasing ramp should encode no duplicates.")
    }

    /** A flat map has no range to normalise against; it must encode as zero, not divide by it. */
    @Test
    fun aFlatHeightmapEncodesAsZeroWithItsHeightInTheBias() {
        val flat = Heightmap(
            samples = FloatArray(4 * 4) { 7f },
            width = 4,
            depth = 4,
            scale = Vec3f(1f, 2f, 1f),
        )

        val encoded = flat.encodeForSampling()

        assertEquals(TexturePixelFormat.Rg8Unorm, encoded.texture.pixelFormat)
        assertTrue(encoded.texture.data.all { it == 0.toByte() })
        assertEquals(0f, encoded.scale)
        assertEquals(14f, encoded.bias, "7 units at a y scale of 2.")
    }

    /** Split 16-bit height channels must skip mip chains so texture quality reduction never downsamples elevation. */
    @Test
    fun encodedHeightmapUsesBaseLevelLinearSoMipSkipLeavesItUntouched() {
        val encoded = rampHeightmap(65).encodeForSampling()

        assertEquals(TextureFiltering.BaseLevelLinear, encoded.texture.filtering)
        assertEquals(TexturePixelFormat.Rg8Unorm, encoded.texture.pixelFormat)
    }

    private companion object {
        const val RG_BYTES = 2
        const val GREEN = 1
        const val BYTE_MAX = 255f
        const val BYTE_STEP = 256f
        const val DECODE_DIVISOR = 257f

        /** One step of the single-channel encoding this replaced. */
        const val EIGHT_BIT_STEP = 1f / 256f
    }
}
