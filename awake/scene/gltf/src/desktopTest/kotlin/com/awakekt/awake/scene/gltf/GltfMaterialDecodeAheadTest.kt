/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A glTF material made again decodes its images off the frame thread first (#1619): once a material
 * made from a model's image has let its pixels go, [GltfAssetResolver.loadMaterial] decodes them in
 * the scene's scope, after which nothing is left to decode and the material made again uploads the
 * same pixels.
 */
@OptIn(ExperimentalEncodingApi::class)
class GltfMaterialDecodeAheadTest {
    @Test
    fun aMaterialMadeAgainIsDecodedOffTheFrameThreadFirst() = runTest {
        val path = "assets/painted-triangle.gltf"
        val resolver = GltfAssetResolver()
        resolver.preloadMaterials(path, TRIANGLE.encodeToByteArray())
        val name = resolver.materialName(path)
        val uploaded = mutableListOf<TextureAsset?>()
        val renderer = object : NoopRenderer() {
            override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material {
                uploaded += texture
                return super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)
            }
        }
        val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        assertNull(resolver.loadMaterial(name, background), "a preloaded image is still decoded")
        assertNotNull(resolver.createMaterial(renderer, name))

        // The upload let the pixels go: making the material again would decode them where it runs.
        val load = assertNotNull(resolver.loadMaterial(name, background), "its pixels were let go once uploaded")
        while (!load.poll()) yield()
        load.close()
        assertNull(load.failure)
        assertNull(resolver.loadMaterial(name, background), "nothing is left for the frame thread to decode")

        assertNotNull(resolver.createMaterial(renderer, name))
        val decoded = createBitmap(Base64.decode(PIXEL_PNG))
        val pixels = TextureAsset(decoded.toRgba8Bytes(), decoded.width, decoded.height)
        assertEquals(listOf<TextureAsset?>(pixels, pixels), uploaded, "the same pixels both times")
        background.cancel()
    }

    private companion object {
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
