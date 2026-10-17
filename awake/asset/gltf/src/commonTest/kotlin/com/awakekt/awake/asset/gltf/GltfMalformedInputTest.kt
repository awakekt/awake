/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

import com.awakekt.awake.asset.gltf.GltfMalformedFixtures.CHUNK_BIN
import com.awakekt.awake.asset.gltf.GltfMalformedFixtures.CHUNK_JSON
import com.awakekt.awake.asset.gltf.GltfMalformedFixtures.assertMalformed
import com.awakekt.awake.asset.gltf.GltfMalformedFixtures.chunk
import com.awakekt.awake.asset.gltf.GltfMalformedFixtures.container
import com.awakekt.awake.asset.gltf.GltfMalformedFixtures.glb
import com.awakekt.awake.asset.gltf.GltfMalformedFixtures.skinnedJson
import com.awakekt.awake.asset.gltf.GltfMalformedFixtures.triangleBin
import com.awakekt.awake.asset.gltf.GltfMalformedFixtures.triangleJson
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * A corpus of hand-made malformed glTF and GLB files. Each must be refused promptly with a
 * [GltfFormatException] naming where the problem is, never with a hang, an out-of-memory error or an
 * untyped exception.
 */
class GltfMalformedInputTest {
    private fun scene(json: String, bin: ByteArray? = triangleBin, limits: GltfLimits = GltfLimits.Default) {
        GltfParser.parseScene(glb(json, bin), limits = limits)
    }

    private fun jsonChunk(json: String = triangleJson()): ByteArray {
        val bytes = json.encodeToByteArray()
        return chunk(CHUNK_JSON, bytes + ByteArray((4 - bytes.size % 4) % 4) { ' '.code.toByte() })
    }

    @Test
    fun theBaselinesParse() {
        assertEquals(1, GltfParser.parseScene(glb()).meshes.size)
        val skinned = GltfParser.parseSkinned(skinnedJson())
        assertEquals(1, skinned.skinnedNodes.size)
        assertEquals(1, skinned.clips.single().channels.size)
    }

    // --- GLB container ---

    @Test
    fun aFileShorterThanTheHeaderIsRefused() {
        assertMalformed("glb.header") { GltfParser.parseScene(ByteArray(5)) }
    }

    @Test
    fun aWrongMagicIsRefused() {
        val bytes = glb().also { it[0] = 'x'.code.toByte() }
        assertMalformed("glb.header.magic") { GltfParser.parseScene(bytes) }
    }

    @Test
    fun anotherGlbVersionIsRefused() {
        assertMalformed("glb.header.version") { GltfParser.parseScene(container(listOf(jsonChunk()), version = 1)) }
    }

    @Test
    fun aDeclaredLengthPastTheFileIsRefused() {
        val bytes = container(listOf(jsonChunk()), totalLength = 1 shl 20)
        assertMalformed("glb.header.length") { GltfParser.parseScene(bytes) }
    }

    @Test
    fun aChunkLengthThatReadsNegativeIsRefusedInsteadOfLooping() {
        // 0xFFFFFFF8 read as a signed Int is -8: the old loop never moved past this chunk.
        val unknown = chunk(type = 0x12345678, data = ByteArray(0), length = 0xFFFFFFF8.toInt())
        assertMalformed("glb.chunks[1].length") { GltfParser.parseScene(container(listOf(jsonChunk(), unknown))) }
    }

    @Test
    fun aChunkRunningPastTheFileIsRefused() {
        val bin = chunk(CHUNK_BIN, triangleBin, length = 4096)
        assertMalformed("glb.chunks[1].length") { GltfParser.parseScene(container(listOf(jsonChunk(), bin))) }
    }

    @Test
    fun anUnalignedChunkLengthIsRefused() {
        val bin = chunk(CHUNK_BIN, triangleBin, length = 42)
        assertMalformed("glb.chunks[1].length") { GltfParser.parseScene(container(listOf(jsonChunk(), bin))) }
    }

    @Test
    fun aTruncatedChunkHeaderIsRefused() {
        val bytes = container(listOf(jsonChunk(), ByteArray(4)))
        assertMalformed("glb.chunks[1]") { GltfParser.parseScene(bytes) }
    }

