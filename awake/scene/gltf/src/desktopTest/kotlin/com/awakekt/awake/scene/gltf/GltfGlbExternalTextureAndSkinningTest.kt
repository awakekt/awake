/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class GltfGlbExternalTextureAndSkinningTest {
    @Test
    fun glbWithExternalTextureResolvesSidecarImage() = runTest {
        val path = "models/block.glb"
        val png = Base64.decode(PIXEL_PNG)
        val glb = staticExternalTextureGlb("Textures/colormap.png")
        val resolver = GltfAssetResolver().apply {
            setAssetSource(
                inMemorySource(
                    mapOf(
                        path to glb,
                        "models/Textures/colormap.png" to png,
                    ),
                ),
            )
        }

        resolver.preload(path)

        assertEquals("gltf-material:$path", resolver.materialName(path))
        assertNotNull(resolver.createMaterial(NoopRenderer(), "gltf-material:$path"))
    }

    @Test
    fun glbWithMissingExternalTextureDegradesToUntexturedWithoutFailing() = runTest {
        val path = "models/orphan.glb"
        val glb = staticExternalTextureGlb("Textures/colormap.png")
        val resolver = GltfAssetResolver().apply {
            setAssetSource(inMemorySource(mapOf(path to glb)))
        }

        resolver.preload(path)

        assertEquals("lit-shadow", resolver.materialName(path))
        assertTrue(resolver.isPreloaded(path))
    }

    @Test
    fun skinnedGlbWithExternalTextureLoadsSkeletonAndTexturedParts() = runTest {
        val path = "models/character-oobi.glb"
        val png = Base64.decode(PIXEL_PNG)
        val glb = skinnedExternalTextureGlb("Textures/colormap.png")
        val resolver = GltfAssetResolver().apply {
            setAssetSource(
                inMemorySource(
                    mapOf(
                        path to glb,
                        "models/Textures/colormap.png" to png,
                    ),
                ),
            )
        }

        resolver.preload(path)

        val scene = assertNotNull(resolver.getLoadedScene(path), "skinned .glb must populate loadedScenes")
        assertEquals(2, scene.skinnedNodes.size)
        val slots = resolver.materialSlots(path)
        assertEquals(
            listOf("gltf-primitive:$path#0" to "gltf-material:$path#0", "gltf-primitive:$path#1" to "skinned-material"),
            slots.map { it.mesh to it.material },
        )
        assertEquals(VertexFormat.PositionNormalColorUvSkin, resolver.partGeometry("gltf-primitive:$path#0")?.format)
        assertNotNull(resolver.createMaterial(NoopRenderer(), "gltf-material:$path#0"))
    }

    private fun inMemorySource(files: Map<String, ByteArray>): AssetSource = AssetSource { assetPath ->
        files[assetPath.value]?.let(Result.Companion::success)
            ?: Result.failure(IllegalStateException("Missing asset: ${assetPath.value}"))
    }

    private fun staticExternalTextureGlb(imageUri: String): ByteArray {
        val bin = triangleBufferBytes()
        val json = """
            {
              "buffers": [{"byteLength":${bin.size}}],
              "bufferViews": [
                {"buffer":0,"byteOffset":0,"byteLength":36},
                {"buffer":0,"byteOffset":36,"byteLength":6}
              ],
              "accessors": [
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"}
              ],
              "materials": [{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}],
              "textures": [{"source":0}],
              "images": [{"uri":"$imageUri"}],
              "meshes": [{"primitives":[{"attributes":{"POSITION":0},"indices":1,"material":0}]}],
              "nodes": [{"mesh":0}],
              "scenes": [{"nodes":[0]}],
              "scene": 0
            }
        """.trimIndent()
        return packGlb(json, bin)
    }

    private fun skinnedExternalTextureGlb(imageUri: String): ByteArray {
        val bytes = triangleBufferBytes()
        val skinning = ByteArray(12) + floatBytes(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f) +
            floatBytes(0f, 0f, 1f, 0f, 0f, 1f) + floatBytes(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val bin = bytes + ByteArray(2) + skinning
        val base = bytes.size + 2
        val json = """
            {
              "buffers": [{"byteLength":${bin.size}}],
              "bufferViews": [
                {"buffer":0,"byteOffset":0,"byteLength":36},
                {"buffer":0,"byteOffset":36,"byteLength":6},
                {"buffer":0,"byteOffset":$base,"byteLength":12},
                {"buffer":0,"byteOffset":${base + 12},"byteLength":48},
                {"buffer":0,"byteOffset":${base + 60},"byteLength":24},
                {"buffer":0,"byteOffset":${base + 84},"byteLength":64}
              ],
              "accessors": [
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"},
                {"bufferView":2,"componentType":5121,"count":3,"type":"VEC4"},
                {"bufferView":3,"componentType":5126,"count":3,"type":"VEC4"},
                {"bufferView":4,"componentType":5126,"count":3,"type":"VEC2"},
                {"bufferView":5,"componentType":5126,"count":1,"type":"MAT4"}
              ],
              "materials": [{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}],
              "textures": [{"source":0}],
              "images": [{"uri":"$imageUri"}],
              "meshes": [
                {"primitives":[{"attributes":{"POSITION":0,"JOINTS_0":2,"WEIGHTS_0":3,"TEXCOORD_0":4},"indices":1,"material":0}]},
                {"primitives":[{"attributes":{"POSITION":0,"JOINTS_0":2,"WEIGHTS_0":3},"indices":1}]}
              ],
              "nodes": [{"name":"Root"}, {"mesh":0,"skin":0}, {"mesh":1,"skin":0}],
              "skins": [{"inverseBindMatrices":5,"joints":[0]}],
              "scenes": [{"nodes":[0,1,2]}],
              "scene": 0
            }
        """.trimIndent()
        return packGlb(json, bin)
    }

    private fun triangleBufferBytes(): ByteArray {
        val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val bytes = ByteArray(positions.size * 4 + 6)
        var offset = 0
        positions.forEach { value ->
            val bits = value.toRawBits()
            bytes[offset] = bits.toByte()
            bytes[offset + 1] = (bits ushr 8).toByte()
            bytes[offset + 2] = (bits ushr 16).toByte()
            bytes[offset + 3] = (bits ushr 24).toByte()
            offset += 4
        }
        bytes[offset + 2] = 2
        return bytes
    }

    private fun floatBytes(vararg values: Float): ByteArray {
        val bytes = ByteArray(values.size * 4)
        values.forEachIndexed { index, value ->
            val bits = value.toRawBits()
            for (byte in 0 until 4) bytes[index * 4 + byte] = (bits ushr (byte * 8)).toByte()
        }
        return bytes
    }

    private fun packGlb(json: String, bin: ByteArray): ByteArray {
        val rawJson = json.encodeToByteArray()
        val jsonPad = (4 - rawJson.size % 4) % 4
        val jsonChunk = rawJson + ByteArray(jsonPad) { 0x20 }
        val binPad = (4 - bin.size % 4) % 4
        val binChunk = bin + ByteArray(binPad)
        val total = 12 + 8 + jsonChunk.size + 8 + binChunk.size
        val out = ByteArray(total)
        fun writeIntLe(offset: Int, value: Int) {
            out[offset] = value.toByte()
            out[offset + 1] = (value ushr 8).toByte()
            out[offset + 2] = (value ushr 16).toByte()
            out[offset + 3] = (value ushr 24).toByte()
        }
        writeIntLe(0, 0x46546C67)
        writeIntLe(4, 2)
        writeIntLe(8, total)
        writeIntLe(12, jsonChunk.size)
        writeIntLe(16, 0x4E4F534A)
        jsonChunk.copyInto(out, 20)
        val binHeader = 20 + jsonChunk.size
        writeIntLe(binHeader, binChunk.size)
        writeIntLe(binHeader + 4, 0x004E4942)
        binChunk.copyInto(out, binHeader + 8)
        return out
    }

    private companion object {
        const val PIXEL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
