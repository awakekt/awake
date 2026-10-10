/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Every primitive of a skinned model is a part, and so is every mesh node with no skin. The fixture
 * is a root joint with a hand joint above it and quads skinned wholly to the hand, each quad told
 * apart by where it starts along x.
 */
class GltfAssetResolverSkinnedPartsTest {
    /** The reproduction from the issue: two quads as one skinned node with two primitives draw both. */
    @Test
    fun everyPrimitiveOfASkinnedMeshIsAPart() = runTest {
        val resolver = GltfAssetResolver()

        resolver.preload(PATH, model(Layout.OneNodeTwoPrimitives).encodeToByteArray())

        val slots = resolver.materialSlots(PATH)
        assertEquals(listOf("gltf-primitive:$PATH#0", "gltf-primitive:$PATH#1"), slots.map { it.mesh })
        assertEquals(listOf(0f, 4f), slots.map { firstX(assertNotNull(resolver.partGeometry(it.mesh))) })
        assertEquals(listOf(RED, BLUE), slots.map { it.parameters?.baseColorFactor }, "each primitive keeps its own material")
        assertEquals(BLUE, resolver.materialDefaults("gltf-primitive:$PATH#1", "skinned-material")?.baseColorFactor)
    }

    /** The positive control: the same quads as two single-primitive skinned nodes draw as before. */
    @Test
    fun singlePrimitiveSkinnedNodesAreStillOnePartEach() = runTest {
        val resolver = GltfAssetResolver()

        resolver.preload(PATH, model(Layout.TwoNodes).encodeToByteArray())

        val slots = resolver.materialSlots(PATH)
        assertEquals(listOf("gltf-primitive:$PATH#0" to "skinned-material", "gltf-primitive:$PATH#1" to "skinned-material"), slots.map { it.mesh to it.material })
        assertEquals(listOf(0f, 4f), slots.map { firstX(assertNotNull(resolver.partGeometry(it.mesh))) })
        assertEquals(VertexFormat.PositionNormalColorSkin, resolver.partGeometry(slots[0].mesh)?.format)
    }

    /** A saved scene's `#1` named the second skinned node; it still does, and the extra primitive comes after. */
    @Test
    fun aPartNumberASavedSceneHoldsKeepsItsMeaning() = runTest {
        val resolver = GltfAssetResolver()

        resolver.preload(PATH, model(Layout.TwoNodesFirstWithTwoPrimitives).encodeToByteArray())

        val starts = resolver.materialSlots(PATH).map { firstX(assertNotNull(resolver.partGeometry(it.mesh))) }
        assertEquals(listOf(0f, 8f, 4f), starts, "first node's first primitive, second node's, then the first node's second")
    }

    /** A prop parented to the hand is drawn with the palette, wholly on the hand, so it follows it. */
    @Test
    fun aMeshNodeWithoutASkinUnderAJointIsBoundToIt() = runTest {
        val resolver = GltfAssetResolver()

        resolver.preload(PATH, model(Layout.TwoNodes, rigid = true).encodeToByteArray())

        val slot = resolver.materialSlots(PATH)[2]
        assertEquals("gltf-primitive:$PATH#2" to "skinned-material", slot.mesh to slot.material)
        val geometry = assertNotNull(resolver.partGeometry(slot.mesh))
        assertEquals(VertexFormat.PositionNormalColorSkin, geometry.format)
        val stride = geometry.format.strideFloats
        val joints = geometry.format.floatOffsetOf(VertexSemantic.JointIndices)
        val weights = geometry.format.floatOffsetOf(VertexSemantic.JointWeights)
        for (vertex in 0 until geometry.vertices.size / stride) {
            assertEquals(1, geometry.vertices[vertex * stride + joints].toRawBits(), "vertex $vertex on skin joint 1, the hand")
            assertEquals(1f, geometry.vertices[vertex * stride + weights])
        }
        // At rest the hand's palette entry is identity, so the bound vertices are where the prop sits: (0.5, 1, 0).
        assertEquals(listOf(0.5f, 1f, 0f), geometry.vertices.copyOfRange(0, 3).toList())
    }

    /** A crate at the scene root follows no joint: it is static geometry at its own transform, lit as a static model is. */
    @Test
    fun aMeshNodeWithoutASkinUnderNoJointIsDrawnInPlace() = runTest {
        val resolver = GltfAssetResolver()

        resolver.preload(PATH, model(Layout.TwoNodes, rigid = true).encodeToByteArray())

        val slot = resolver.materialSlots(PATH)[3]
        assertEquals("gltf-primitive:$PATH#3" to "lit-shadow", slot.mesh to slot.material)
        val geometry = assertNotNull(resolver.partGeometry(slot.mesh))
        assertEquals(VertexFormat.PositionNormalColor, geometry.format)
        assertEquals(3f, firstX(geometry), "moved by its node, 3 m along x")
        assertNull(resolver.materialDefaults(slot.mesh, slot.material), "no skinned factors for a static part")
        assertEquals(4, resolver.materialSlots(PATH).size, "a mesh node in no scene is not drawn")
    }

    private fun firstX(geometry: MeshGeometry): Float = geometry.vertices[geometry.format.floatOffsetOf(VertexSemantic.Position)]

    private enum class Layout { OneNodeTwoPrimitives, TwoNodes, TwoNodesFirstWithTwoPrimitives }

