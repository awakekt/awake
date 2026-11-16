/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.TextureFiltering
import kotlin.test.Test
import kotlin.test.assertEquals

/** How many top levels [Renderer.textureMipSkip] leaves out of a chain (#1660). */
class TextureSamplingTest {
    @Test
    fun anRgbaChainSkipsUpToQuarterSizeAndKeepsALevel() {
        assertEquals(2, TextureSampling.levelsToSkip(TextureFiltering.Linear, levels = 9, mipSkip = 5), "clamped to quarter size")
        assertEquals(1, TextureSampling.levelsToSkip(TextureFiltering.Linear, levels = 2, mipSkip = 2), "one level always remains")
        assertEquals(0, TextureSampling.levelsToSkip(TextureFiltering.Nearest, levels = 9, mipSkip = 2), "pixel-exact textures upload whole")
        assertEquals(0, TextureSampling.levelsToSkip(TextureFiltering.Linear, levels = 1, mipSkip = 2), "a single level skips nothing")
    }

    @Test
    fun aCompressedChainSkipsOnlyToALevelOfWholeBlocks() {
        assertEquals(2, TextureSampling.levelsToSkip(image(64, 64, levels = 7), TextureFiltering.Linear, mipSkip = 2), "16 x 16 is whole 4 x 4 blocks")
        assertEquals(1, TextureSampling.levelsToSkip(image(8, 8, levels = 4), TextureFiltering.Linear, mipSkip = 2), "2 x 2 is less than a block; 4 x 4 is one")
        assertEquals(0, TextureSampling.levelsToSkip(image(12, 12, levels = 4), TextureFiltering.Linear, mipSkip = 1), "6 x 6 is not whole blocks")
        assertEquals(0, TextureSampling.levelsToSkip(image(64, 64, levels = 1), TextureFiltering.Linear, mipSkip = 2), "a container with one level")
        assertEquals(0, TextureSampling.levelsToSkip(image(64, 64, levels = 7), TextureFiltering.Nearest, mipSkip = 2), "pixel-exact")
    }

    private fun image(width: Int, height: Int, levels: Int): CompressedTextureImage {
        val format = CompressedTextureFormat.Bc1Rgba
        val bytes = List(levels) { level -> ByteArray(format.byteSize(maxOf(1, width shr level), maxOf(1, height shr level))) }
        return CompressedTextureImage(format, width, height, srgb = false, levels = bytes)
    }
}
