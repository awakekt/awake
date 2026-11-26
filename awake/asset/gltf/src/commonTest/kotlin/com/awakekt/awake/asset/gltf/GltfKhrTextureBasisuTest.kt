/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Verifies that `KHR_texture_basisu` on glTF textures resolves KTX2 images across base-color and
 * PBR channels, both standalone and alongside a PNG/JPEG fallback `source`.
 */
@OptIn(ExperimentalEncodingApi::class)
class GltfKhrTextureBasisuTest {

    @Test
    fun khrTextureBasisuResolvesBaseColorAndNormalWithoutFallbackSource() {
        val ktxBase = byteArrayOf(0xAB.toByte(), 0x4B, 0x54, 0x58, 1, 2, 3, 4)
        val ktxNormal = byteArrayOf(0xAB.toByte(), 0x4B, 0x54, 0x58, 5, 6, 7, 8)
        val json = triangleGltf(
            texturesJson = """
                [
                  { "extensions": { "KHR_texture_basisu": { "source": 0 } } },
                  { "extensions": { "KHR_texture_basisu": { "source": 1 } } }
                ]
            """.trimIndent(),
            imagesJson = """
                [
                  { "uri": "albedo.ktx2", "mimeType": "image/ktx2" },
                  { "uri": "normal.ktx2", "mimeType": "image/ktx2" }
                ]
            """.trimIndent(),
        )

        assertEquals(listOf("albedo.ktx2", "normal.ktx2"), GltfParser.externalUris(json))

        val mesh = GltfParser.parse(json, mapOf("albedo.ktx2" to ktxBase, "normal.ktx2" to ktxNormal))
        assertContentEquals(ktxBase, mesh.baseColorImageBytes)
        assertContentEquals(ktxNormal, mesh.normalImageBytes)
    }

    @Test
    fun khrTextureBasisuPrefersKtx2OverFallbackSourceAndFallsBackWhenAbsent() {
        val ktxBytes = byteArrayOf(0xAB.toByte(), 0x4B, 0x54, 0x58, 9, 9)
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 1)
        val json = triangleGltf(
            texturesJson = """
                [
                  { "source": 1, "extensions": { "KHR_texture_basisu": { "source": 0 } } }
                ]
            """.trimIndent(),
            imagesJson = """
                [
                  { "uri": "albedo.ktx2", "mimeType": "image/ktx2" },
                  { "uri": "albedo.png", "mimeType": "image/png" }
                ]
            """.trimIndent(),
            includeNormal = false,
        )

        val withKtx = GltfParser.parse(json, mapOf("albedo.ktx2" to ktxBytes, "albedo.png" to pngBytes))
        assertContentEquals(ktxBytes, withKtx.baseColorImageBytes)

        val fallbackOnly = GltfParser.parse(json, mapOf("albedo.png" to pngBytes))
        assertContentEquals(pngBytes, fallbackOnly.baseColorImageBytes)
    }

    private fun triangleGltf(
        texturesJson: String,
        imagesJson: String,
        includeNormal: Boolean = true,
    ): String {
        val buf = buildTriangleBuffer()
        val dataUri = "data:application/octet-stream;base64,${Base64.encode(buf)}"
        val normalField = if (includeNormal) """, "normalTexture": { "index": 1 }""" else ""
        return """
            {
              "asset": { "version": "2.0" },
              "extensionsUsed": ["KHR_texture_basisu"],
              "buffers": [{ "uri": "$dataUri", "byteLength": ${buf.size} }],
              "bufferViews": [
                { "buffer": 0, "byteOffset": 0, "byteLength": 36 },
                { "buffer": 0, "byteOffset": 36, "byteLength": 24 },
                { "buffer": 0, "byteOffset": 60, "byteLength": 6 }
              ],
              "accessors": [
                { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0, 0, 0], "max": [1, 1, 0] },
                { "bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC2" },
                { "bufferView": 2, "componentType": 5123, "count": 3, "type": "SCALAR" }
              ],
              "images": $imagesJson,
              "textures": $texturesJson,
              "materials": [{
                "pbrMetallicRoughness": { "baseColorTexture": { "index": 0 } }$normalField
              }],
              "meshes": [{
                "primitives": [{
                  "attributes": { "POSITION": 0, "TEXCOORD_0": 1 },
                  "indices": 2,
                  "material": 0
                }]
              }]
            }
        """.trimIndent()
    }

    private fun buildTriangleBuffer(): ByteArray {
        val floats = floatArrayOf(
            0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f,
            0f, 0f, 1f, 0f, 0f, 1f,
        )
        val out = ByteArray(66)
        var at = 0
        for (f in floats) {
            val bits = f.toRawBits()
            out[at++] = (bits and 0xFF).toByte()
            out[at++] = ((bits ushr 8) and 0xFF).toByte()
            out[at++] = ((bits ushr 16) and 0xFF).toByte()
            out[at++] = ((bits ushr 24) and 0xFF).toByte()
        }
        for (idx in shortArrayOf(0, 1, 2)) {
            out[at++] = (idx.toInt() and 0xFF).toByte()
            out[at++] = ((idx.toInt() ushr 8) and 0xFF).toByte()
        }
        return out
    }
}
