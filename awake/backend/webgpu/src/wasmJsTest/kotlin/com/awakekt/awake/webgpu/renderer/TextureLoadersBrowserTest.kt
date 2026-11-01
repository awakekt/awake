/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.passes.sprites.SpriteDrawInput
import com.awakekt.awake.render.passes.sprites.SpriteRenderBatch
import com.awakekt.awake.render.pipeline.GpuImageUpload
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.runtime.AssetSourceTextureResolver
import com.awakekt.awake.scene.runtime.SceneTextureLoad
import com.awakekt.awake.webgpu.material.Material
import com.awakekt.awake.webgpu.texture.mipLevelCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

/**
 * The engine's own texture loaders take WebGPU's GPU-direct upload in a browser (awakekt/awake#1184):
 * a glTF model's base colour and normal maps, and a scene sprite sheet a file resolver loads. Each is
 * compared, every mip level, with the same material made through the CPU fallback ([cpuOnly], the
 * renderer without its [GpuImageUpload]), which is what every other renderer still does.
 *
 * Negative control, measured: decoding the glTF's images with `createBitmap` again, as the loader did
 * before this change, fails the first test with 0 copies where 2 are expected.
 */
class TextureLoadersBrowserTest {
    @Test
    fun aGltfModelsMapsAreCopiedOnTheGpu() = runTest(timeout = 2.minutes) {
        withRenderer { renderer, upload ->
            val path = "models/painted.gltf"
            val resolver = GltfAssetResolver()
            val base = encodedImage("image/png", 256, 256, translucent = false)
            val normal = encodedImage("image/png", 128, 128, translucent = false)
            resolver.preload(path, texturedGltf(base, normal))
            val name = resolver.materialName(path)

            val copiesBefore = upload.copies
            val gpu = assertNotNull(resolver.createMaterial(renderer, name)) as Material
            assertEquals(2, upload.copies - copiesBefore, "the base colour and the normal map are copied on the GPU")
            val cpu = assertNotNull(resolver.createMaterial(cpuOnly(renderer), name)) as Material
            assertEquals(2, upload.copies - copiesBefore, "the CPU fallback copies nothing")

            val (cpuBase, cpuMaps) = cpu.sampledTextures.let { it.first() to it.drop(1) }
            val (gpuBase, gpuMaps) = gpu.sampledTextures.let { it.first() to it.drop(1) }
            renderer.assertSameTexture("glTF base colour", cpuBase, gpuBase, mipLevelCount(256, 256))
            renderer.assertSameTexture("glTF normal map", cpuMaps[NORMAL], gpuMaps[NORMAL], mipLevelCount(128, 128))
            gpu.destroy()
            cpu.destroy()
        }
    }

    @Test
    fun aSceneSpriteSheetIsCopiedOnTheGpu() = runTest(timeout = 2.minutes) {
        withRenderer { renderer, upload ->
            val sheet = encodedImage("image/png", 64, 32, translucent = true)
            val resolver = AssetSourceTextureResolver(AssetSource { Result.success(sheet) })
            val source = loaded(resolver, "sprites/sheet.png")
            val draw = listOf(SpriteDrawInput(texture = "sprites/sheet.png", model = Mat4()))

            val copiesBefore = upload.copies
            val gpuBatch = SpriteRenderBatch(renderer, { null }) { source }
            assertEquals(1, gpuBatch.collect(draw).size)
            assertEquals(1, upload.copies - copiesBefore, "the sheet is copied on the GPU")
            val cpuBatch = SpriteRenderBatch(cpuOnly(renderer), { null }) { source }
            val cpu = cpuBatch.collect(draw).single().material as Material
            val gpu = gpuBatch.collect(draw).single().material as Material
            renderer.assertSameTexture("sprite sheet", cpu.sampledTextures.first(), gpu.sampledTextures.first(), levels = 1)
            gpuBatch.forgetTextures()
            cpuBatch.forgetTextures()
            resolver.close()
        }
    }

    // Real time, not the test's virtual clock: the browser decodes between polls.
    private suspend fun loaded(resolver: AssetSourceTextureResolver, name: String): GpuImageSource = withContext(Dispatchers.Default) {
        var load = resolver.texture(name)
        while (load == SceneTextureLoad.Loading) {
            delay(POLL_MS)
            load = resolver.texture(name)
        }
        when (load) {
            is SceneTextureLoad.Loaded -> load.source.also { assertNotNull(it.externalImage, "loaded as a browser image") }
            is SceneTextureLoad.Failed -> throw load.cause
            else -> error("unexpected $load")
        }
    }

    private suspend fun withRenderer(block: suspend (Renderer, ExternalImageUpload) -> Unit) {
        val engine = ImageUploadEngine()
        try {
            val renderer = engine.boot()
            block(renderer, renderer.capability(GpuImageUpload) as ExternalImageUpload)
            assertEquals(emptyList(), engine.errors, "WebGPU validation errors")
        } finally {
            engine.destroy()
        }
    }

    private companion object {
        // In the PBR maps' order: metallic-roughness, normal, occlusion, emissive.
        const val NORMAL = 1
        const val POLL_MS = 5L
    }
}
