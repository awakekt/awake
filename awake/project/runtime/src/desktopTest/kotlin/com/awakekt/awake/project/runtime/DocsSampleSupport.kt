/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import java.io.File
import kotlin.io.encoding.Base64

/** Tests run from the module directory; the snippets live with the docs. */
internal val DOCS_SNIPPETS = File("../../../website/docs/snippets")

internal fun rendering(snippet: String): SceneDocument {
    DefaultSceneComponentResolvers.install()
    return SceneLoader.decode(File(DOCS_SNIPPETS, "rendering/$snippet").readText())
}

/** A GPU-free renderer that keeps the last frame's draw requests and counts attached content features. */
internal class DocsRenderer :
    NoopRenderer(),
    GpuDrawPreparationSource,
    ContentFeatureHost {
    val draws = mutableListOf<GpuDrawRequest>()
    var attachedFeatures = 0
        private set

    override val gpuDrawPreparer = GpuDrawPreparer { request, sourceIndex, _ ->
        if (sourceIndex == 0) draws.clear()
        draws += request
        null
    }

    override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature {
        attachedFeatures++
        return AttachedContentFeature { attachedFeatures-- }
    }
}

internal const val FRAME = 1f / 60f
internal const val WIDTH = 800f
internal const val HEIGHT = 600f

/** A one-triangle static glTF with an embedded buffer. */
internal fun triangleGltf(): String {
    val positions = littleEndian(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
    val indices = byteArrayOf(0, 0, 1, 0, 2, 0, 0, 0)
    val buffer = positions + indices
    return """
    {
      "asset": {"version": "2.0"},
      "buffers": [{"uri": "data:application/octet-stream;base64,${Base64.encode(buffer)}", "byteLength": ${buffer.size}}],
      "bufferViews": [
        {"buffer": 0, "byteOffset": 0, "byteLength": ${positions.size}},
        {"buffer": 0, "byteOffset": ${positions.size}, "byteLength": ${indices.size}}
      ],
      "accessors": [
        {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
        {"bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR"}
      ],
      "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "indices": 1}]}],
      "nodes": [{"name": "Body", "mesh": 0}],
      "scenes": [{"nodes": [0]}],
      "scene": 0
    }
    """.trimIndent()
}

/**
 * One triangle skinned to two joints. The second joint turns 90 degrees about Z over one second,
 * carrying the triangle's top vertex with it. Its one clip is unnamed, so it plays as `clip_0`.
 */
internal fun skinnedArmGltf(): String {
    val positions = littleEndian(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
    val joints = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0)
    val weights = littleEndian(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
    val indices = byteArrayOf(0, 0, 1, 0, 2, 0, 0, 0)
    val identity = littleEndian(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
    val inverseBind = identity + littleEndian(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 1f)
    val times = littleEndian(0f, 1f)
    val rotations = littleEndian(0f, 0f, 0f, 1f, 0f, 0f, 0.7071f, 0.7071f)
    val chunks = listOf(positions, joints, weights, indices, inverseBind, times, rotations)
    val buffer = chunks.reduce(ByteArray::plus)
    val offsets = chunks.runningFold(0) { offset, chunk -> offset + chunk.size }
    fun view(index: Int) = """{"buffer":0,"byteOffset":${offsets[index]},"byteLength":${chunks[index].size}}"""
    return """
    {
      "asset": {"version": "2.0"},
      "buffers": [{"uri": "data:application/octet-stream;base64,${Base64.encode(buffer)}", "byteLength": ${buffer.size}}],
      "bufferViews": [${chunks.indices.joinToString(",") { view(it) }}],
      "accessors": [
        {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
        {"bufferView": 1, "componentType": 5121, "count": 3, "type": "VEC4"},
        {"bufferView": 2, "componentType": 5126, "count": 3, "type": "VEC4"},
        {"bufferView": 3, "componentType": 5123, "count": 3, "type": "SCALAR"},
        {"bufferView": 4, "componentType": 5126, "count": 2, "type": "MAT4"},
        {"bufferView": 5, "componentType": 5126, "count": 2, "type": "SCALAR"},
        {"bufferView": 6, "componentType": 5126, "count": 2, "type": "VEC4"}
      ],
      "meshes": [{"primitives": [{"attributes": {"POSITION": 0, "JOINTS_0": 1, "WEIGHTS_0": 2}, "indices": 3}]}],
      "nodes": [
        {"name": "Root", "children": [1]},
        {"name": "Tip", "translation": [0, 1, 0]},
        {"name": "Body", "mesh": 0, "skin": 0}
      ],
      "skins": [{"inverseBindMatrices": 4, "joints": [0, 1]}],
      "animations": [{"channels": [{"sampler": 0, "target": {"node": 1, "path": "rotation"}}],
                      "samplers": [{"input": 5, "output": 6, "interpolation": "LINEAR"}]}],
      "scenes": [{"nodes": [0, 2]}],
      "scene": 0
    }
    """.trimIndent()
}

private fun littleEndian(vararg values: Float): ByteArray {
    val bytes = ByteArray(values.size * 4)
    values.forEachIndexed { index, value ->
        val bits = value.toRawBits()
        for (byte in 0 until 4) bytes[index * 4 + byte] = (bits ushr (byte * 8)).toByte()
    }
    return bytes
}

/** Serves [files] by path, the way a project directory would. */
internal fun files(vararg files: Pair<String, String>): AssetSource {
    val byPath = files.toMap()
    return AssetSource { path -> runCatching { byPath.getValue(path.value).encodeToByteArray() } }
}
