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
import com.awakekt.awake.scene.scene2d.SceneTilemap
import com.awakekt.awake.scene.scene2d.Sprite
import com.awakekt.awake.scene.scene2d.Tilemap
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A texture the scene's asset library forgets is asked for again, and what the frame uploaded from it
 * is destroyed, so an edited file redraws; a sheet that doesn't divide into its cells is logged once and
 * not drawn, without throwing, while everything else draws.
 */
class SceneTextureForgetTest {
    private val records = mutableListOf<LogRecord>()
    private val renderer = TrackedMaterials()
    private val world = World()
    private val files = Images(mapOf(HERO to SHEET, TILES to ATLAS))
    private val library = SceneAssetLibrary(emptyMap(), emptyMap(), emptyMap(), dynamicResolvers = listOf(files))
    private val drawing = Drawing()

    @BeforeTest
    fun listen() {
        Log.reset()
        Log.install(LogSink { records += it })
    }

    @AfterTest
    fun stopListening() {
        drawing.destroy()
        Log.reset()
    }

    @Test
    fun aForgottenTextureIsAskedForAgainAndDrawsItsNewImage() {
        sprite(HERO, columns = 2)
        tilemap(TILES)
        assertEquals(listOf(SHEET, ATLAS), drawing.frame())

        files.sizes = mapOf(HERO to EDITED_SHEET, TILES to EDITED_ATLAS)
        assertEquals(listOf(SHEET, ATLAS), drawing.frame(), "an edited file is not read until it is forgotten")
        library.forgetTexture(HERO)
        val afterForgetting = drawing.frame()
        drawing.frame()

        assertEquals(listOf(EDITED_SHEET, ATLAS), afterForgetting, "the sprite draws the new image; the tilemap keeps its own")
        assertEquals(mapOf(HERO to 2, TILES to 1), files.asks, "asked again once, for the forgotten texture only")
        assertEquals(listOf(SHEET), renderer.destroyed.map { it.size }, "the old sheet's material is destroyed, and nothing else")
        assertTrue(renderer.waitedIdle > 0, "the GPU is done with the old material before it is destroyed")
    }

    @Test
    fun forgettingEveryTextureRedrawsEachFromItsNewFile() {
        sprite(HERO, columns = 2)
        tilemap(TILES)
        drawing.frame()

        files.sizes = mapOf(HERO to EDITED_SHEET, TILES to EDITED_ATLAS)
        library.forgetTextures()
        val afterForgetting = drawing.frame()

        assertEquals(listOf(EDITED_SHEET, EDITED_ATLAS), afterForgetting)
        assertEquals(setOf(SHEET, ATLAS), renderer.destroyed.map { it.size }.toSet(), "both old materials are destroyed")
        assertEquals(2, renderer.destroyed.size)
        assertEquals(mapOf(HERO to 2, TILES to 2), files.asks)
    }

    @Test
    fun aTextureStillMissingAfterItIsForgottenIsLoggedAgainOnce() {
        sprite(GONE)
        repeat(2) { drawing.frame() }
        library.forgetTexture(GONE)
        repeat(2) { drawing.frame() }

        assertEquals(2, log(LIBRARY, LogLevel.Error).size, "once before it was forgotten, once after: ${log(LIBRARY, LogLevel.Error)}")
    }

    @Test
    fun aSheetThatDoesNotDivideIsLoggedOnceAndNotDrawnWhileOtherSpritesDraw() {
        files.sizes = mapOf(HERO to SHEET, ODD to ODD_SHEET, TILES to ODD_SHEET)
        sprite(ODD, columns = 2)
        sprite(ODD, columns = 2)
        sprite(HERO, columns = 2)
        tilemap(TILES)

        val frames = List(3) { drawing.frame() }

        assertEquals(List(3) { listOf(SHEET) }, frames, "only the sprite whose sheet divides draws, on every frame")
        val errors = log(SPRITES, LogLevel.Error)
        assertEquals(2, errors.size, "once for the sprite sheet and once for the tile atlas, however many frames: $errors")
        assertTrue("Sprite sheet 'odd.png' is 5x2 pixels, which cannot be divided into 2x1 equal cells." in errors[0], errors[0])
        assertTrue("Tile atlas 'tiles.png' is 5x2 pixels, which cannot be divided into 2x1 equal cells." in errors[1], errors[1])
        assertEquals(1, files.asks[TILES], "an atlas that doesn't divide is not asked for again")
    }

