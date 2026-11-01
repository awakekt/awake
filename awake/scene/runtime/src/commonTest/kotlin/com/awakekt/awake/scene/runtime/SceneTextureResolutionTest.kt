/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRecord
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
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
import com.awakekt.awake.scene.scene2d.SceneTilemap
import com.awakekt.awake.scene.scene2d.Sprite
import com.awakekt.awake.scene.scene2d.Tilemap
import com.awakekt.awake.scene.scene2d.collectSpriteDraws
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A sprite or tilemap draws from a texture registered by name, or one a resolver supplies when it is
 * first drawn; one that isn't available yet, failed, or that nothing supplies draws nothing and is
 * logged once, and never throws.
 */
class SceneTextureResolutionTest {
    private val records = mutableListOf<LogRecord>()
    private val renderer = TexturedMaterials()
    private val world = World()

    @BeforeTest
    fun listen() {
        Log.reset()
        Log.install(LogSink { records += it })
        world.create().also {
            world.add(it, Transform())
            world.add(it, Sprite(SceneSprite("hero.png", columns = 2)))
        }
        world.create().also {
            world.add(it, Transform())
            world.add(it, Tilemap(SceneTilemap("tiles.png", width = 2, height = 1, tiles = listOf(0, 1), columns = 2)))
        }
    }

    @AfterTest
    fun stopListening() {
        Log.reset()
    }

    @Test
    fun anUnregisteredTextureDrawsNothingAndIsLoggedOnceWithoutThrowing() {
        val drawing = Drawing(library())

        repeat(3) { assertEquals(0, drawing.frame(), "frame $it draws nothing") }

        assertEquals(2, log(LogLevel.Error).size, "one error for each texture, however many frames: ${log(LogLevel.Error)}")
        assertTrue(log(LogLevel.Error).all { "is registered or could be resolved" in it && it.endsWith("Not drawn.") })
        assertTrue(renderer.textures.isEmpty(), "nothing uploaded")
        drawing.destroy()
    }

    @Test
    fun aTextureThatLoadsLateIsNotDrawnYetAndDrawsFromTheFrameItArrives() {
        val files = SwitchableTextures()
        val drawing = Drawing(library(files))

        repeat(3) { assertEquals(0, drawing.frame(), "frame $it, before the textures load") }
        files.loaded = true
        val drawn = drawing.frame()
        drawing.frame()

        assertEquals(2, drawn, "the sprite and the tilemap's one chunk draw once their textures have loaded")
        val waiting = log(LogLevel.Info)
        assertEquals(2, waiting.size, "once for each texture, however many frames: $waiting")
        assertTrue(waiting.all { it.endsWith("has not loaded yet. Not drawn yet; it draws once it has.") }, waiting.toString())
        assertEquals(emptyList(), log(LogLevel.Error))
        assertEquals(listOf(SHEET, ATLAS), renderer.textures.map { it.width to it.height }, "each uploaded once, when it arrived")
        assertEquals(mapOf("hero.png" to 4, "tiles.png" to 4), files.asks, "asked on each frame until loaded, and not after")
        drawing.destroy()
    }

    @Test
    fun aTextureThatFailedToLoadIsLoggedOnceAsAnErrorAndNeverThrows() {
        val broken = IllegalStateException("not an image")
        val failing = object : SceneAssetResolver {
            override fun texture(name: String): SceneTextureLoad = SceneTextureLoad.Failed(broken)
        }
        val drawing = Drawing(library(failing))

        repeat(3) { assertEquals(0, drawing.frame()) }

        val errors = records.filter { it.tag == TAG && it.level == LogLevel.Error }
        assertEquals(2, errors.size, "once for each texture: ${errors.map { it.message }}")
        assertTrue(errors.all { it.cause === broken && it.message.endsWith("could not load: not an image. Not drawn.") })
        assertEquals(emptyList(), log(LogLevel.Info), "a failure is not reported as still loading")
        drawing.destroy()
    }

    @Test
    fun aRegisteredTextureIsUsedBeforeAnyResolverIsAsked() {
        val files = SwitchableTextures().apply { loaded = true }
        val registered = TextureAsset(ByteArray(SHEET.first * SHEET.second * RGBA), SHEET.first, SHEET.second)
        val drawing = Drawing(library(files, textures = mapOf("hero.png" to { registered })))

        assertEquals(2, drawing.frame())

        assertEquals(mapOf("tiles.png" to 1), files.asks, "the resolver is not asked for the registered texture")
        assertSame(registered.data, renderer.textures.first().data, "the sprite draws the registered pixels")
        assertEquals(emptyList(), log(LogLevel.Info) + log(LogLevel.Error))
        drawing.destroy()
    }

    private fun library(vararg resolvers: SceneAssetResolver, textures: Map<String, SceneTextureFactory> = emptyMap()) =
        SceneAssetLibrary(emptyMap(), emptyMap(), emptyMap(), dynamicResolvers = resolvers.toList(), textureFactories = textures)

    private fun log(level: LogLevel): List<String> = records.filter { it.tag == TAG && it.level == level }.map { it.message }

    /** The runtime's two 2D draws, as `SceneAppLifecycleRuntime` builds them, over [library]. */
    private inner class Drawing(library: SceneAssetLibrary) {
        private val sprites = SpriteRenderBatch(renderer, { null }, library::textureImage)
        private val tilemaps = SceneTilemapRenderFeature(renderer, library::textureImage)
        private val context = RenderFeatureContext3D(
            Camera(Lens(eye = Vec3f(1f, 0f, 5f), center = Vec3f(1f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 20f)),
            0f,
            1f,
        )

        /** How many draws the frame has. */
        fun frame(): Int = sprites.collect(world.collectSpriteDraws()).size + tilemaps.collect(world, context).draws.size

        fun destroy() {
            sprites.destroy()
            tilemaps.destroy()
        }
    }

    /** Supplies both textures once [loaded], and counts how often each is asked for. */
    private class SwitchableTextures : SceneAssetResolver {
        var loaded = false
        val asks = mutableMapOf<String, Int>()

        override fun texture(name: String): SceneTextureLoad {
            asks[name] = (asks[name] ?: 0) + 1
            if (!loaded) return SceneTextureLoad.Loading
            val (width, height) = if (name == "hero.png") SHEET else ATLAS
            return SceneTextureLoad.Loaded(TextureAsset(ByteArray(width * height * RGBA), width, height))
        }
    }

    /** Records the texture of every material made with one. */
    private class TexturedMaterials : NoopRenderer() {
        val textures = mutableListOf<TextureAsset>()

        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material {
            texture?.let { textures += it }
            return super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)
        }
    }

    private companion object {
        const val TAG = "scene-assets"
        const val RGBA = 4

        /** A two-cell sprite sheet and a two-tile atlas, in pixels. */
        val SHEET = 4 to 2
        val ATLAS = 6 to 2
    }
}