    /**
     * Quads skinned to the hand, starting 0, 4 and 8 m along x, the first red and the others blue.
     * [layout] says how they are split into skinned nodes. With [rigid], an unskinned quad twice more:
     * a prop under the hand half a metre along x, and a crate at the scene root 3 m along x; a third
     * copy is in no scene.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun model(layout: Layout, rigid: Boolean = false): String {
        val buffer = Buffer()
        val quad = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)
        val starts = listOf(0f, 4f, 8f).map { x -> buffer.floats("VEC3", *FloatArray(quad.size) { if (it % 3 == 0) quad[it] + x else quad[it] }) }
        val joints = buffer.unsignedBytes(*IntArray(16) { if (it % 4 == 0) 1 else 0 })
        val weights = buffer.floats("VEC4", *FloatArray(16) { if (it % 4 == 0) 1f else 0f })
        val indices = buffer.unsignedShorts(0, 1, 2, 0, 2, 3)
        val inverseBinds = buffer.floats("MAT4", *translation(0f, 0f, 0f), *translation(0f, -1f, 0f))
        fun primitive(start: Int) =
            """{"attributes":{"POSITION":${starts[start]},"JOINTS_0":$joints,"WEIGHTS_0":$weights},"indices":$indices,"material":${if (start == 0) 0 else 1}}"""
        val skinnedMeshes = when (layout) {
            Layout.OneNodeTwoPrimitives -> listOf(listOf(0, 1))
            Layout.TwoNodes -> listOf(listOf(0), listOf(1))
            Layout.TwoNodesFirstWithTwoPrimitives -> listOf(listOf(0, 1), listOf(2))
        }
        val meshes = skinnedMeshes.map { prims -> """{"primitives":[${prims.joinToString(",") { primitive(it) }}]}""" } +
            """{"primitives":[{"attributes":{"POSITION":${starts[0]}},"indices":$indices}]}"""
        val rigidMesh = meshes.size - 1
        val skinnedNodes = skinnedMeshes.indices.map { """{"name":"Part $it","mesh":$it,"skin":0}""" }
        val firstRigid = 2 + skinnedNodes.size
        val rigidNodes = if (rigid) {
            listOf(
                """{"name":"Prop","mesh":$rigidMesh,"translation":[0.5,0,0]}""",
                """{"name":"Crate","mesh":$rigidMesh,"translation":[3,0,0]}""",
                """{"name":"Stray","mesh":$rigidMesh}""",
            )
        } else {
            emptyList()
        }
        val handChildren = if (rigid) ""","children":[$firstRigid]""" else ""
        val roots = listOf(0) + skinnedNodes.indices.map { 2 + it } + if (rigid) listOf(firstRigid + 1) else emptyList()
        return buffer.json(
            """
            "materials": [
              {"pbrMetallicRoughness":{"baseColorFactor":[0.9,0.1,0.1,1.0]}},
              {"pbrMetallicRoughness":{"baseColorFactor":[0.1,0.2,0.9,1.0]}}
            ],
            "meshes": [${meshes.joinToString(",")}],
            "nodes": [
              {"name":"Root","children":[1]},
              {"name":"Hand","translation":[0,1,0]$handChildren},
              ${(skinnedNodes + rigidNodes).joinToString(",")}
            ],
            "skins": [{"inverseBindMatrices":$inverseBinds,"joints":[0,1]}],
            "scenes": [{"nodes":[${roots.joinToString(",")}]}],
            "scene": 0
            """,
        )
    }

    private fun translation(x: Float, y: Float, z: Float) = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, x, y, z, 1f)

    /** One embedded buffer built up accessor by accessor; [json] wraps a document body around it. */
    @OptIn(ExperimentalEncodingApi::class)
    private class Buffer {
        private var bytes = ByteArray(0)
        private val views = mutableListOf<String>()
        private val accessors = mutableListOf<String>()

        fun floats(type: String, vararg values: Float): Int = add(type, 5126, values.size, 4) { out ->
            values.forEachIndexed { index, value ->
                val bits = value.toRawBits()
                for (byte in 0 until 4) out[index * 4 + byte] = (bits ushr (byte * 8)).toByte()
            }
        }

        fun unsignedBytes(vararg values: Int): Int =
            add("VEC4", 5121, values.size, 1) { out -> values.forEachIndexed { index, value -> out[index] = value.toByte() } }

        fun unsignedShorts(vararg values: Int): Int = add("SCALAR", 5123, values.size, 2) { out ->
            values.forEachIndexed { index, value ->
                out[index * 2] = value.toByte()
                out[index * 2 + 1] = (value ushr 8).toByte()
            }
        }

        fun json(body: String): String = """
            {
              "asset": {"version": "2.0"},
              "buffers": [{"uri":"data:application/octet-stream;base64,${Base64.encode(bytes)}","byteLength":${bytes.size}}],
              "bufferViews": [${views.joinToString(",")}],
              "accessors": [${accessors.joinToString(",")}],
              ${body.trimIndent()}
            }
        """.trimIndent()

        private fun add(type: String, componentType: Int, count: Int, componentSize: Int, write: (ByteArray) -> Unit): Int {
            val size = count * componentSize
            val offset = bytes.size
            bytes += ByteArray(size).also(write) + ByteArray((4 - size % 4) % 4)
            views += """{"buffer":0,"byteOffset":$offset,"byteLength":$size}"""
            val components = mapOf("SCALAR" to 1, "VEC3" to 3, "VEC4" to 4, "MAT4" to 16).getValue(type)
            accessors += """{"bufferView":${views.size - 1},"componentType":$componentType,"count":${count / components},"type":"$type"}"""
            return accessors.size - 1
        }
    }

    private companion object {
        const val PATH = "assets/characters/harbor-guard.gltf"
        val RED = Color(0.9f, 0.1f, 0.1f, 1f)
        val BLUE = Color(0.1f, 0.2f, 0.9f, 1f)
    }
}