    @Test
    fun aSheetThatDidNotDivideDrawsOnceItsFixedFileIsForgotten() {
        files.sizes = mapOf(ODD to ODD_SHEET, TILES to ODD_SHEET)
        sprite(ODD, columns = 2)
        tilemap(TILES)
        drawing.frame()

        files.sizes = mapOf(ODD to SHEET, TILES to ATLAS)
        library.forgetTextures()

        assertEquals(listOf(SHEET, ATLAS), drawing.frame())
    }

    private fun sprite(texture: String, columns: Int = 1) {
        world.create().also {
            world.add(it, Transform())
            world.add(it, Sprite(SceneSprite(texture, columns = columns)))
        }
    }

    private fun tilemap(texture: String) {
        world.create().also {
            world.add(it, Transform())
            world.add(it, Tilemap(SceneTilemap(texture, width = 2, height = 1, tiles = listOf(0, 1), columns = 2)))
        }
    }

    private fun log(tag: String, level: LogLevel): List<String> = records.filter { it.tag == tag && it.level == level }.map { it.message }

    /** The runtime's two 2D draws over [library], each told when it forgets a texture, as `SceneAppLifecycleRuntime` wires them. */
    private inner class Drawing {
        private val sprites = SceneSpriteRenderFeature(SpriteRenderBatch(renderer, library::texture))
        private val tilemaps = SceneTilemapRenderFeature(renderer, library::texture)
        private val context = RenderFeatureContext3D(
            Camera(Lens(eye = Vec3f(1f, 0f, 5f), center = Vec3f(1f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 20f)),
            0f,
            1f,
        )

        init {
            library.addTextureUploads(sprites)
            library.addTextureUploads(tilemaps)
        }

        /** The image size each of the frame's draws shows, sprites first. */
        fun frame(): List<Pair<Int, Int>> =
            (sprites.collect(world, context).draws + tilemaps.collect(world, context).draws).map { renderer.sizes.getValue(it.material) }

        fun destroy() {
            sprites.forgetTextures()
            tilemaps.destroy()
        }
    }

    /** Supplies each texture at the size [sizes] gives it now, and counts how often each is asked for. */
    private class Images(var sizes: Map<String, Pair<Int, Int>>) : SceneAssetResolver {
        val asks = mutableMapOf<String, Int>()

        override fun texture(name: String): SceneTextureLoad? {
            asks[name] = (asks[name] ?: 0) + 1
            val (width, height) = sizes[name] ?: return SceneTextureLoad.Failed(IllegalStateException("no $name"))
            return SceneTextureLoad.Loaded(TextureAsset(ByteArray(width * height * RGBA), width, height))
        }
    }

    /** Records the image size of every textured material, the ones destroyed, and each wait for the GPU. */
    private class TrackedMaterials : NoopRenderer() {
        val sizes = HashMap<GpuMaterial, Pair<Int, Int>>()
        val destroyed = mutableListOf<Material>()
        var waitedIdle = 0

        override fun waitIdle() {
            waitedIdle++
        }

        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material {
            val made = super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)
            val tracked = object : Material by made {
                override fun destroy() {
                    destroyed += this
                    made.destroy()
                }
            }
            if (texture != null) sizes[tracked] = texture.width to texture.height
            return tracked
        }
    }

    private val Material.size: Pair<Int, Int> get() = renderer.sizes.getValue(this)

    private companion object {
        const val LIBRARY = "scene-assets"
        const val SPRITES = "sprites"
        const val RGBA = 4
        const val HERO = "hero.png"
        const val TILES = "tiles.png"
        const val ODD = "odd.png"
        const val GONE = "gone.png"

        /** Two-cell images, in pixels, before and after an edit, and one that two cells don't divide. */
        val SHEET = 4 to 2
        val ATLAS = 6 to 2
        val EDITED_SHEET = 8 to 2
        val EDITED_ATLAS = 10 to 2
        val ODD_SHEET = 5 to 2
    }
}
