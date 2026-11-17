/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneMeshLoad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A model marked [GltfAssetResolver.releaseAfterUpload] lets go of its CPU geometry once its mesh is
 * made, and of its images once its material is made: a populated scene a consumer measured held 27 MiB
 * of them for the resolver's life (#1753). A mesh or material made again reads the model again first,
 * through [GltfAssetResolver.loadMesh] and [GltfAssetResolver.loadMaterial], and comes out the same. A
 * model not marked, or marked [GltfAssetResolver.keepAfterUpload], keeps both, as before.
 */
@OptIn(ExperimentalEncodingApi::class)
class GltfReleaseAfterUploadTest {
    private val uploaded = mutableListOf<TextureAsset?>()
    private val renderer = object : NoopRenderer(), GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }

        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material {
            uploaded += texture
            return super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)
        }
    }

    @Test
    fun aReleasedModelLetsGoOnceUploadedAndReadsItselfAgainToBeMadeAgain() = runTest {
        val resolver = loadedResolver(TRIANGLE.encodeToByteArray())
        resolver.releaseAfterUpload(PATH)
        val runtime = runtime(resolver)
        val material = resolver.materialName(PATH)
        val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        assertNull(resolver.loadMesh(PATH, background), "a preloaded mesh needs no load")
        assertNotNull(resolver.createMesh(runtime, PATH))
        assertNotNull(resolver.createMaterial(renderer, material))

        // Both let go of their CPU copies, so making either again reads the model first: one read for both.
        val materialLoad = assertNotNull(resolver.loadMaterial(material, background), "its images were let go of")
        awaitLoad(assertNotNull(resolver.loadMesh(PATH, background), "its geometry was let go of"))
        awaitLoad(materialLoad)
        assertNull(resolver.loadMesh(PATH, background), "the model read again put its geometry back")
        assertNull(resolver.loadMaterial(material, background), "and its images")
        assertNotNull(resolver.createMesh(runtime, PATH))
        assertNotNull(resolver.createMaterial(renderer, material))

        assertEquals(2, uploaded.size)
        assertEquals(uploaded[0], uploaded[1], "the material made again uploads the same pixels")
        background.cancel()
    }

    @Test
    fun aModelNotReleasedKeepsItsCopies() = runTest {
        val resolver = loadedResolver(TRIANGLE.encodeToByteArray())
        resolver.releaseAfterUpload(PATH)
        resolver.keepAfterUpload(PATH)
        val runtime = runtime(resolver)
        val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        assertNotNull(resolver.createMesh(runtime, PATH))
        assertNotNull(resolver.createMaterial(renderer, resolver.materialName(PATH)))

        assertNull(resolver.loadMesh(PATH, background), "a kept model keeps its geometry")
        assertNotNull(resolver.createMesh(runtime, PATH), "and makes its mesh again with no load")
        background.cancel()
    }

    @Test
    fun aReleasedModelsGeometryIsFreedOnceUploaded() = runTest {
        val models = List(MODELS) { "assets/props/prop-$it.gltf" }
        val resolver = GltfAssetResolver()
        val bytes = gridModel(VERTICES).encodeToByteArray()
        for (path in models) {
            resolver.preload(path, bytes)
            resolver.releaseAfterUpload(path)
        }
        val runtime = runtime(resolver)

        val before = liveHeap()
        for (path in models) assertNotNull(resolver.createMesh(runtime, path))
        val freed = before - liveHeap()

        val geometryBytes = MODELS.toLong() * VERTICES * VertexFloats * Float.SIZE_BYTES
        println("$MODELS released models of $VERTICES vertices: ${freed / KIB} KiB freed once uploaded, of ${geometryBytes / KIB} KiB of vertices")
        assertTrue(freed >= geometryBytes / 2, "uploading released models freed $freed B of $geometryBytes B of vertices")
    }

    private suspend fun loadedResolver(bytes: ByteArray) = GltfAssetResolver().apply {
        setAssetSource(AssetSource { Result.success(bytes) })
        preload(PATH, bytes)
    }

    private suspend fun runtime(models: GltfAssetResolver): SceneAppLifecycleRuntime {
        val game = app { scene("props") { assets { resolver(models) } } }
        game.ready(renderer)
        return game.requireService()
    }

    private suspend fun awaitLoad(load: SceneMeshLoad) {
        while (!load.poll()) yield()
        load.close()
        assertNull(load.failure)
    }

    private fun liveHeap(): Long {
        repeat(GC_ROUNDS) {
            System.gc()
            Thread.sleep(GC_PAUSE_MS)
        }
        return ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
    }

    /** A strip of [vertices] positions and its triangles, untextured. */
    private fun gridModel(vertices: Int): String {
        val positions = ByteBuffer.allocate(vertices * POSITION_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until vertices) positions.putFloat((i / 2).toFloat()).putFloat((i % 2).toFloat()).putFloat(0f)
        val triangles = (vertices - 2) * 3
        val indices = ByteBuffer.allocate(triangles * Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        for (t in 0 until vertices - 2) indices.putShort(t.toShort()).putShort((t + 1).toShort()).putShort((t + 2).toShort())
        val buffer = Base64.encode(positions.array() + indices.array())
        val positionBytes = vertices * POSITION_BYTES
        return """
            {
              "buffers": [{"uri":"data:application/octet-stream;base64,$buffer","byteLength":${positionBytes + triangles * 2}}],
              "bufferViews": [{"buffer":0,"byteOffset":0,"byteLength":$positionBytes},{"buffer":0,"byteOffset":$positionBytes,"byteLength":${triangles * 2}}],
              "accessors": [
                {"bufferView":0,"componentType":5126,"count":$vertices,"type":"VEC3","min":[0,0,0],"max":[${vertices / 2},1,0]},
                {"bufferView":1,"componentType":5123,"count":$triangles,"type":"SCALAR"}
              ],
              "meshes": [{"primitives":[{"attributes":{"POSITION":0},"indices":1}]}],
              "nodes": [{"mesh":0}], "scenes": [{"nodes":[0]}], "scene": 0
            }
        """.trimIndent()
    }

    private companion object {
        const val PATH = "assets/painted-triangle.gltf"
        const val MODELS = 24
        const val VERTICES = 4000
        const val POSITION_BYTES = 12

        /** PositionNormalColor: what an untextured static model's geometry is interleaved as. */
        const val VertexFloats = 9
        const val KIB = 1024
        const val GC_ROUNDS = 3
        const val GC_PAUSE_MS = 50L

        const val PIXEL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="

        // One triangle with a 1x1 base colour image: positions (36 bytes) then indices (6 bytes).
        const val BUFFER = "AAAAAAAAAAAAAAAAAACAPwAAAAAAAAAAAAAAAAAAgD8AAAAAAAABAAIA"
        val TRIANGLE = """
            {
              "buffers": [{"uri":"data:application/octet-stream;base64,$BUFFER","byteLength":42}],
              "bufferViews": [{"buffer":0,"byteOffset":0,"byteLength":36},{"buffer":0,"byteOffset":36,"byteLength":6}],
              "accessors": [
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"}
              ],
              "materials": [{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}],
              "textures": [{"source":0}],
              "images": [{"uri":"data:image/png;base64,$PIXEL_PNG"}],
              "meshes": [{"primitives":[{"attributes":{"POSITION":0},"indices":1,"material":0}]}],
              "nodes": [{"mesh":0}], "scenes": [{"nodes":[0]}], "scene": 0
            }
        """.trimIndent()
    }
}