    @Test
    fun aTruncatedFileIsRefused() {
        val whole = glb()
        val truncated = whole.copyOf(whole.size - 10)
        GltfMalformedFixtures.putInt(truncated, 8, truncated.size)
        assertMalformed("glb.chunks[1].length") { GltfParser.parseScene(truncated) }
    }

    @Test
    fun aFileWithoutAJsonChunkIsRefused() {
        assertMalformed("glb") { GltfParser.parseScene(container(listOf(chunk(CHUNK_BIN, triangleBin)))) }
    }

    @Test
    fun aSecondJsonChunkIsRefused() {
        assertMalformed("glb.chunks[1]") { GltfParser.parseScene(container(listOf(jsonChunk(), jsonChunk()))) }
    }

    @Test
    fun aJsonChunkPastTheLimitIsRefused() {
        assertMalformed("glb.chunks[0].length") { scene(triangleJson(), limits = GltfLimits(maxJsonBytes = 64)) }
    }

    @Test
    fun jsonThatIsNotADocumentIsRefused() {
        assertMalformed("json") { scene("{ \"accessors\": [ { \"count\": ") }
        assertMalformed("json") { GltfParser.parse("not json at all") }
    }

    @Test
    fun aNumberTooLargeForItsFieldIsRefusedAsJson() {
        assertMalformed("json") {
            scene(triangleJson(accessors = """{ "bufferView": 0, "componentType": 5126, "count": 1e20, "type": "VEC3" }"""))
        }
    }

    // --- buffers and bufferViews ---

    @Test
    fun badBase64IsRefused() {
        val json = triangleJson(buffers = """{ "uri": "data:application/octet-stream;base64,@@not*base64@@", "byteLength": 44 }""")
        assertMalformed("buffers[0].uri") { scene(json, bin = null) }
    }

    @Test
    fun aDataUriThatIsNotBase64IsRefused() {
        val json = triangleJson(buffers = """{ "uri": "data:text/plain,hello", "byteLength": 5 }""")
        assertMalformed("buffers[0].uri") { scene(json, bin = null) }
    }

    @Test
    fun aBufferWithoutBytesIsRefused() {
        assertMalformed("buffers[0]") { scene(triangleJson(), bin = null) }
    }

    @Test
    fun aBufferLongerThanItsBytesIsRefused() {
        assertMalformed("buffers[0].byteLength") { scene(triangleJson(buffers = """{ "byteLength": 4096 }""")) }
    }

    @Test
    fun anOutOfRangeBufferIndexIsRefused() {
        val json = triangleJson(
            bufferViews = """
                { "buffer": 7, "byteOffset": 0, "byteLength": 36 },
                { "buffer": 0, "byteOffset": 36, "byteLength": 6 }
            """,
        )
        assertMalformed("bufferViews[0].buffer") { scene(json) }
    }

    @Test
    fun aNegativeBufferViewOffsetIsRefused() {
        val json = triangleJson(
            bufferViews = """
                { "buffer": 0, "byteOffset": -36, "byteLength": 36 },
                { "buffer": 0, "byteOffset": 36, "byteLength": 6 }
            """,
        )
        assertMalformed("bufferViews[0].byteOffset") { scene(json) }
    }

    @Test
    fun aBufferViewPastItsBufferIsRefused() {
        val json = triangleJson(
            bufferViews = """
                { "buffer": 0, "byteOffset": 2147483600, "byteLength": 2147483600 },
                { "buffer": 0, "byteOffset": 36, "byteLength": 6 }
            """,
        )
        assertMalformed("bufferViews[0]") { scene(json) }
    }

    @Test
    fun aNegativeStrideIsRefused() {
        val json = triangleJson(
            bufferViews = """
                { "buffer": 0, "byteOffset": 0, "byteLength": 36, "byteStride": -12 },
                { "buffer": 0, "byteOffset": 36, "byteLength": 6 }
            """,
        )
        assertMalformed("bufferViews[0].byteStride") { scene(json) }
    }

    // --- accessors ---

