/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.asset.shaderpack.TexturedUniformLayout
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneRenderableRequest
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Verifies content-addressed deduplication and reference-counted disposal of glTF/GLB GPU materials
 * across separate models, multi-primitive static meshes, and multi-part skinned models.
 */
@OptIn(ExperimentalEncodingApi::class)
class GltfTextureDeduplicationTest {

    @Test
    fun separateGlbModelsWithIdenticalEmbeddedImageUploadOnceAndRefCountDisposal() = runTest {
        val redPng = solidPng(0xFFFF2020.toInt())
        val bluePng = solidPng(0xFF2020FF.toInt())
        val modelA = "assets/props/crate-a.glb"
        val modelB = "assets/props/crate-b.glb"
        val modelC = "assets/props/pillar.glb"
        val resolver = preloadedReleasableResolver(
            mapOf(
                modelA to texturedGlb(redPng, roughness = 0.25f, doubleSided = false),
                modelB to texturedGlb(redPng, roughness = 0.85f, doubleSided = true),
                modelC to texturedGlb(bluePng, roughness = 0.50f, doubleSided = false),
            ),
        )
        val renderer = TrackingRenderer()
        val game = app { scene("dedup") { assets { resolver(resolver) } } }
        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val library = runtime.requireAssetLibrary()
        val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val reqA = materialRequest(runtime, modelA, resolver.materialName(modelA))
            val reqB = materialRequest(runtime, modelB, resolver.materialName(modelB))
            val reqC = materialRequest(runtime, modelC, resolver.materialName(modelC))

            val renderA = library.resolve(runtime, reqA)
            val renderB = library.resolve(runtime, reqB)
            assertSame(renderA.material, renderB.material, "byte-identical GLB images must share one GPU material")
            assertEquals(1, renderer.createdMaterials.size, "two identical GLB models trigger only 1 texture upload")
            assertEquals(0.25f, renderA.defaultMaterial?.roughness)
            assertEquals(0.85f, renderB.defaultMaterial?.roughness, "each model keeps its own authored PBR factors")
            assertEquals(CullMode.Back, renderA.cullMode)
            assertEquals(CullMode.None, renderB.cullMode, "each model keeps its own cull mode")
            assertTrue(resolver.isMaterialLetGo(resolver.materialName(modelA)), "first model drops CPU image data after upload")
            assertTrue(resolver.isMaterialLetGo(resolver.materialName(modelB)), "reusing model also drops CPU image data")

            val renderC = library.resolve(runtime, reqC)
            assertNotSame(renderA.material, renderC.material, "distinct image bytes upload a separate GPU material")
            assertEquals(2, renderer.createdMaterials.size)

            library.releaseRenderable(reqA)
            assertEquals(0, renderer.createdMaterials[0].destroyCount, "shared GPU material stays alive while model B uses it")
            library.releaseRenderable(reqB)
            assertEquals(1, renderer.createdMaterials[0].destroyCount, "shared GPU material is destroyed once on last release")

            val reload = assertNotNull(resolver.loadMesh(modelA, background))
            while (!reload.poll()) yield()
            reload.close()
            assertNull(reload.failure)
            assertNotSame(renderA.material, library.resolve(runtime, reqA).material, "reloaded model receives a fresh GPU material")
            assertEquals(3, renderer.createdMaterials.size)

            library.releaseRenderable(reqA)
            library.releaseRenderable(reqC)
            assertEquals(0, renderer.liveMaterials)
            assertTrue(renderer.createdMaterials.all { it.destroyCount == 1 }, "every GPU material destroyed exactly once")
        } finally {
            background.cancel()
            game.dispose()
        }
    }

    private suspend fun preloadedReleasableResolver(files: Map<String, ByteArray>): GltfAssetResolver = GltfAssetResolver().apply {
        setAssetSource(AssetSource { path -> files[path.value]?.let(Result.Companion::success) ?: Result.failure(NoSuchElementException(path.value)) })
        files.forEach { (path, bytes) ->
            preload(path, bytes)
            releaseAfterUpload(path)
        }
    }

    private fun materialRequest(runtime: SceneAppLifecycleRuntime, mesh: String, material: String): SceneRenderableRequest =
        SceneRenderableRequest(runtime.world.create(), SceneMeshRenderer(mesh = mesh, material = material, cullMode = SceneMeshRenderer.CullMode.FromMaterial))

    @Test
    fun multiplePrimitivesInOneStaticGltfWithIdenticalImageUploadOnce() = runTest {
        val sharedPng = solidPng(0xFF33AA44.toInt())
        val uniquePng = solidPng(0xFFAA3388.toInt())
        val path = "assets/models/multi-primitive.gltf"
        val json = multiPrimitiveStaticGltf(listOf(sharedPng, sharedPng, uniquePng))

        val resolver = GltfAssetResolver().apply {
            preload(path, json.encodeToByteArray())
            releaseAfterUpload(path)
        }
        val renderer = TrackingRenderer()
        val game = app { scene("static-multi") { assets { resolver(resolver) } } }
        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val library = runtime.requireAssetLibrary()

        try {
            val slots = resolver.materialSlots(path)
            assertEquals(3, slots.size)
            val requests = slots.map { slot ->
                SceneRenderableRequest(runtime.world.create(), SceneMeshRenderer(mesh = slot.mesh, material = slot.material))
            }
            val resolved = requests.map { library.resolve(runtime, it) }

            assertSame(resolved[0].material, resolved[1].material, "primitives with identical image bytes share one GPU material")
            assertNotSame(resolved[0].material, resolved[2].material, "primitive with distinct image bytes gets its own material")
            assertEquals(2, renderer.createdMaterials.size, "3 primitives with 2 unique textures trigger only 2 uploads")

            library.releaseRenderable(requests[0])
            assertEquals(0, renderer.createdMaterials[0].destroyCount)
            library.releaseRenderable(requests[1])
            assertEquals(1, renderer.createdMaterials[0].destroyCount)
            library.releaseRenderable(requests[2])
            assertEquals(1, renderer.createdMaterials[1].destroyCount)
            assertEquals(0, renderer.liveMaterials)
        } finally {
            game.dispose()
        }
    }

    @Test
    fun skinnedPrimitivesSharingAtlasUploadOnceAndDoNotCollideWithStaticLayoutOrDifferentPbrMaps() = runTest {
        val atlasPng = solidPng(0xFF4488CC.toInt())
        val emissivePng = solidPng(0xFFFFEE22.toInt())
        val skinnedPath = "assets/characters/rigged-figure.gltf"
        val staticPath = "assets/props/static-crate.glb"

        val resolver = GltfAssetResolver().apply {
            preload(skinnedPath, multiPartSkinnedGltf(atlasPng, emissivePng).encodeToByteArray())
            preload(staticPath, texturedGlb(atlasPng, roughness = 0.5f, doubleSided = false))
            releaseAfterUpload(skinnedPath)
            releaseAfterUpload(staticPath)
        }
        val renderer = TrackingRenderer()
        val game = app { scene("skinned-dedup") { assets { resolver(resolver) } } }
        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val library = runtime.requireAssetLibrary()

        try {
            val slots = resolver.materialSlots(skinnedPath)
            assertEquals(3, slots.size)
            val part0 = library.resolve(runtime, SceneRenderableRequest(runtime.world.create(), SceneMeshRenderer(slots[0].mesh, slots[0].material)))
            val part1 = library.resolve(runtime, SceneRenderableRequest(runtime.world.create(), SceneMeshRenderer(slots[1].mesh, slots[1].material)))
            val part2 = library.resolve(runtime, SceneRenderableRequest(runtime.world.create(), SceneMeshRenderer(slots[2].mesh, slots[2].material)))
            val staticCrate = library.resolve(
                runtime,
                SceneRenderableRequest(runtime.world.create(), SceneMeshRenderer(staticPath, resolver.materialName(staticPath))),
            )

            assertSame(part0.material, part1.material, "skinned parts with identical baseColor and PBR maps share one GPU material")
            assertNotSame(part0.material, part2.material, "skinned part with an extra emissive PBR map gets its own GPU material")
            assertNotSame(part0.material, staticCrate.material, "static and skinned uniform layouts never share a GPU material")
            assertEquals(3, renderer.createdMaterials.size, "part0+part1 share 1, part2 (emissive) is 2nd, static layout is 3rd")
            assertEquals(SkinnedUniformLayout.total, renderer.createdMaterials[0].uniformFloatCount)
            assertEquals(SkinnedUniformLayout.total, renderer.createdMaterials[1].uniformFloatCount)
            assertEquals(TexturedUniformLayout.total, renderer.createdMaterials[2].uniformFloatCount)

            // SceneAssetLibrary.dispose() destroys each distinct GPU material exactly once, even with shared references.
            game.dispose()
            assertEquals(0, renderer.liveMaterials)
            assertTrue(renderer.createdMaterials.all { it.destroyCount == 1 })
        } finally {
            game.dispose()
        }
    }

    @Test
    fun forgettingAModelClearsItsSharedMaterialKey() = runTest {
        val png = solidPng(0xFF1177EE.toInt())
        val path = "assets/props/ephemeral.glb"
        val resolver = GltfAssetResolver().apply {
            preload(path, texturedGlb(png, roughness = 0.4f, doubleSided = false))
        }
        val materialName = resolver.materialName(path)
        assertNotNull(resolver.sharedMaterialKey(materialName))

        resolver.forget(path)
        assertNull(resolver.sharedMaterialKey(materialName), "forget() clears cached content keys for the model")
    }

    private class TrackedMaterial(val uniformFloatCount: Int, private val onDestroy: () -> Unit) : Material {
        var destroyCount = 0
            private set

        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit

        override fun destroy() {
            destroyCount++
            onDestroy()
        }
    }

    private class TrackingRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
        val createdMaterials = mutableListOf<TrackedMaterial>()
        var liveMaterials = 0
            private set

        override fun createMaterial(
            texture: TextureAsset?,
            renderTarget: RenderTarget?,
            uniformFloatCount: Int,
            pbrTextures: PbrTextureSet?,
        ): Material {
            liveMaterials++
            return TrackedMaterial(uniformFloatCount) { liveMaterials-- }.also { createdMaterials += it }
        }
    }

    private fun solidPng(argb: Int): ByteArray = ByteArrayOutputStream().also { out ->
        val img = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 2) {
            for (x in 0 until 2) img.setRGB(x, y, argb)
        }
        ImageIO.write(img, "png", out)
    }.toByteArray()

    private fun texturedGlb(pngBytes: ByteArray, roughness: Float, doubleSided: Boolean): ByteArray {
        val positions = floatBytes(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val uvs = floatBytes(0f, 0f, 1f, 0f, 0f, 1f)
        val indices = byteArrayOf(0, 0, 1, 0, 2, 0, 0, 0)
        val imgOffset = positions.size + uvs.size + indices.size
        val bin = positions + uvs + indices + pngBytes
        val json = """
            {
              "asset":{"version":"2.0"},
              "buffers":[{"byteLength":${bin.size}}],
              "bufferViews":[
                {"buffer":0,"byteOffset":0,"byteLength":${positions.size}},
                {"buffer":0,"byteOffset":${positions.size},"byteLength":${uvs.size}},
                {"buffer":0,"byteOffset":${positions.size + uvs.size},"byteLength":6},
                {"buffer":0,"byteOffset":$imgOffset,"byteLength":${pngBytes.size}}
              ],
              "accessors":[
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5126,"count":3,"type":"VEC2"},
                {"bufferView":2,"componentType":5123,"count":3,"type":"SCALAR"}
              ],
              "images":[{"bufferView":3,"mimeType":"image/png"}],
              "textures":[{"source":0}],
              "materials":[{"pbrMetallicRoughness":{"baseColorTexture":{"index":0},"roughnessFactor":$roughness},"doubleSided":$doubleSided}],
              "meshes":[{"primitives":[{"attributes":{"POSITION":0,"TEXCOORD_0":1},"indices":2,"material":0}]}],
              "nodes":[{"mesh":0}],
              "scenes":[{"nodes":[0]}],
              "scene":0
            }
        """.trimIndent()
        return packGlb(json, bin)
    }

    private fun multiPrimitiveStaticGltf(imagesBytes: List<ByteArray>): String {
        val positions = floatBytes(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val uvs = floatBytes(0f, 0f, 1f, 0f, 0f, 1f)
        val indices = byteArrayOf(0, 0, 1, 0, 2, 0)
        val buffer = positions + uvs + indices
        val images = imagesBytes.joinToString(",") { """{"uri":"data:image/png;base64,${Base64.encode(it)}"}""" }
        val textures = imagesBytes.indices.joinToString(",") { """{"source":$it}""" }
        val materials = imagesBytes.indices.joinToString(",") {
            """{"pbrMetallicRoughness":{"baseColorTexture":{"index":$it}}}"""
        }
        val primitives = imagesBytes.indices.joinToString(",") {
            """{"attributes":{"POSITION":0,"TEXCOORD_0":1},"indices":2,"material":$it}"""
        }
        return """
            {
              "asset":{"version":"2.0"},
              "buffers":[{"uri":"data:application/octet-stream;base64,${Base64.encode(buffer)}","byteLength":${buffer.size}}],
              "bufferViews":[
                {"buffer":0,"byteOffset":0,"byteLength":${positions.size}},
                {"buffer":0,"byteOffset":${positions.size},"byteLength":${uvs.size}},
                {"buffer":0,"byteOffset":${positions.size + uvs.size},"byteLength":6}
              ],
              "accessors":[
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5126,"count":3,"type":"VEC2"},
                {"bufferView":2,"componentType":5123,"count":3,"type":"SCALAR"}
              ],
              "images":[$images],
              "textures":[$textures],
              "materials":[$materials],
              "meshes":[{"primitives":[$primitives]}],
              "nodes":[{"mesh":0}],
              "scenes":[{"nodes":[0]}],
              "scene":0
            }
        """.trimIndent()
    }

    /** Three skinned parts: parts 0 and 1 use identical baseColor bytes; part 2 adds an emissive map. */
    private fun multiPartSkinnedGltf(basePng: ByteArray, emissivePng: ByteArray): String {
        val positions = floatBytes(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val indices = byteArrayOf(0, 0, 1, 0, 2, 0, 0, 0)
        val joints = ByteArray(12)
        val weights = floatBytes(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
        val uvs = floatBytes(0f, 0f, 1f, 0f, 0f, 1f)
        val inverseBind = floatBytes(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val buffer = positions + indices + joints + weights + uvs + inverseBind
        var offset = 0
        val views = listOf(positions, indices, joints, weights, uvs, inverseBind).joinToString(",") { part ->
            """{"buffer":0,"byteOffset":$offset,"byteLength":${if (part === indices) 6 else part.size}}""".also { offset += part.size }
        }
        val baseB64 = Base64.encode(basePng)
        val emissiveB64 = Base64.encode(emissivePng)
        return """
            {
              "asset":{"version":"2.0"},
              "buffers":[{"uri":"data:application/octet-stream;base64,${Base64.encode(buffer)}","byteLength":${buffer.size}}],
              "bufferViews":[$views],
              "accessors":[
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"},
                {"bufferView":2,"componentType":5121,"count":3,"type":"VEC4"},
                {"bufferView":3,"componentType":5126,"count":3,"type":"VEC4"},
                {"bufferView":4,"componentType":5126,"count":3,"type":"VEC2"},
                {"bufferView":5,"componentType":5126,"count":1,"type":"MAT4"}
              ],
              "images":[
                {"uri":"data:image/png;base64,$baseB64"},
                {"uri":"data:image/png;base64,$baseB64"},
                {"uri":"data:image/png;base64,$emissiveB64"}
              ],
              "textures":[{"source":0},{"source":1},{"source":2}],
              "materials":[
                {"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}},
                {"pbrMetallicRoughness":{"baseColorTexture":{"index":1}}},
                {"pbrMetallicRoughness":{"baseColorTexture":{"index":0}},"emissiveTexture":{"index":2},"emissiveFactor":[1,1,1]}
              ],
              "meshes":[
                {"primitives":[
                  {"attributes":{"POSITION":0,"JOINTS_0":2,"WEIGHTS_0":3,"TEXCOORD_0":4},"indices":1,"material":0},
                  {"attributes":{"POSITION":0,"JOINTS_0":2,"WEIGHTS_0":3,"TEXCOORD_0":4},"indices":1,"material":1},
                  {"attributes":{"POSITION":0,"JOINTS_0":2,"WEIGHTS_0":3,"TEXCOORD_0":4},"indices":1,"material":2}
                ]}
              ],
              "nodes":[{"name":"Root"},{"mesh":0,"skin":0}],
              "skins":[{"inverseBindMatrices":5,"joints":[0]}],
              "scenes":[{"nodes":[0,1]}],
              "scene":0
            }
        """.trimIndent()
    }

    private fun floatBytes(vararg values: Float): ByteArray = ByteBuffer.allocate(values.size * 4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply { values.forEach(::putFloat) }
        .array()

    private fun packGlb(json: String, bin: ByteArray): ByteArray {
        val jsonBytes = json.encodeToByteArray().let { it + ByteArray((4 - it.size % 4) % 4) { ' '.code.toByte() } }
        val binBytes = bin + ByteArray((4 - bin.size % 4) % 4)
        val out = ByteArray(12 + 8 + jsonBytes.size + 8 + binBytes.size)
        out.putInt(0, 0x46546C67)
        out.putInt(4, 2)
        out.putInt(8, out.size)
        out.putInt(12, jsonBytes.size)
        out.putInt(16, 0x4E4F534A)
        jsonBytes.copyInto(out, 20)
        val binOffset = 20 + jsonBytes.size
        out.putInt(binOffset, binBytes.size)
        out.putInt(binOffset + 4, 0x004E4942)
        binBytes.copyInto(out, binOffset + 8)
        return out
    }

    private fun ByteArray.putInt(offset: Int, value: Int) {
        for (byte in 0 until 4) this[offset + byte] = (value ushr (byte * 8)).toByte()
    }
}
