/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRecord
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A played project's sprites and tilemaps draw their textures from the project's files, which nothing
 * registered when the scene was built: nothing while the files load, then from the frame they arrive.
 * One whose file is missing draws nothing, and the frame goes on.
 */
class ProjectSpriteTexturesTest {
    private val records = mutableListOf<LogRecord>()

    @BeforeTest
    fun listen() {
        Log.reset()
        Log.install(LogSink { records += it })
    }

    @AfterTest
    fun stopListening() {
        Log.reset()
    }

    @Test
    fun aSceneOfSpritesAndATilemapDrawsThemOnceTheirFilesLoad() = runTest {
        val arrived = CompletableDeferred<Unit>()
        val reads = mutableListOf<String>()
        val files = AssetSource { path ->
            if (path.value.endsWith(".png")) {
                reads += path.value
                arrived.await()
            }
            PROJECT.read(path)
        }
        val project = loadProject(files)
        project.sceneLoads = StandardTestDispatcher(testScheduler)
        val renderer = TexturedDraws()
        val game = app { scene("play") { runProject(project) } }

        game.ready(renderer)
        repeat(2) { game.update(FRAME, WIDTH, HEIGHT) }
        advanceUntilIdle()
        val beforeTheFilesArrive = renderer.lastFrameTextures()
        arrived.complete(Unit)
        advanceUntilIdle()
        game.update(FRAME, WIDTH, HEIGHT)
        val afterwards = renderer.lastFrameTextures()
        game.update(FRAME, WIDTH, HEIGHT)

        assertEquals(emptyList(), beforeTheFilesArrive, "nothing textured is drawn while the files load")
        assertEquals(setOf(SHEET, ATLAS), afterwards.map { it.width to it.height }.toSet(), "the sprite and the tilemap draw from their files")
        assertEquals(2, afterwards.size, "the sprite, and the tilemap's one chunk")
        assertEquals(listOf("art/hero.png", "art/tiles.png", "art/gone.png").sorted(), reads.sorted(), "each file read once")

        val waiting = log(LogLevel.Info).filter { "has not loaded yet" in it }
        assertEquals(3, waiting.size, "each texture is reported not drawn yet once: $waiting")
        val failed = log(LogLevel.Error)
        assertEquals(1, failed.size, "the missing file is reported once: $failed")
        assertTrue("Texture 'art/gone.png' could not load" in failed.single(), failed.single())
        game.dispose()
        project.close()
    }

    @Test
    fun anImageEditedWhileTheProjectPlaysRedrawsOnceTheSceneForgetsIt() = runTest {
        var hero = SHEET
        val reads = mutableListOf<String>()
        val files = AssetSource { path ->
            if (path.value.endsWith(".png")) reads += path.value
            if (path.value == "art/hero.png") Result.success(png(hero)) else PROJECT.read(path)
        }
        val project = loadProject(files)
        project.sceneLoads = StandardTestDispatcher(testScheduler)
        val renderer = TexturedDraws()
        val game = app { scene("play") { runProject(project) } }
        game.ready(renderer)
        game.update(FRAME, WIDTH, HEIGHT)
        advanceUntilIdle()
        game.update(FRAME, WIDTH, HEIGHT)
        val before = renderer.lastFrameTextures().map { it.width to it.height }.toSet()

        hero = EDITED_SHEET
        game.update(FRAME, WIDTH, HEIGHT)
        val editedButNotForgotten = renderer.lastFrameTextures().map { it.width to it.height }.toSet()
        game.requireService<SceneAppLifecycleRuntime>().requireAssetLibrary().forgetTexture("art/hero.png")
        game.update(FRAME, WIDTH, HEIGHT)
        advanceUntilIdle()
        game.update(FRAME, WIDTH, HEIGHT)
        val after = renderer.lastFrameTextures().map { it.width to it.height }.toSet()

        assertEquals(setOf(SHEET, ATLAS), before)
        assertEquals(setOf(SHEET, ATLAS), editedButNotForgotten, "an edited file is not read until it is forgotten")
        assertEquals(setOf(EDITED_SHEET, ATLAS), after, "the sprite draws the edited image, the tilemap its own")
        assertEquals(listOf("art/hero.png", "art/hero.png"), reads.filter { it == "art/hero.png" }, "read again once, after it was forgotten")
        assertEquals(1, renderer.destroyedTextures.count { it.width to it.height == SHEET }, "the old sheet's material is destroyed")
        game.dispose()
        project.close()
    }

    private fun log(level: LogLevel): List<String> = records.filter { it.tag == "scene-assets" && it.level == level }.map { it.message }

    /** Keeps the last frame's draws, and the texture of every material made with one. */
    private class TexturedDraws :
        NoopRenderer(),
        GpuDrawPreparationSource {
        private val textures = HashMap<Material, TextureAsset>()
        private var pending = ArrayList<GpuDrawRequest>()
        private var lastFrame: List<GpuDrawRequest> = emptyList()

        override val gpuDrawPreparer = GpuDrawPreparer { request, _, _ ->
            pending += request
            null
        }

        override fun draw(input: GpuPassInput) {
            lastFrame = pending
            pending = ArrayList()
        }

        /** The textures of the materials destroyed so far. */
        val destroyedTextures = mutableListOf<TextureAsset>()

        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material {
            val material = super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)
            val made = object : Material by material {
                override fun destroy() {
                    if (texture != null) destroyedTextures += texture
                    material.destroy()
                }
            }
            if (texture != null) textures[made] = texture
            return made
        }

        /** The textures the last frame drew with, one for each textured draw. */
        fun lastFrameTextures(): List<TextureAsset> = lastFrame.mapNotNull { textures[it.material] }
    }

    private companion object {
        /** A two-cell sprite sheet and a two-tile atlas, in pixels. */
        val SHEET = 4 to 2
        val ATLAS = 6 to 2
        val EDITED_SHEET = 8 to 2

        fun png(size: Pair<Int, Int>): ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(size.first, size.second, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()

        val PROJECT = AssetSource { path ->
            runCatching {
                when (path.value) {
                    "awake.project.json" ->
                        """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
                            .encodeToByteArray()
                    "scenes/main.scene.json" -> """
                        { "version": 1, "name": "harbor", "nodes": [
                          { "name": "Hero", "components": [ { "component": "sprite", "texture": "art/hero.png", "columns": 2, "pixelsPerUnit": 2 } ] },
                          { "name": "Ground", "components": [ { "component": "tilemap", "texture": "art/tiles.png", "width": 2, "height": 1,
                            "tiles": [0, 1], "columns": 2, "pixelsPerUnit": 2 } ] },
                          { "name": "Ghost", "components": [ { "component": "sprite", "texture": "art/gone.png" } ] }
                        ] }
                    """.trimIndent().encodeToByteArray()
                    "art/hero.png" -> png(SHEET)
                    "art/tiles.png" -> png(ATLAS)
                    else -> error("no ${path.value}")
                }
            }
        }
    }
}
