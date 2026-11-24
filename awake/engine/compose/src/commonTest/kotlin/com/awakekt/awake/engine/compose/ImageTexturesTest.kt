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
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ImageTexturesTest {

    private val image = ImageBitmap(1, 1, byteArrayOf(-1, 0, 0, -1))

    @Test
    fun anImageIsUploadedOnceHoweverOftenItIsDrawn() {
        val renderer = CountingRenderer()
        val textures = ImageTextures()
        val first = textures.materialFor(renderer, image)
        repeat(300) {
            assertSame(first, textures.materialFor(renderer, image))
            textures.endFrame()
        }
        assertEquals(1, renderer.created)
        assertEquals(0, renderer.destroyed)
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

    @Test
    fun unusedImagesAreEvictedAfterIdleWindowAndReuploadedIfDrawnAgain() {
        val renderer = CountingRenderer()
        val compositor = GraphicsLayerCompositor()
        val drawImage = listOf(UiDrawPrimitive.Texture(0f, 0f, 10f, 10f, image))

        val first = (compositor.composite(renderer, drawImage, emptyList(), UiFonts.default(), 10, 10).single() as UiDrawPrimitive.Texture).material
        assertEquals(1, renderer.created)
        assertEquals(0, renderer.destroyed)

        // Stay warm while under the idle frame window.
        repeat((ImageTextures.DEFAULT_MAX_IDLE_FRAMES - 1L).toInt()) {
            compositor.composite(renderer, emptyList(), emptyList(), UiFonts.default(), 10, 10)
        }
        assertEquals(0, renderer.destroyed, "small idle image stays warm within grace window")

        // Crossing maxIdleFrames evicts and destroys the material.
        compositor.composite(renderer, emptyList(), emptyList(), UiFonts.default(), 10, 10)
        assertEquals(1, renderer.destroyed, "unused image is evicted once idle window expires")

        // Drawing the bitmap again uploads cleanly.
        val second = (compositor.composite(renderer, drawImage, emptyList(), UiFonts.default(), 10, 10).single() as UiDrawPrimitive.Texture).material
        assertNotSame(first, second)
        assertEquals(2, renderer.created)
        compositor.dispose()
        assertEquals(2, renderer.destroyed)
    }

    @Test
    fun largeTransientImagesEvictPromptlyAboveIdleBudget() {
        val renderer = CountingRenderer()
        val textures = ImageTextures(minIdleFrames = 2L, maxIdleFrames = 60L, maxIdleBytes = 1024L)
        val total = 20
        var maxLive = 0

        repeat(total) {
            // 32x32x4 = 4096 bytes (> 1024 B idle budget).
            val transient = ImageBitmap(32, 32, ByteArray(32 * 32 * ImageBitmap.BYTES_PER_PIXEL))
            textures.materialFor(renderer, transient)
            textures.endFrame()
            maxLive = maxOf(maxLive, textures.size)
        }

        assertEquals(total, renderer.created)
        assertTrue(maxLive <= 3, "retained live materials must stay bounded by minIdleFrames+1, was $maxLive")
        assertTrue(renderer.destroyed >= total - 3, "superseded large bitmaps must be destroyed, destroyed=${renderer.destroyed}")

        textures.dispose()
        assertEquals(total, renderer.destroyed)
        assertEquals(0, textures.size)
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
