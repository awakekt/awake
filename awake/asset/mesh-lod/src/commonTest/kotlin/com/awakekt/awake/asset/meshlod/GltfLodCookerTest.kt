/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

import com.awakekt.awake.asset.gltf.GltfParser
import com.awakekt.awake.asset.gltf.parseMeshPrimitives
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GltfLodCookerTest {
    private val sphere = TestGlb.write(TestMeshes.uvSphere(segments = 48, rings = 24))

    private fun json(glb: ByteArray): JsonObject = Json.parseToJsonElement(GlbFile.decode(glb).json).jsonObject

    @Test
    fun aCookedGlbRoundTripsWithMsftLodLevelsAndCoverage() {
        val cooked = GltfLodCooker.cook(sphere)
        assertEquals(LodCookOutcome.Generated, cooked.outcome)
        val document = json(cooked.bytes)
        assertTrue("MSFT_lod" in document["extensionsUsed"]!!.jsonArray.map { it.jsonPrimitive.content })

        val chain = GltfLods.read(document).single()
        assertEquals(0, chain.node)
        assertEquals(4, chain.levels.size, "the full mesh and three generated levels")
        assertEquals(listOf(0.25f, 0.12f, 0.05f, 0f), chain.screenCoverage)

        val meshes = GltfParser.parseMeshPrimitives(cooked.bytes)
        val nodes = document["nodes"]!!.jsonArray
        val triangles = chain.levels.map { node ->
            meshes[nodes[node].jsonObject["mesh"]!!.jsonPrimitive.int].single().indices.size / 3
        }
        assertEquals(cooked.meshes.single().triangles, triangles)
        listOf(0.5f, 0.25f, 0.1f).forEachIndexed { level, ratio ->
            val target = triangles[0] * ratio
            assertTrue(kotlin.math.abs(triangles[level + 1] - target) <= triangles[0] * 0.02f, "level ${level + 1}: ${triangles[level + 1]} vs $target")
        }
        // The full mesh still reads exactly as it did; the scene still has one model in it.
        val original = GltfParser.parseMeshPrimitives(sphere).single().single()
        assertContentEquals(original.indices, meshes[0].single().indices)
        assertEquals(1, GltfParser.parseScene(cooked.bytes).meshes.size)
    }

    @Test
    fun skinnedLevelsShareTheJointsAndWeightsOfTheFullMesh() {
        val tube = TestMeshes.skinnedTube(segments = 24, rings = 32)
        val cooked = GltfLodCooker.cook(TestGlb.write(tube))
        val document = json(cooked.bytes)
        val chain = GltfLods.read(document).single()
        val nodes = document["nodes"]!!.jsonArray.map { it.jsonObject }
        val meshes = document["meshes"]!!.jsonArray.map { it.jsonObject }
        fun attributes(node: Int) = meshes[nodes[node]["mesh"]!!.jsonPrimitive.int]["primitives"]!!.jsonArray.single()
            .jsonObject["attributes"]!!.jsonObject
        for (level in chain.levels.drop(1)) {
            assertEquals(0, nodes[level]["skin"]!!.jsonPrimitive.int, "LOD node $level keeps the skin")
            assertEquals(attributes(chain.node), attributes(level), "LOD node $level reads the full mesh's own vertex streams")
        }
        val decoded = GltfParser.parseMeshPrimitives(cooked.bytes)
        val coarsest = decoded[nodes[chain.levels.last()]["mesh"]!!.jsonPrimitive.int].single()
        for (vertex in coarsest.indices.toSet()) {
            assertContentEquals(tube.joints!!.copyOfRange(vertex * 4, vertex * 4 + 4), coarsest.jointIndices!!.copyOfRange(vertex * 4, vertex * 4 + 4))
            assertContentEquals(tube.weights!!.copyOfRange(vertex * 4, vertex * 4 + 4), coarsest.jointWeights!!.copyOfRange(vertex * 4, vertex * 4 + 4))
        }
    }

    @Test
    fun anAssetThatAlreadyCarriesLodsIsKeptAsAuthored() {
        val named = TestGlb.write(TestMeshes.uvSphere(16, 8), nodeName = "Rock_LOD0", extraNodes = """,{"name":"Rock_LOD1","mesh":0}""")
        val result = GltfLodCooker.cook(named)
        assertEquals(LodCookOutcome.AuthoredLods, result.outcome)
        assertSame(named, result.bytes)
        assertEquals(listOf(0, 1), GltfLods.read(json(named)).single().levels)

        val cookedOnce = GltfLodCooker.cook(sphere).bytes
        assertEquals(LodCookOutcome.AuthoredLods, GltfLodCooker.cook(cookedOnce).outcome)
    }

    @Test
    fun anAssetOptedOutInProjectSettingsIsUnchanged() {
        val settings = ProjectLodSettings.decode(
            """{"defaults":{"levels":[{"ratio":0.5,"screenCoverage":0.1}]},"assets":{"props/rock.glb":{"enabled":false}}}""",
        )
        assertEquals(LodCookOutcome.OptedOut, GltfLodCooker.cook(sphere, settings.forAsset("props/rock.glb")).outcome)
        val fence = GltfLodCooker.cook(sphere, settings.forAsset("./props/fence.glb"))
        assertEquals(2, fence.meshes.single().triangles.size, "the project default asks for one level")
        assertEquals(listOf(0.25f, 0.1f), GltfLods.read(json(fence.bytes)).single().screenCoverage)
        assertEquals(settings, ProjectLodSettings.decode(settings.encode()))
    }

    @Test
    fun aDotGltfDocumentGetsItsIndexDataInANewEmbeddedBuffer() {
        val glb = GlbFile.decode(sphere)
        val base64 = kotlin.io.encoding.Base64.encode(glb.bin!!)
        val text = glb.json.replace(""""buffers":[{"byteLength"""", """"buffers":[{"uri":"data:application/octet-stream;base64,$base64","byteLength"""")
        val cooked = GltfLodCooker.cook(text.encodeToByteArray())
        val document = Json.parseToJsonElement(cooked.bytes.decodeToString()).jsonObject
        assertEquals(2, document["buffers"]!!.jsonArray.size)
        assertEquals(4, GltfParser.parseMeshPrimitives(cooked.bytes).size)
        assertTrue(document["nodes"]!!.jsonArray[0].jsonObject["extras"]!!.jsonObject["MSFT_screencoverage"]!!.jsonArray[0].jsonPrimitive.float > 0f)
    }
}
