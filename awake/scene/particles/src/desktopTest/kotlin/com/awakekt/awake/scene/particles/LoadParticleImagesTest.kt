/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.particles

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [loadParticleImages] decodes what [loadParticleSprites] does, for the GPU path, and an emitter draws
 * from it. Off the browser the image's pixels are on the CPU, so the material gets exactly the sprite
 * [loadParticleSprites] would have given it; `TextureLoadersBrowserTest` covers the GPU copy.
 */
class LoadParticleImagesTest {
    @Test
    fun imagesHoldThePixelsSpritesDo() = runTest {
        val document = SceneDocument(nodes = listOf(SceneNode("a", components = listOf(SceneParticleEmitter(texture = "dust.png")))))
        val assets = AssetSource { path -> if (path.value == "dust.png") Result.success(PNG) else Result.failure(IllegalStateException("missing")) }

        val image = loadParticleImages(document, assets).getValue("dust.png")
        val sprite = loadParticleSprites(document, assets).getValue("dust.png")

        assertEquals(sprite.width to sprite.height, image.width to image.height)
        assertContentEquals(sprite.data, image.readRgba8())
    }

    @Test
    fun anEmitterDrawsFromItsImageBeforeAHostsSprite() {
        val world = World()
        val node = world.create()
        world.add(node, ParticleEmitterSource(SceneParticleEmitter(texture = "dust.png")))
        val image = TextureAsset(byteArrayOf(1, 2, 3, 4), 1, 1)
        val renderer = RecordingRenderer()

        ParticleContentSystem(renderer, mapOf("dust.png" to TextureAsset(ByteArray(4), 1, 1)), mapOf("dust.png" to image.asGpuImageSource())).update(world, 0f)

        assertNotNull(world.get<ParticleEmitter>(node))
        assertEquals(listOf<TextureAsset?>(image), renderer.textures, "one material, from the image")
    }

    private class RecordingRenderer : NoopRenderer() {
        val textures = mutableListOf<TextureAsset?>()

        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material {
            textures += texture
            return super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)
        }
    }

    private companion object {
        val PNG: ByteArray = ByteArrayOutputStream().also {
            val image = BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, 0x80FF2010.toInt())
            image.setRGB(2, 1, 0xFF10A0FF.toInt())
            ImageIO.write(image, "png", it)
        }.toByteArray()
    }
}
