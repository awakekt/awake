/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.player

import com.awakekt.awake.core.io.AssetSource
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class GltfAssetResolverTextureTest {
    @Test
    fun embeddedBaseColorTextureSelectsAPathScopedMaterial() = runTest {
        val path = "assets/painted-triangle.gltf"
        val resolver = GltfAssetResolver()

        resolver.preloadMaterials(path, texturedTriangleJson().encodeToByteArray())

        assertEquals("gltf-material:$path", resolver.materialName(path))
        val parameters = assertNotNull(resolver.materialParameters(path))
        assertEquals(0.7f, parameters.metallic)
        assertEquals(0.35f, parameters.roughness)
    }

    @Test
    fun multiplePrimitiveMaterialsBecomeIndependentRenderSlots() = runTest {
        val path = "assets/multi-material.gltf"
        val resolver = GltfAssetResolver()

        resolver.preloadMaterials(path, texturedTriangleJson(primitiveCount = 2).encodeToByteArray())

        val slots = resolver.materialSlots(path)
        assertEquals(2, slots.size)
        assertEquals("gltf-primitive:$path#0", slots[0].mesh)
        assertEquals("gltf-material:$path#0", slots[0].material)
        assertEquals("gltf-primitive:$path#1", slots[1].mesh)
        assertEquals("gltf-material:$path#1", slots[1].material)
    }

    @Test
    fun aSavedPrimitiveMeshResolvesToItsModelFile() {
        val resolver = GltfAssetResolver()
        val slotMesh = "gltf-primitive:assets/models/house#2.glb#1"

        assertTrue(resolver.canResolveMesh(slotMesh))
        assertEquals("assets/models/house#2.glb", resolver.modelPath(slotMesh))
        assertEquals("assets/models/house.glb", resolver.modelPath("assets/models/house.glb"))
        assertFalse(resolver.canResolveMesh("gltf-primitive:assets/models/notes.txt#0"))
    }

    @Test
    fun externalSidecarImageResolvesRelativeToTheModelPath() = runTest {
        val path = "assets/models/sidecar.gltf"
        val resolver = GltfAssetResolver()
        val imageBytes = Base64.Default.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
        resolver.setAssetSource(
            AssetSource { assetPath ->
                imageBytes.takeIf { assetPath.value == "assets/models/paint.png" }
                    ?.let(Result.Companion::success)
                    ?: Result.failure(IllegalStateException("Missing asset: ${assetPath.value}"))
            },
        )

        resolver.preloadMaterials(
            path,
            texturedTriangleJson(externalImage = true).encodeToByteArray(),
        )

        assertEquals("gltf-material:$path", resolver.materialName(path))
        assertNotNull(resolver.materialParameters(path))
    }

    @Test
    fun externalSidecarBufferResolvesRelativeToTheModelPath() = runTest {
        val path = "assets/models/sidecar.gltf"
        val resolver = GltfAssetResolver()
        resolver.setAssetSource(
            AssetSource { assetPath ->
                if (assetPath.value == "assets/models/triangle.bin") {
                    Result.success(triangleBufferBytes())
                } else {
                    Result.failure(IllegalStateException("Missing asset: ${assetPath.value}"))
                }
            },
        )

        resolver.preload(path, texturedTriangleJson(externalBuffer = true).encodeToByteArray())

        assertEquals("gltf-material:$path", resolver.materialName(path))
        assertNotNull(resolver.materialParameters(path))
    }

    private fun texturedTriangleJson(
        primitiveCount: Int = 1,
        externalImage: Boolean = false,
        externalBuffer: Boolean = false,
    ): String {
        val bytes = triangleBufferBytes()
        val image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        val imageUri = if (externalImage) "paint.png" else "data:image/png;base64,$image"
        val bufferUri = if (externalBuffer) "triangle.bin" else "data:application/octet-stream;base64,${Base64.encode(bytes)}"
        val materials = List(primitiveCount) {
            """{"pbrMetallicRoughness":{"baseColorTexture":{"index":$it},"metallicFactor":0.7,"roughnessFactor":0.35}}"""
        }.joinToString(",")
        val textures = List(primitiveCount) { """{"source":$it}""" }.joinToString(",")
        val images = List(primitiveCount) { """{"uri":"$imageUri"}""" }.joinToString(",")
        val primitives = List(primitiveCount) {
            """{"attributes":{"POSITION":0},"indices":1,"material":$it}"""
        }.joinToString(",")
        return """
            {
              "buffers": [{"uri":"$bufferUri","byteLength":${bytes.size}}],
              "bufferViews": [
                {"buffer":0,"byteOffset":0,"byteLength":36},
                {"buffer":0,"byteOffset":36,"byteLength":6}
              ],
              "accessors": [
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"}
              ],
              "materials": [$materials],
              "textures":[$textures],
              "images":[$images],
              "meshes":[{"primitives":[$primitives]}],
              "nodes":[{"mesh":0}],
              "scenes":[{"nodes":[0]}],
              "scene":0
            }
        """.trimIndent()
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
}
