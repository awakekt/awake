/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.compose

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ImageTexturesTest {

    private val image = ImageBitmap(1, 1, byteArrayOf(-1, 0, 0, -1))

    @Test
    fun anImageIsUploadedOnceHoweverOftenItIsDrawn() {
        val renderer = CountingRenderer()
        val textures = ImageTextures()
        val first = textures.materialFor(renderer, image)
        repeat(300) { assertSame(first, textures.materialFor(renderer, image)) }
        assertEquals(1, renderer.created)
        textures.dispose()
        assertEquals(1, renderer.destroyed)
    }

    @Test
    fun compositorSwapsTheBitmapForItsMaterial() {
        val renderer = CountingRenderer()
        val compositor = GraphicsLayerCompositor()
        val output = compositor.composite(
            renderer = renderer,
            primitives = listOf(UiDrawPrimitive.Texture(0f, 0f, 10f, 10f, image)),
            layers = emptyList(),
            font = UiFonts.default(),
            viewportWidth = 10,
            viewportHeight = 10,
        )
        val material = (output.single() as UiDrawPrimitive.Texture).material
        assertEquals(true, material is Material, "the renderer receives a material, not the bitmap")
        compositor.dispose()
        assertEquals(1, renderer.destroyed)
    }
}

private class CountingRenderer : NoopRenderer() {
    var created = 0
    var destroyed = 0

    override fun createMaterial(
        texture: TextureAsset?,
        renderTarget: RenderTarget?,
        uniformFloatCount: Int,
        pbrTextures: PbrTextureSet?,
    ): Material {
        created += 1
        val base = super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)
        return object : Material by base {
            override fun destroy() {
                destroyed += 1
                base.destroy()
            }
        }
    }
}
