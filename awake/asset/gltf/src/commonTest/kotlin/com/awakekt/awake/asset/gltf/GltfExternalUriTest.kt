/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** A glTF names its sidecar files by percent-encoded URI, as the spec requires, and they're read by their real names. */
class GltfExternalUriTest {
    private val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
    private val buffer = ByteArray(positions.size * Float.SIZE_BYTES).also { bytes ->
        positions.forEachIndexed { i, value ->
            val bits = value.toRawBits()
            for (b in 0 until Float.SIZE_BYTES) bytes[i * Float.SIZE_BYTES + b] = (bits ushr (b * Byte.SIZE_BITS)).toByte()
        }
    }
    private val texture = byteArrayOf(1, 2, 3, 4)

    private fun gltf(bufferUri: String, imageUri: String) = """
        {
          "buffers": [ { "uri": "$bufferUri", "byteLength": ${buffer.size} } ],
          "bufferViews": [ { "buffer": 0, "byteOffset": 0, "byteLength": ${buffer.size} } ],
          "accessors": [ { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" } ],
          "images": [ { "uri": "$imageUri" } ],
          "meshes": [ { "primitives": [ { "attributes": { "POSITION": 0 } } ] } ]
        }
    """.trimIndent()

    /** The files under `models/`, by their real names. */
    private fun files(vararg entries: Pair<String, ByteArray>): AssetSource {
        val stored = entries.toMap()
        return AssetSource { path -> runCatching { stored.getValue(path.value) } }
    }

    @Test
    fun aSpaceWrittenAsPercent20ReadsTheFileWithTheSpace() = runTest {
        val json = gltf(bufferUri = "Harbor%20Crate.bin", imageUri = "Part_Foot%20.png")
        val source = files("models/Harbor Crate.bin" to buffer, "models/Part_Foot .png" to texture)

        val external = GltfParser.loadExternalResources(json, AssetPath("models/crate.gltf"), source).getOrThrow()

        assertContentEquals(texture, external["Part_Foot%20.png"], "keyed by the URI as the document writes it")
        assertEquals(positions.toList(), GltfParser.parse(json, external).positions.toList())
    }

    @Test
    fun aLoadedModelReadsItsEncodedSidecars() = runTest {
        val source = files(
            "models/crate.gltf" to gltf(bufferUri = "caf%C3%A9%20data.bin", imageUri = "skin.png").encodeToByteArray(),
            "models/café data.bin" to buffer,
            "models/skin.png" to texture,
        )

        val mesh = GltfParser.load(AssetPath("models/crate.gltf"), source).getOrThrow()

        assertEquals(positions.toList(), mesh.positions.toList(), "UTF-8 escapes decode to the file's name")
    }

    @Test
    fun aUriWithNothingEncodedReadsAsBefore() = runTest {
        val source = files("models/mesh.bin" to buffer, "models/skin.png" to texture)

        val external = GltfParser.loadExternalResources(gltf("mesh.bin", "skin.png"), AssetPath("models/crate.gltf"), source).getOrThrow()

        assertEquals(setOf("mesh.bin", "skin.png"), external.keys)
    }

    @Test
    fun onlyAPercentWithTwoHexDigitsIsAnEscape() {
        assertEquals("Part Foot.png", GltfParser.percentDecoded("Part%20Foot.png"))
        assertEquals("café.png", GltfParser.percentDecoded("caf%c3%A9.png"), "either case of hex digit")
        assertEquals("100%.png", GltfParser.percentDecoded("100%.png"))
        assertEquals("a%2", GltfParser.percentDecoded("a%2"), "an escape cut short at the end")
        assertEquals("50%zz.png", GltfParser.percentDecoded("50%zz.png"))
        assertEquals("a+b.png", GltfParser.percentDecoded("a+b.png"), "a plus is a plus outside form encoding")
        assertEquals("50%.png", GltfParser.percentDecoded("50%25.png"), "an encoded percent decodes once")
    }
}
