/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.vulkan.renderer.uploadTexture
import com.awakekt.awake.vulkan.texture.Texture
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Renderer.textureMipSkip` on Vulkan (#1660): a texture only ever sampled by UV -- a material's, or a
 * content feature's that is never written -- uploads without its top levels, while its `width` and
 * `height` stay the source size, so a pixel rect turned into UVs with them samples the same place.
 * The device fails on any validation error when it is destroyed, a leaked image among them.
 */
class VulkanMipSkipTest {
    @Test
    fun aSkippedUploadIsExactlyHalfOrQuarterSizeAndKeepsItsSourceSize() = withProbeRenderer(scale = 1f to 1f) { renderer, _ ->
        for (skip in 0..2) {
            renderer.textureMipSkip = skip
            val material = renderer.uploadTexture(gradient())
            assertEquals(TEXELS shr skip, material.uploadedWidth, "a material texture at skip $skip")
            assertEquals(TEXELS shr skip, material.uploadedHeight)
            assertEquals(LEVELS - skip, material.uploadedMipLevels)
            assertEquals(TEXELS, material.width, "the source size is kept")

            // How VulkanContentFeatureGpu uploads a terrain layer it never writes.
            val content = Texture.skippingMips(renderer.graphicsDevice, renderer.transferContext::runOneTimeCommands, gradient())
            assertEquals(TEXELS shr skip, content.uploadedWidth, "a terrain layer at skip $skip")
            assertEquals(TEXELS, content.height)
            content.destroy()
        }
    }

    @Test
    fun pixelExactUiAndSingleLevelTexturesUploadWhole() = withProbeRenderer(scale = 1f to 1f) { renderer, _ ->
        renderer.textureMipSkip = 5
        assertEquals(2, renderer.textureMipSkip, "clamped to quarter size")
        for (filtering in listOf(TextureFiltering.Nearest, TextureFiltering.BaseLevelLinear)) {
            assertEquals(TEXELS, renderer.uploadTexture(gradient(filtering)).uploadedWidth, "$filtering uploads whole")
        }
        // UI images, glyph atlases and written textures are made with the constructor, which never skips.
        val ui = Texture(renderer.graphicsDevice, renderer.transferContext::runOneTimeCommands, gradient().data, TEXELS, TEXELS)
        assertEquals(TEXELS, ui.uploadedWidth, "a UI image uploads whole")
        assertEquals(LEVELS, ui.uploadedMipLevels)
        ui.destroy()
    }

    /**
     * A sprite-sheet draw: the probe samples the pixel rect (128, 0)-(256, 128) of the texture, turned
     * into UVs by dividing by `width`, as an atlas or a nine-slice does. A half-size upload keeps
     * `width`, so the rect maps to the same UVs and the frame matches the full upload within filtering.
     */
    @Test
    fun aPixelRectDrawsTheSameRegionAtHalfSize() = withProbeRenderer(scale = RECT / TEXELS to RECT / TEXELS, offset = RECT / TEXELS to 0f) { renderer, probe ->
        val frames = (0..1).map { skip ->
            renderer.textureMipSkip = skip
            val texture = renderer.uploadTexture(gradient())
            assertEquals(TEXELS, texture.width, "the rect is divided by the source width at skip $skip")
            probe.writeContentTextures(mapOf(1 to texture))
            renderer.frame()
        }
        val reds = frames[0].indices.step(4).map { frames[0][it].toInt() and 0xFF }
        assertTrue(reds.max() - reds.min() > RECT / 2, "the frame shows the rect's red ramp, not one colour: ${reds.min()}..${reds.max()}")
        val worst = frames[0].indices.maxOf { abs((frames[0][it].toInt() and 0xFF) - (frames[1][it].toInt() and 0xFF)) }
        println("VulkanMipSkip: the rect's worst channel difference between skip 0 and 1 is $worst of 255")
        assertTrue(worst <= TOLERANCE, "the same region, within filtering: $worst")
    }

    private companion object {
        const val TEXELS = 256
        const val LEVELS = 9
        const val RECT = 128f
        const val TOLERANCE = 4

        /** Red along x, green along y, blue by half: smooth, so a box-filtered level matches it closely. */
        fun gradient(filtering: TextureFiltering = TextureFiltering.Linear) = TextureAsset(
            data = ByteArray(TEXELS * TEXELS * 4) { index ->
                val pixel = index / 4
                val x = pixel % TEXELS
                val y = pixel / TEXELS
                when (index % 4) {
                    0 -> x.toByte()
                    1 -> y.toByte()
                    2 -> (if (x < TEXELS / 2) 64 else 192).toByte()
                    else -> -1
                }
            },
            width = TEXELS,
            height = TEXELS,
            filtering = filtering,
        )
    }
}
