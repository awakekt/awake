/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.pipeline.GpuCapability
import com.awakekt.awake.render.pipeline.GpuCapabilityKind
import com.awakekt.awake.render.pipeline.GpuCompressedTextures
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.scene.authoring.scene
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A played project's sprite whose image was cooked to KTX2 beside it loads from the cooked file (#1211):
 * in the block format the device samples, and as RGBA8 on a device that takes none. Its image isn't read.
 */
class ProjectCookedTexturesTest {

    @Test
    fun aCookedSpriteUploadsInTheDevicesBlockFormat() = runTest {
        val (renderer, reads) = play(CookedDraws(setOf(CompressedTextureFormat.Bc7Rgba)))

        val drawn = renderer.lastFrame.map { renderer.uploads[it.material] }
        assertEquals(listOf<Any?>(CompressedTextureFormat.Bc7Rgba to SIZE), drawn.map { (it as? CompressedTextureImage)?.let { i -> i.format to i.width } })
        assertEquals(listOf("art/hero.ktx2"), reads, "the cooked file, and not the image")
    }

    @Test
    fun aDeviceWithNoBlockFormatGetsTheCookedFileAsRgba8() = runTest {
        val (renderer, reads) = play(CookedDraws(emptySet()))

        val drawn = renderer.lastFrame.map { renderer.uploads[it.material] }
        assertEquals(listOf<Any?>(SIZE), drawn.map { (it as? TextureAsset)?.width })
        assertEquals(listOf("art/hero.ktx2"), reads)
    }

    /** Plays the project on [renderer] until its sprite has loaded and drawn; the art it read, in order. */
    private suspend fun TestScope.play(renderer: CookedDraws): Pair<CookedDraws, List<String>> {
        val reads = mutableListOf<String>()
        val files = AssetSource { path ->
            if (path.value.startsWith("art/")) reads += path.value
            PROJECT.read(path)
        }
        val project = loadProject(files)
        project.sceneLoads = StandardTestDispatcher(testScheduler)
        val game = app { scene("play") { runProject(project) } }
        game.ready(renderer)
        game.update(FRAME, WIDTH, HEIGHT)
        advanceUntilIdle()
        game.update(FRAME, WIDTH, HEIGHT)
        game.update(FRAME, WIDTH, HEIGHT)
        game.dispose()
        project.close()
        return renderer to reads
    }

    /** A device taking [formats] in blocks, which keeps what each material was made from and the last frame's draws. */
    private class CookedDraws(formats: Set<CompressedTextureFormat>) :
        NoopRenderer(),
        GpuDrawPreparationSource {
        val uploads = HashMap<Material, Any>()
        private var pending = ArrayList<GpuDrawRequest>()
        var lastFrame: List<GpuDrawRequest> = emptyList()

        private val compressed = object : GpuCompressedTextures {
            override val formats = formats

            override fun createMaterial(image: CompressedTextureImage, filtering: TextureFiltering, uniformFloatCount: Int): Material? =
                if (image.format in formats) super@CookedDraws.createMaterial(null, null, uniformFloatCount, null).also { uploads[it] = image } else null
        }

        override val gpuDrawPreparer = GpuDrawPreparer { request, _, _ ->
            pending += request
            null
        }

        override fun draw(input: GpuPassInput) {
            lastFrame = pending
            pending = ArrayList()
        }

        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material =
            super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures).also { if (texture != null) uploads[it] = texture }

        @Suppress("UNCHECKED_CAST")
        override fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? =
            if (kind == GpuCompressedTextures) compressed as T else null
    }

    private companion object {
        /** The cooked file's size: a 64 x 64 ETC1S texture the cook wrote, as `awake:asset:texture-ktx-loader`'s tests use. */
        const val SIZE = 64

        val COOKED: ByteArray = requireNotNull(ProjectCookedTexturesTest::class.java.getResourceAsStream("/cooked/rock.ktx2")).use { it.readBytes() }

        val PROJECT = AssetSource { path ->
            runCatching {
                when (path.value) {
                    "awake.project.json" ->
                        """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
                            .encodeToByteArray()
                    "scenes/main.scene.json" -> """
                        { "version": 1, "name": "harbor", "nodes": [
                          { "name": "Hero", "components": [ { "component": "sprite", "texture": "art/hero.png" } ] }
                        ] }
                    """.trimIndent().encodeToByteArray()
                    "art/hero.ktx2" -> COOKED
                    else -> error("no ${path.value}")
                }
            }
        }
    }
}
