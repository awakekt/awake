/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.vulkan.models.info.VkFilter
import com.awakekt.awake.vulkan.models.info.VkSamplerAddressMode
import com.awakekt.awake.vulkan.models.info.VkSamplerCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSamplerMipmapMode
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.texture.Texture
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A distance-field atlas is sampled at its base level.
 *
 * The MTSDF atlas is 32 texels to the em, and a UI draws it at 11 to 16 pixels, so with a mip chain
 * the sampler's implicit LOD picks mip 1 or 2. A box filter averages distances, which erodes a
 * thin stem: the same 11 pixel run had 13 solid pixels (at least 200 of 255) on the mipmapped
 * atlas against 194 on the base level.
 */
class RendererHeadlessUiFontAtlasTest {

    /** Lays [text] out as [com.awakekt.awake.compose.foundation.text.TextRun] does, at a whole-pixel origin. */
    private fun glyphs(text: String, font: UiFont, px: Float): List<UiDrawPrimitive.Glyph> {
        val pad = ((LINE_HEIGHT - px * font.lineHeightEm) / 2f).coerceAtLeast(0f)
        var pen = ORIGIN
        val out = ArrayList<UiDrawPrimitive.Glyph>()
        for (ch in text) {
            val rect = font.glyphFor(ch.code, FontWeight.Normal)
            if (rect != null) {
                out += UiDrawPrimitive.Glyph(
                    x = pen + rect.offsetXEm * px,
                    y = ORIGIN + pad + rect.offsetYEm * px,
                    w = rect.widthEm * px,
                    h = rect.heightEm * px,
                    u0 = rect.u0,
                    v0 = rect.v0,
                    u1 = rect.u1,
                    v1 = rect.v1,
                    color = Color.White,
                    font = font,
                )
            }
            pen += font.advanceFor(ch.code, px, FontWeight.Normal)
        }
        return out
    }

    /** How many pixels of [text] at [px] are solid, that is at least 200 of 255, on the red channel. */
    private fun solidPixels(renderer: Renderer, font: UiFont, px: Float): Int {
        val target = renderer.createRenderTarget(WIDTH, HEIGHT)
        try {
            renderer.drawUiToTexture(target, glyphs(TEXT, font, px), font)
            val data = runBlocking { renderer.readPixels(target) }.data
            var solid = 0
            for (i in 0 until WIDTH * HEIGHT) if ((data[i * 4].toInt() and 0xFF) >= SOLID) solid++
            return solid
        } finally {
            target.destroy()
        }
    }

    /** The same atlas with the mip chain the font atlas used to be uploaded with. */
    private fun Renderer.useMippedAtlas(font: UiFont) {
        fontAtlases[font] = Texture(
            graphicsDevice,
            transferContext::runOneTimeCommands,
            font.atlasPixelsRgba,
            font.atlasWidth,
            font.atlasHeight,
            samplerCreateInfo = VkSamplerCreateInfo(
                magFilter = VkFilter.VK_FILTER_LINEAR,
                minFilter = VkFilter.VK_FILTER_LINEAR,
                addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                mipmapMode = VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST,
            ),
        )
    }

    /**
     * Negative control: with `ensureFontAtlas` uploading a mip chain (the code before this change),
     * "Mesh Terrain Texture" at 11px has 13 solid pixels against the 194 on the base level. The
     * control kept in the test uploads the same chain for a second font and must stay well under
     * the production atlas.
     */
    @Test
    fun smallTextIsSampledFromTheAtlasBaseLevel() {
        RendererHeadlessUiGlyphBaselineTest.withSharedHeadlessRenderer { renderer ->
            val production = UiFonts.weightedSans(12)
            val mipped = UiFonts.weightedSans(12)
            check(production !== mipped) { "the control needs its own atlas upload" }
            renderer.useMippedAtlas(mipped)

            for ((px, minimum) in listOf(11f to 150, 12f to 140)) {
                val crisp = solidPixels(renderer, production, px)
                val soft = solidPixels(renderer, mipped, px)
                assertTrue(crisp >= minimum, "${px}px text has $crisp solid pixels, expected at least $minimum")
                assertTrue(soft * 4 < crisp * 3, "the mip chain must not keep up with the base level at ${px}px: $soft against $crisp")
            }
        }
    }

    private companion object {
        const val TEXT = "Mesh Terrain Texture"
        const val WIDTH = 150
        const val HEIGHT = 22
        const val ORIGIN = 4f
        const val LINE_HEIGHT = 16f
        const val SOLID = 200
    }
}
