/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.GpuMaterial
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.passes.sprites.SpriteRenderBatch
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.RenderFeatureContext3D
import com.awakekt.awake.scene.scene2d.SceneSprite
import com.awakekt.awake.scene.scene2d.Sprite
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/** A texture the resolver forgets is read from its files again: an edited image, a fixed one, or another project's. */
class AssetSourceTextureForgetTest {
    private val reads = mutableListOf<String>()

    /** The images there are now, by path, at these sizes; any other path is missing. */
    private var images = mapOf(HERO to SHEET)
    private val files = AssetSource { path ->
        reads += path.value
        images[path.value]?.let { Result.success(png(it)) } ?: Result.failure(IllegalStateException("no ${path.value}"))
    }

    @Test
    fun aTextureThatFailedIsReadAgainOnceForgotten() = runTest {
        val textures = resolver()
        textures.texture(GONE)
        advanceUntilIdle()
        assertIs<SceneTextureLoad.Failed>(textures.texture(GONE))

        images = mapOf(GONE to SHEET)
        assertIs<SceneTextureLoad.Failed>(textures.texture(GONE), "a failure is kept until it is forgotten")
        textures.forgetTexture(GONE)
        assertSame(SceneTextureLoad.Loading, textures.texture(GONE))
        advanceUntilIdle()

        assertEquals(SHEET, assertIs<SceneTextureLoad.Loaded>(textures.texture(GONE)).image.size)
        assertEquals(listOf(GONE, GONE), reads)
        textures.close()
    }

    @Test
    fun aLoadThatFinishedBeforeItsTextureWasForgottenIsDropped() = runTest {
        val textures = resolver()
        textures.texture(HERO)
        advanceUntilIdle() // finished, and not yet received by the frame

        images = mapOf(HERO to EDITED_SHEET)
        textures.forgetTexture(HERO)
        assertSame(SceneTextureLoad.Loading, textures.texture(HERO), "the old image does not arrive as the new one")
        advanceUntilIdle()

        assertEquals(EDITED_SHEET, assertIs<SceneTextureLoad.Loaded>(textures.texture(HERO)).image.size)
        assertEquals(listOf(HERO, HERO), reads)
        textures.close()
    }

    @Test
    fun forgettingEveryTextureInTheSceneReadsEachAgainFromTheFilesItReadsNow() = runTest {
        images = mapOf(HERO to SHEET, TILES to SHEET)
        val textures = resolver()
        val library = SceneAssetLibrary(emptyMap(), emptyMap(), emptyMap(), dynamicResolvers = listOf(textures))
        listOf(HERO, TILES).forEach(textures::texture)
        advanceUntilIdle()
        listOf(HERO, TILES).forEach { assertIs<SceneTextureLoad.Loaded>(textures.texture(it)) }

        images = mapOf(HERO to EDITED_SHEET, TILES to EDITED_SHEET) // another project, at the same paths
        library.forgetTextures()
        listOf(HERO, TILES).forEach(textures::texture)
        advanceUntilIdle()

        listOf(HERO, TILES).forEach { assertEquals(EDITED_SHEET, assertIs<SceneTextureLoad.Loaded>(textures.texture(it)).image.size) }
        assertEquals(listOf(HERO, TILES, HERO, TILES), reads)
        textures.close()
    }

    @Test
    fun aSpriteWhoseFileFailedDrawsOnceItIsFixedAndTheSceneForgetsIt() = runTest {
        val textures = resolver()
        val library = SceneAssetLibrary(emptyMap(), emptyMap(), emptyMap(), dynamicResolvers = listOf(textures))
        val renderer = Textured()
        val sprites = SceneSpriteRenderFeature(SpriteRenderBatch(renderer, library::texture)).also(library::addTextureUploads)
        val world = World()
        world.create().also {
            world.add(it, Transform())
            world.add(it, Sprite(SceneSprite(GONE, columns = 2)))
        }
        val context = RenderFeatureContext3D(Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 20f)), 0f, 1f)
        fun frame(): List<Pair<Int, Int>> = sprites.collect(world, context).draws.map { renderer.sizes.getValue(it.material) }

        frame()
        advanceUntilIdle()
        assertEquals(emptyList(), frame(), "the file is missing")
        images = mapOf(GONE to SHEET)
        advanceUntilIdle()
        assertEquals(emptyList(), frame(), "a failed file is not read again by itself")

        library.forgetTexture(GONE)
        frame()
        advanceUntilIdle()

        assertEquals(listOf(SHEET), frame())
        assertEquals(listOf(GONE, GONE), reads)
        sprites.forgetTextures()
        textures.close()
    }

    private fun TestScope.resolver() = AssetSourceTextureResolver(files, StandardTestDispatcher(testScheduler))

    private val TextureAsset.size: Pair<Int, Int> get() = width to height

    /** Records the image size of every textured material. */
    private class Textured : NoopRenderer() {
        val sizes = HashMap<GpuMaterial, Pair<Int, Int>>()

        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material {
            val made = object : Material by super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures) {}
            if (texture != null) sizes[made] = texture.width to texture.height
            return made
        }
    }

    private companion object {
        const val HERO = "hero.png"
        const val TILES = "tiles.png"
        const val GONE = "gone.png"

        /** A two-cell sheet, in pixels, before and after an edit. */
        val SHEET = 4 to 2
        val EDITED_SHEET = 8 to 2

        fun png(size: Pair<Int, Int>): ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(size.first, size.second, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()
    }
}
