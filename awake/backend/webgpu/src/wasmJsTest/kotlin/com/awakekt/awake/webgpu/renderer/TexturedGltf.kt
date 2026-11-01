/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.pipeline.GpuCapability
import com.awakekt.awake.render.pipeline.GpuCapabilityKind
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.awakekt.awake.render.renderer.Renderer as RenderRenderer

/**
 * A one-triangle glTF whose material has a [baseColor] and a [normal] map, each embedded as a data URI
 * of the image file it holds: what a scene's model brings, with its images inline.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun texturedGltf(baseColor: ByteArray, normal: ByteArray): ByteArray {
    val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
    val buffer = ByteArray(positions.size * Float.SIZE_BYTES + INDEX_BYTES)
    positions.forEachIndexed { i, value ->
        val bits = value.toRawBits()
        for (b in 0 until Float.SIZE_BYTES) buffer[i * Float.SIZE_BYTES + b] = (bits ushr (b * 8)).toByte()
    }
    buffer[positions.size * Float.SIZE_BYTES + 2] = 1
    buffer[positions.size * Float.SIZE_BYTES + 4] = 2
    fun png(bytes: ByteArray) = "data:image/png;base64,${Base64.encode(bytes)}"
    return """
        {
          "asset": {"version": "2.0"},
          "buffers": [{"uri": "data:application/octet-stream;base64,${Base64.encode(buffer)}", "byteLength": ${buffer.size}}],
          "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 36}, {"buffer": 0, "byteOffset": 36, "byteLength": 6}],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3"},
            {"bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR"}
          ],
          "materials": [{"pbrMetallicRoughness": {"baseColorTexture": {"index": 0}}, "normalTexture": {"index": 1}}],
          "textures": [{"source": 0}, {"source": 1}],
          "images": [{"uri": "${png(baseColor)}"}, {"uri": "${png(normal)}"}],
          "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "indices": 1, "material": 0}]}],
          "nodes": [{"mesh": 0}],
          "scenes": [{"nodes": [0]}],
          "scene": 0
        }
    """.trimIndent().encodeToByteArray()
}

/** [renderer] without its GPU image upload: every `createImageMaterial` through it takes the CPU path. */
internal fun cpuOnly(renderer: Renderer): RenderRenderer = object : RenderRenderer by renderer {
    override fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? = null
}

private const val INDEX_BYTES = 6
