/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A skinned part's glTF `emissiveTexture` reaches its material as the emissive map, so it masks the
 * part's glow; before, the part was made from its base colour alone and glowed all over (#1829).
 */
@OptIn(ExperimentalEncodingApi::class)
class GltfSkinnedEmissiveTest {
    @Test
    fun aSkinnedPartIsMadeWithItsEmissiveTexture() = runTest {
        assertNotNull(skinnedPartMaterial(emissive = true)?.emissive, "the part's emissive texture is its material's emissive map")
    }

    @Test
    fun aSkinnedPartWithNoEmissiveTextureIsMadeWithNone() = runTest {
        assertNull(skinnedPartMaterial(emissive = false)?.emissive)
    }

    /** The PBR maps the first, textured, skinned part's material was made with. */
    private suspend fun skinnedPartMaterial(emissive: Boolean): PbrTextureSet? {
        val path = "assets/creatures/mount.gltf"
        val resolver = GltfAssetResolver()
        resolver.preload(path, skinnedJson(emissive).encodeToByteArray())
        var made: PbrTextureSet? = null
        val renderer = object : NoopRenderer() {
            override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material {
                made = pbrTextures ?: PbrTextureSet()
                return super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)
            }
        }
        assertNotNull(resolver.createMaterial(renderer, "gltf-material:$path#0"))
        return made
    }

    /** One triangle skinned to one joint, textured, with an emblem mask as its emissive texture when [emissive]. */
    private fun skinnedJson(emissive: Boolean): String {
        val positions = floats(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val indices = byteArrayOf(0, 0, 1, 0, 2, 0, 0, 0)
        val joints = ByteArray(12)
        val weights = floats(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
        val uvs = floats(0f, 0f, 1f, 0f, 0f, 1f)
        val inverseBind = floats(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val buffer = positions + indices + joints + weights + uvs + inverseBind
        var offset = 0
        val views = listOf(positions, indices, joints, weights, uvs, inverseBind).joinToString(",") { part ->
            """{"buffer":0,"byteOffset":$offset,"byteLength":${if (part === indices) 6 else part.size}}""".also { offset += part.size }
        }
        val emissiveTexture = if (emissive) ""","emissiveTexture":{"index":1},"emissiveFactor":[1,1,1]""" else ""
        return """
            {
              "buffers": [{"uri":"data:application/octet-stream;base64,${Base64.encode(buffer)}","byteLength":${buffer.size}}],
              "bufferViews": [$views],
              "accessors": [
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"},
                {"bufferView":2,"componentType":5121,"count":3,"type":"VEC4"},
                {"bufferView":3,"componentType":5126,"count":3,"type":"VEC4"},
                {"bufferView":4,"componentType":5126,"count":3,"type":"VEC2"},
                {"bufferView":5,"componentType":5126,"count":1,"type":"MAT4"}
              ],
              "materials": [{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}$emissiveTexture}],
              "textures": [{"source":0},{"source":1}],
              "images": [{"uri":"data:image/png;base64,$PIXEL_PNG"},{"uri":"data:image/png;base64,$PIXEL_PNG"}],
              "meshes": [{"primitives":[{"attributes":{"POSITION":0,"JOINTS_0":2,"WEIGHTS_0":3,"TEXCOORD_0":4},"indices":1,"material":0}]}],
              "nodes": [{"name":"Root"}, {"mesh":0,"skin":0}],
              "skins": [{"inverseBindMatrices":5,"joints":[0]}],
              "scenes": [{"nodes":[0,1]}],
              "scene": 0
            }
        """.trimIndent()
    }

    private fun floats(vararg values: Float): ByteArray = ByteArray(values.size * 4).also { bytes ->
        values.forEachIndexed { index, value ->
            val bits = value.toRawBits()
            for (byte in 0 until 4) bytes[index * 4 + byte] = (bits ushr (byte * 8)).toByte()
        }
    }

    private companion object {
        const val PIXEL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