    @Test
    fun aHugeCountOverATinyBufferIsRefusedBeforeAllocating() {
        // 2^24 VEC3 floats would be a 192 MiB FloatArray; the 36-byte bufferView holds three.
        val json = triangleJson(
            accessors = """
                { "bufferView": 0, "componentType": 5126, "count": 16777216, "type": "VEC3" },
                { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
            """,
        )
        assertMalformed("accessors[0]") { scene(json) }
    }

    @Test
    fun aCountPastTheLimitIsRefused() {
        val json = triangleJson(
            accessors = """
                { "componentType": 5126, "count": 2147483647, "type": "VEC3" },
                { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
            """,
            primitives = """{ "attributes": { "POSITION": 0 } }""",
        )
        assertMalformed("accessors[0].count") { scene(json) }
    }

    @Test
    fun aNegativeCountIsRefused() {
        val json = triangleJson(
            accessors = """
                { "bufferView": 0, "componentType": 5126, "count": -3, "type": "VEC3" },
                { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
            """,
        )
        assertMalformed("accessors[0].count") { scene(json) }
    }

    @Test
    fun aNegativeAccessorOffsetIsRefused() {
        val json = triangleJson(
            accessors = """
                { "bufferView": 0, "byteOffset": -4, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
            """,
        )
        assertMalformed("accessors[0].byteOffset") { scene(json) }
    }

    @Test
    fun anOffsetThatWouldOverflowAnIntIsRefused() {
        val json = triangleJson(
            accessors = """
                { "bufferView": 0, "byteOffset": 2147483647, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
            """,
        )
        assertMalformed("accessors[0]") { scene(json) }
    }

    @Test
    fun anOutOfRangeAccessorIndexIsRefused() {
        val json = triangleJson(primitives = """{ "attributes": { "POSITION": 9 }, "indices": 1 }""")
        assertMalformed("meshes[0].primitives[0].attributes.POSITION") { scene(json) }
    }

    @Test
    fun anOutOfRangeBufferViewIndexIsRefused() {
        val json = triangleJson(
            accessors = """
                { "bufferView": 5, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
            """,
        )
        assertMalformed("accessors[0].bufferView") { scene(json) }
    }

    @Test
    fun anIndexPastTheVerticesIsRefused() {
        val bin = triangleBin.copyOf().also { GltfMalformedFixtures.putShort(it, 38, 7) }
        assertMalformed("accessors[1]") { scene(triangleJson(), bin) }
    }

    @Test
    fun aWrongAccessorTypeIsRefused() {
        val json = triangleJson(
            accessors = """
                { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC2" },
                { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
            """,
        )
        assertMalformed("accessors[0].type") { scene(json) }
    }

    @Test
    fun anUnknownComponentTypeIsRefused() {
        val json = triangleJson(
            accessors = """
                { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 1, "componentType": 9999, "count": 3, "type": "SCALAR" }
            """,
        )
        assertMalformed("accessors[1].componentType") { scene(json) }
    }

    @Test
    fun anAttributeWithAnotherVertexCountIsRefused() {
        val json = triangleJson(
            accessors = """
                { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" },
                { "bufferView": 0, "componentType": 5126, "count": 2, "type": "VEC3" }
            """,
            primitives = """{ "attributes": { "POSITION": 0, "NORMAL": 2 }, "indices": 1 }""",
        )
        assertMalformed("meshes[0].primitives[0].attributes.NORMAL") { scene(json) }
    }

    // --- sparse accessors ---

