/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.texturecook

import com.awakekt.awake.asset.ktx.TextureRole
import kotlin.test.Test
import kotlin.test.assertEquals

class MipChainTest {

    @Test
    fun colourIsAveragedInLinearLightNotInSrgbValues() {
        // Two black and two white pixels: half the light, which sRGB stores as 188, not 128.
        val image = image(2, 2, listOf(0, 0, 0, 255), listOf(255, 255, 255, 255), listOf(0, 0, 0, 255), listOf(255, 255, 255, 255))

        val colour = MipChain.halve(image, TextureRole.Color)
        val data = MipChain.halve(image, TextureRole.Data)

        assertEquals(listOf(188, 188, 188, 255), pixel(colour), "sRGB colour")
        assertEquals(listOf(128, 128, 128, 255), pixel(data), "linear data")
    }

    @Test
    fun aNormalMapsMeanIsScaledBackToUnitLength() {
        // Two normals tilted 45 degrees either way along x: their mean is straight out, length 0.71.
        val left = listOf(37, 128, 218, 255)
        val right = listOf(218, 128, 218, 255)

        val normal = pixel(MipChain.halve(image(2, 2, left, right, left, right), TextureRole.Normal))

        assertEquals(listOf(128, 128, 255, 255), normal, "a unit normal straight out, not one of length 0.71")
    }

    @Test
    fun theChainRunsDownToOneByOneAndAnOddSideRoundsDown() {
        val levels = MipChain.levels(image(16, 5), TextureRole.Color, mipmaps = true)

        assertEquals(listOf(16 to 5, 8 to 2, 4 to 1, 2 to 1, 1 to 1), levels.map { it.width to it.height })
        assertEquals(1, MipChain.levels(image(16, 5), TextureRole.Color, mipmaps = false).size)
    }

    private fun image(width: Int, height: Int, vararg pixels: List<Int>): RgbaImage {
        val bytes = ByteArray(width * height * CHANNELS)
        pixels.forEachIndexed { i, rgba -> rgba.forEachIndexed { c, value -> bytes[i * CHANNELS + c] = value.toByte() } }
        return RgbaImage(width, height, bytes)
    }

    private fun pixel(image: RgbaImage): List<Int> = (0 until CHANNELS).map { image.channel(0, 0, it) }
}
