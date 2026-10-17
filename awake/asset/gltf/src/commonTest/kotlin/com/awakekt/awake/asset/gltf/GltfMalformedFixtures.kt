/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Hand-made glTF and GLB inputs, valid ones to break and the helpers that break them. */
@OptIn(ExperimentalEncodingApi::class)
internal object GltfMalformedFixtures {
    const val CHUNK_JSON = 0x4E4F534A
    const val CHUNK_BIN = 0x004E4942
    private const val GLB_MAGIC = 0x46546C67

    /** A triangle: 3 float positions at byte 0, then 3 unsigned-short indices at byte 36, padded to 44. */
    val triangleBin: ByteArray = ByteArray(44).also { bytes ->
        floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f).forEachIndexed { i, value -> putInt(bytes, i * 4, value.toRawBits()) }
        intArrayOf(0, 1, 2).forEachIndexed { i, value -> putShort(bytes, 36 + i * 2, value) }
    }

    /** The triangle document for [triangleBin]; each argument replaces one array's contents. */
    fun triangleJson(
        buffers: String = """{ "byteLength": 44 }""",
        bufferViews: String = """
            { "buffer": 0, "byteOffset": 0, "byteLength": 36 },
            { "buffer": 0, "byteOffset": 36, "byteLength": 6 }
        """,
        accessors: String = """
            { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
            { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
        """,
        primitives: String = """{ "attributes": { "POSITION": 0 }, "indices": 1 }""",
        nodes: String = """{ "mesh": 0 }""",
        scenes: String = """{ "nodes": [0] }""",
        extra: String = "",
    ): String = """
        {
          "asset": { "version": "2.0" },
          "buffers": [ $buffers ],
          "bufferViews": [ $bufferViews ],
          "accessors": [ $accessors ],
          "meshes": [ { "primitives": [ $primitives ] } ],
          "nodes": [ $nodes ],
          "scenes": [ $scenes ]
          $extra
        }
    """.trimIndent()

    /** A spec-shaped GLB: header, the JSON chunk padded with spaces, the BIN chunk when there is one. */
    fun glb(json: String = triangleJson(), bin: ByteArray? = triangleBin): ByteArray {
        val jsonBytes = json.encodeToByteArray()
        val padded = jsonBytes + ByteArray((4 - jsonBytes.size % 4) % 4) { ' '.code.toByte() }
        val chunks = listOfNotNull(CHUNK_JSON to padded, bin?.let { CHUNK_BIN to it })
        return container(chunks.map { (type, data) -> chunk(type, data) })
    }

    /** One chunk: its [length] (the data's own size unless given), [type], then [data]. */
    fun chunk(type: Int, data: ByteArray, length: Int = data.size): ByteArray =
        ByteArray(8 + data.size).also { bytes ->
            putInt(bytes, 0, length)
            putInt(bytes, 4, type)
            data.copyInto(bytes, 8)
        }

    /** A GLB header followed by [chunks], declaring [totalLength] (the real length unless given). */
    fun container(chunks: List<ByteArray>, version: Int = 2, totalLength: Int? = null): ByteArray {
        val body = chunks.fold(ByteArray(0)) { all, chunk -> all + chunk }
        return ByteArray(12 + body.size).also { bytes ->
            putInt(bytes, 0, GLB_MAGIC)
            putInt(bytes, 4, version)
            putInt(bytes, 8, totalLength ?: (12 + body.size))
            body.copyInto(bytes, 12)
        }
    }

    /**
     * A skinned document with its buffer as a data URI: three skinned vertices on joint 0, one inverse
     * bind matrix, and a two-key translation animation. Each argument replaces one array's contents.
     */
    fun skinnedJson(
        accessors: String = """
            { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
            { "bufferView": 1, "componentType": 5121, "count": 3, "type": "VEC4" },
            { "bufferView": 2, "componentType": 5126, "count": 3, "type": "VEC4" },
            { "bufferView": 3, "componentType": 5126, "count": 1, "type": "MAT4" },
            { "bufferView": 4, "componentType": 5126, "count": 2, "type": "SCALAR" },
            { "bufferView": 5, "componentType": 5126, "count": 2, "type": "VEC3" }
        """,
        skins: String = """{ "joints": [0], "inverseBindMatrices": 3 }""",
        samplers: String = """{ "input": 4, "output": 5 }""",
        uri: String = "data:application/octet-stream;base64," + Base64.encode(skinnedBin),
    ): String = """
        {
          "asset": { "version": "2.0" },
          "buffers": [ { "uri": "$uri", "byteLength": ${skinnedBin.size} } ],
          "bufferViews": [
            { "buffer": 0, "byteOffset": 0, "byteLength": 36 },
            { "buffer": 0, "byteOffset": 36, "byteLength": 12 },
            { "buffer": 0, "byteOffset": 48, "byteLength": 48 },
            { "buffer": 0, "byteOffset": 96, "byteLength": 64 },
            { "buffer": 0, "byteOffset": 160, "byteLength": 8 },
            { "buffer": 0, "byteOffset": 168, "byteLength": 24 }
          ],
          "accessors": [ $accessors ],
          "meshes": [ { "primitives": [ { "attributes": { "POSITION": 0, "JOINTS_0": 1, "WEIGHTS_0": 2 } } ] } ],
          "nodes": [ { "children": [1] }, { "mesh": 0, "skin": 0 } ],
          "scenes": [ { "nodes": [0] } ],
          "skins": [ $skins ],
          "animations": [ {
            "channels": [ { "sampler": 0, "target": { "node": 0, "path": "translation" } } ],
            "samplers": [ $samplers ]
          } ]
        }
    """.trimIndent()

    private val skinnedBin: ByteArray = ByteArray(192).also { bytes ->
        floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f).forEachIndexed { i, value -> putInt(bytes, i * 4, value.toRawBits()) }
        // Joints: all zero already. Weights: (1, 0, 0, 0) per vertex.
        repeat(3) { vertex -> putInt(bytes, 48 + vertex * 16, 1f.toRawBits()) }
        floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            .forEachIndexed { i, value -> putInt(bytes, 96 + i * 4, value.toRawBits()) }
        floatArrayOf(0f, 1f).forEachIndexed { i, value -> putInt(bytes, 160 + i * 4, value.toRawBits()) }
        floatArrayOf(0f, 0f, 0f, 0f, 2f, 0f).forEachIndexed { i, value -> putInt(bytes, 168 + i * 4, value.toRawBits()) }
    }

    fun putInt(bytes: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) bytes[offset + i] = (value ushr (8 * i)).toByte()
    }

    fun putShort(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    /** How long one malformed input may take to be refused. */
    val promptly: Duration = 2.seconds

    /**
     * Runs [parse], requiring it to fail with [GltfFormatException] at [location] (or one starting with
     * it when [prefix]) within [promptly].
     */
    fun assertMalformed(location: String, prefix: Boolean = false, parse: () -> Unit): GltfFormatException {
        val start = TimeSource.Monotonic.markNow()
        val failure = try {
            parse()
            fail("Expected a GltfFormatException at $location, but the parse succeeded.")
        } catch (expected: GltfFormatException) {
            expected
        }
        val elapsed = start.elapsedNow()
        assertTrue(elapsed < promptly, "Refusing the input took $elapsed: ${failure.message}")
        if (prefix) {
            assertTrue(failure.location.startsWith(location), "Expected a location under $location, was: ${failure.message}")
        } else {
            assertEquals(location, failure.location, "Wrong location in: ${failure.message}")
        }
        return failure
    }
}