    @Test
    fun aSparseAccessorSubstitutesItsValues() {
        // No bufferView, so all three positions start at zero; one substitution sets one of them.
        val json = triangleJson(
            accessors = """
                { "componentType": 5126, "count": 3, "type": "VEC3",
                  "sparse": { "count": 1,
                    "indices": { "bufferView": 1, "byteOffset": 2, "componentType": 5123 },
                    "values": { "bufferView": 0, "byteOffset": 12 } } },
                { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
            """,
        )
        val primitive = GltfParser.parseScene(glb(json)).meshes.single().primitives.single()
        val stride = primitive.vertices.size / 3
        val positions = (0 until 3).flatMap { vertex -> (0 until 3).map { primitive.vertices[vertex * stride + it] } }
        // Index bytes [2, 4) hold 1, so vertex 1 takes (1, 0, 0); the others stay zero.
        assertContentEquals(listOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f), positions)
    }

    @Test
    fun aSparseCountPastTheAccessorIsRefused() {
        val json = triangleJson(
            accessors = """
                { "componentType": 5126, "count": 3, "type": "VEC3",
                  "sparse": { "count": 4,
                    "indices": { "bufferView": 1, "componentType": 5123 },
                    "values": { "bufferView": 0 } } },
                { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
            """,
        )
        assertMalformed("accessors[0].sparse.count") { scene(json) }
    }

    @Test
    fun sparseIndicesOutOfOrderAreRefused() {
        val bin = triangleBin.copyOf().also {
            GltfMalformedFixtures.putShort(it, 36, 2)
            GltfMalformedFixtures.putShort(it, 38, 1)
        }
        val json = triangleJson(
            accessors = """
                { "componentType": 5126, "count": 3, "type": "VEC3",
                  "sparse": { "count": 2,
                    "indices": { "bufferView": 1, "componentType": 5123 },
                    "values": { "bufferView": 0 } } },
                { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
            """,
            primitives = """{ "attributes": { "POSITION": 0 } }""",
        )
        assertMalformed("accessors[0].sparse.indices") { scene(json, bin) }
    }

    @Test
    fun sparseValuesPastTheirViewAreRefused() {
        val json = triangleJson(
            accessors = """
                { "componentType": 5126, "count": 3, "type": "VEC3",
                  "sparse": { "count": 2,
                    "indices": { "bufferView": 1, "componentType": 5123 },
                    "values": { "bufferView": 0, "byteOffset": 24 } } },
                { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
            """,
        )
        assertMalformed("accessors[0].sparse.values") { scene(json) }
    }

    // --- nodes and scenes ---

    @Test
    fun cyclicNodesAreRefused() {
        val json = triangleJson(nodes = """{ "mesh": 0, "children": [1] }, { "children": [0] }""", scenes = """{ "nodes": [] }""")
        assertMalformed("nodes[", prefix = true) { scene(json) }
    }

    @Test
    fun aNodeThatIsItsOwnChildIsRefused() {
        val json = triangleJson(nodes = """{ "mesh": 0, "children": [0] }""", scenes = """{ "nodes": [] }""")
        assertMalformed("nodes[0].children") { scene(json) }
    }

    @Test
    fun aNodeWithTwoParentsIsRefused() {
        // A diamond: walked naively, a chain of these doubles the work at every level.
        val json = triangleJson(nodes = """{ "children": [1, 1] }, { "mesh": 0 }""")
        assertMalformed("nodes[0].children[1]") { scene(json) }
    }

    @Test
    fun anOutOfRangeChildIsRefused() {
        assertMalformed("nodes[0].children[0]") { scene(triangleJson(nodes = """{ "mesh": 0, "children": [42] }""")) }
    }

    @Test
    fun aHierarchyDeeperThanTheLimitIsRefused() {
        val depth = 100_000
        val nodes = (0 until depth).joinToString { if (it < depth - 1) """{ "children": [${it + 1}] }""" else """{ "mesh": 0 }""" }
        assertMalformed("nodes[", prefix = true) { scene(triangleJson(nodes = nodes)) }
    }

    @Test
    fun aHierarchyWithinACustomLimitParses() {
        val nodes = (0 until 10).joinToString { if (it < 9) """{ "children": [${it + 1}] }""" else """{ "mesh": 0 }""" }
        assertEquals(1, GltfParser.parseScene(glb(triangleJson(nodes = nodes)), limits = GltfLimits(maxNodeDepth = 10)).meshes.size)
        assertMalformed("nodes[", prefix = true) { scene(triangleJson(nodes = nodes), limits = GltfLimits(maxNodeDepth = 9)) }
    }

    @Test
    fun aRootThatIsAlsoAChildIsRefused() {
        val json = triangleJson(nodes = """{ "children": [1] }, { "mesh": 0 }""", scenes = """{ "nodes": [0, 1] }""")
        assertMalformed("scenes[0].nodes[1]") { scene(json) }
    }

    @Test
    fun anOutOfRangeSceneIsRefused() {
        assertMalformed("scene") { scene(triangleJson(extra = """, "scene": 3""")) }
    }

    @Test
    fun aMatrixOfTheWrongSizeIsRefused() {
        assertMalformed("nodes[0].matrix") { scene(triangleJson(nodes = """{ "mesh": 0, "matrix": [1, 0, 0] }""")) }
    }

    @Test
    fun anOutOfRangeMeshIsRefused() {
        assertMalformed("nodes[0].mesh") { scene(triangleJson(nodes = """{ "mesh": 3 }""")) }
    }

    // --- skins and animations ---

    @Test
    fun tooFewInverseBindMatricesAreRefused() {
        assertMalformed("skins[0].inverseBindMatrices") {
            GltfParser.parseSkinned(skinnedJson(skins = """{ "joints": [0, 1], "inverseBindMatrices": 3 }"""))
        }
    }

    @Test
    fun anOutOfRangeJointIsRefused() {
        assertMalformed("skins[0].joints[0]") {
            GltfParser.parseSkinned(skinnedJson(skins = """{ "joints": [9], "inverseBindMatrices": 3 }"""))
        }
    }

    @Test
    fun aVertexJointPastTheSkinIsRefused() {
        // Read the weights' bytes as joints: 1f is 00 00 80 3F, so the first vertex uses joint 128 of one.
        val json = skinnedJson().replace(
            """{ "bufferView": 1, "componentType": 5121, "count": 3, "type": "VEC4" }""",
            """{ "bufferView": 2, "componentType": 5121, "count": 3, "type": "VEC4" }""",
        )
        assertMalformed("nodes[1]") { GltfParser.parseSkinned(json) }
    }

    @Test
    fun anAnimationOutputOfTheWrongLengthIsRefused() {
        val json = skinnedJson(samplers = """{ "input": 4, "output": 0 }""")
        assertMalformed("animations[0].samplers[0].output") { GltfParser.parseSkinned(json) }
    }

    @Test
    fun anOutOfRangeSamplerIsRefused() {
        val json = skinnedJson().replace("\"sampler\": 0", "\"sampler\": 5")
        assertMalformed("animations[0].channels[0].sampler") { GltfParser.parseSkinned(json) }
    }

    @Test
    fun badBase64InAJsonDocumentIsRefused() {
        assertMalformed("buffers[0].uri") { GltfParser.parseSkinned(skinnedJson(uri = "data:application/octet-stream;base64,!!!!")) }
    }

    // --- images ---

    @Test
    fun anOutOfRangeImageBufferViewIsRefused() {
        val json = triangleJson(
            primitives = """{ "attributes": { "POSITION": 0 }, "indices": 1, "material": 0 }""",
            extra = """,
              "materials": [ { "pbrMetallicRoughness": { "baseColorTexture": { "index": 0 } } } ],
              "textures": [ { "source": 0 } ],
              "images": [ { "bufferView": 12, "mimeType": "image/png" } ]
            """,
        )
        assertMalformed("images[0].bufferView") { scene(json) }
    }

    @Test
    fun badBase64InAnImageIsRefused() {
        val json = triangleJson(
            primitives = """{ "attributes": { "POSITION": 0 }, "indices": 1, "material": 0 }""",
            extra = """,
              "materials": [ { "pbrMetallicRoughness": { "baseColorTexture": { "index": 0 } } } ],
              "textures": [ { "source": 0 } ],
              "images": [ { "uri": "data:image/png;base64,%%%%" } ]
            """,
        )
        assertMalformed("images[0].uri") { scene(json) }
    }

    // --- limits ---

    @Test
    fun oneAccessorReferencedManyTimesStopsAtTheDecodeLimit() {
        // Every node reads the same mesh again; the budget bounds the total, not any one accessor.
        val nodes = (0 until 1000).joinToString { """{ "mesh": 0 }""" }
        val roots = (0 until 1000).joinToString()
        val json = triangleJson(nodes = nodes, scenes = """{ "nodes": [$roots] }""")
        assertEquals(1000, GltfParser.parseScene(glb(json)).meshes.size)
        assertMalformed("accessors[", prefix = true) { scene(json, limits = GltfLimits(maxDecodedBytes = 4096)) }
    }
}
