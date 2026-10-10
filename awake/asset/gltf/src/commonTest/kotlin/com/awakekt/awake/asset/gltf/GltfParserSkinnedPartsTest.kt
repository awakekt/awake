/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

import com.awakekt.awake.core.animation.AnimationPose
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.math.transformPosition
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [GltfParser.parseSkinned] reads every primitive of a skinned mesh and every rigid mesh node, and
 * [bindRigidNode] makes a rigid node follow its joint. The fixture: a root joint at z = 2 with a hand
 * joint 1 above it, and unit quads skinned wholly to the hand.
 */
class GltfParserSkinnedPartsTest {
    @Test
    fun everyPrimitiveOfASkinnedMeshIsRead() {
        val scene = GltfParser.parseSkinned(skinnedQuads(onePrimitivePerNode = false))

        val body = scene.primitives[scene.skinnedNodes.single().meshIndex]
        assertEquals(2, body.size, "both primitives of the one skinned mesh")
        assertContentEquals(scene.meshes[0].positions, body[0].positions, "meshes keeps the first")
        assertEquals(4f, body[1].positions[0], "the second primitive is the second quad, at x = 4")
        assertContentEquals(RED, body[0].baseColorFactor)
        assertContentEquals(BLUE, body[1].baseColorFactor, "each primitive keeps its own material")
    }

    /** The positive control: the same two quads as two single-primitive skinned nodes read as before. */
    @Test
    fun singlePrimitiveSkinnedNodesReadAsBefore() {
        val scene = GltfParser.parseSkinned(skinnedQuads(onePrimitivePerNode = true))

        assertEquals(2, scene.skinnedNodes.size)
        assertEquals(listOf(1, 1, 1), scene.primitives.map { it.size })
        assertEquals(scene.meshes, scene.primitives.map { it.single() })
        assertEquals(emptyList(), scene.rigidNodes)
    }

    @Test
    fun meshNodesWithoutASkinInTheSceneAreRigid() {
        val scene = GltfParser.parseSkinned(skinnedQuads(onePrimitivePerNode = true, rigid = true))

        assertEquals(listOf(RigidNodeRef(boneIndex = 4, meshIndex = 2), RigidNodeRef(boneIndex = 5, meshIndex = 2)), scene.rigidNodes)
    }

    /** A prop parented to the hand, posed: drawn through the palette, it lands where the hand carries it, within 1e-5. */
    @Test
    fun aRigidNodeBoundToItsJointFollowsIt() {
        val scene = GltfParser.parseSkinned(skinnedQuads(onePrimitivePerNode = true, rigid = true))
        val skin = scene.skins.single()
        val prop = scene.rigidNodes.first()
        val bound = assertNotNull(scene.bindRigidNode(prop, skin)).single()
        val original = scene.primitives[prop.meshIndex].single()

        assertEquals(List(4) { listOf(1, 0, 0, 0) }.flatten(), bound.jointIndices?.toList(), "every vertex on the hand, skin joint 1")
        assertEquals(List(4) { listOf(1f, 0f, 0f, 0f) }.flatten(), bound.jointWeights?.toList())

        val pose = AnimationPose(scene.skeleton)
        assertMaxError(below = 1e-5f, drawn(bound, pose.jointPalette(skin)), expected(original, scene.restTransform(prop.boneIndex)))

        val handTurn = Quat(0f, 0f, sin(QUARTER_TURN / 2f), cos(QUARTER_TURN / 2f))
        pose.setBoneTransform(0, Vec3f(1f, 0f, 2f), Quat(0f, 0f, 0f, 1f))
        pose.setBoneTransform(1, Vec3f(0f, 1f, 0f), handTurn)
        val handNow = Mat4.multiplyColumnMajor(
            Mat4.fromTrs(Vec3f(1f, 0f, 2f), Quat(0f, 0f, 0f, 1f), Vec3f(1f, 1f, 1f)),
            Mat4.fromTrs(Vec3f(0f, 1f, 0f), handTurn, Vec3f(1f, 1f, 1f)),
        )
        val propNow = Mat4.multiplyColumnMajor(handNow, scene.skeleton.bones[prop.boneIndex].localTransform())
        assertMaxError(below = 1e-5f, drawn(bound, pose.jointPalette(skin)), expected(original, propNow))
    }

    @Test
    fun aRigidNodesNormalsTurnWithIt() {
        val scene = GltfParser.parseSkinned(skinnedQuads(onePrimitivePerNode = true, rigid = true))

        val bound = assertNotNull(scene.bindRigidNode(scene.rigidNodes.first(), scene.skins.single())).single()

        // The prop is turned a quarter about x under the hand, so its +z normal faces -y.
        val normals = assertNotNull(bound.normals)
        for (vertex in 0 until 4) {
            assertMaxError(below = 1e-5f, normals.copyOfRange(vertex * 3, vertex * 3 + 3), floatArrayOf(0f, -1f, 0f))
        }
    }

    /** A crate at the scene root: no joint carries it, so it has no binding and sits at its own transform. */
    @Test
    fun aRigidNodeNoJointCarriesSitsAtItsRestTransform() {
        val scene = GltfParser.parseSkinned(skinnedQuads(onePrimitivePerNode = true, rigid = true))
        val crate = scene.rigidNodes.last()

        assertNull(scene.bindRigidNode(crate, scene.skins.single()))
        val rest = scene.restTransform(crate.boneIndex)
        assertEquals(Vec3f(3f, 0f, 0f), Vec3f(rest.m03, rest.m13, rest.m23))
    }

    @Test
    fun aJointsRestTransformComposesItsAncestors() {
        val scene = GltfParser.parseSkinned(skinnedQuads(onePrimitivePerNode = true))

        val hand = scene.restTransform(1)

        assertEquals(Vec3f(0f, 1f, 2f), Vec3f(hand.m03, hand.m13, hand.m23))
    }

    /** Each vertex of [mesh] skinned by [palette] on the CPU, as the skinned shader does, packed xyz. */
    private fun drawn(mesh: GltfMesh, palette: FloatArray): FloatArray {
        val joints = assertNotNull(mesh.jointIndices)
        val weights = assertNotNull(mesh.jointWeights)
        return FloatArray(mesh.positions.size).also { out ->
            for (vertex in 0 until mesh.vertexCount) {
                val position = Vec4(mesh.positions[vertex * 3], mesh.positions[vertex * 3 + 1], mesh.positions[vertex * 3 + 2], 1f)
                for (slot in 0 until 4) {
                    val weight = weights[vertex * 4 + slot]
                    if (weight == 0f) continue
                    val joint = Mat4().also { palette.copyInto(it.data, 0, joints[vertex * 4 + slot] * 16, joints[vertex * 4 + slot] * 16 + 16) }
                    val moved = joint.transformPosition(position)
                    out[vertex * 3] += moved.x * weight
                    out[vertex * 3 + 1] += moved.y * weight
                    out[vertex * 3 + 2] += moved.z * weight
                }
            }
        }
    }

    /** Each vertex of [mesh] placed by [transform], packed xyz. */
    private fun expected(mesh: GltfMesh, transform: Mat4): FloatArray = FloatArray(mesh.positions.size).also { out ->
        for (vertex in 0 until mesh.vertexCount) {
            val moved = transform.transformPosition(Vec4(mesh.positions[vertex * 3], mesh.positions[vertex * 3 + 1], mesh.positions[vertex * 3 + 2], 1f))
            out[vertex * 3] = moved.x
            out[vertex * 3 + 1] = moved.y
            out[vertex * 3 + 2] = moved.z
        }
    }

    private fun assertMaxError(below: Float, actual: FloatArray, expected: FloatArray) {
        assertEquals(expected.size, actual.size)
        val error = actual.indices.maxOf { abs(actual[it] - expected[it]) }
        assertTrue(error < below, "max error $error, expected below $below: ${actual.toList()} vs ${expected.toList()}")
    }

    /**
     * Two unit quads skinned to the hand, the second 4 m along x and blue where the first is red: as
     * one skinned node with two primitives, or with [onePrimitivePerNode] as two skinned nodes. With
     * [rigid], an unskinned quad twice more: node 4, a prop under the hand, half a metre along x and
     * turned a quarter about x, and node 5, a crate at the scene root 3 m along x. Node 6 holds the
     * quad too but is in no scene.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun skinnedQuads(onePrimitivePerNode: Boolean, rigid: Boolean = false): String {
        val fixture = GltfFixture()
        val quad = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)
        val first = fixture.floats("VEC3", *quad)
        val second = fixture.floats("VEC3", *FloatArray(quad.size) { if (it % 3 == 0) quad[it] + 4f else quad[it] })
        val normals = fixture.floats("VEC3", *FloatArray(12) { if (it % 3 == 2) 1f else 0f })
        val joints = fixture.unsignedBytes("VEC4", *IntArray(16) { if (it % 4 == 0) 1 else 0 })
        val weights = fixture.floats("VEC4", *FloatArray(16) { if (it % 4 == 0) 1f else 0f })
        val indices = fixture.unsignedShorts(0, 1, 2, 0, 2, 3)
        val inverseBinds = fixture.floats("MAT4", *translation(0f, 0f, -2f), *translation(0f, -1f, -2f))
        fun primitive(positions: Int, material: Int) =
            """{"attributes":{"POSITION":$positions,"NORMAL":$normals,"JOINTS_0":$joints,"WEIGHTS_0":$weights},"indices":$indices,"material":$material}"""
        val meshes = buildList {
            if (onePrimitivePerNode) {
                add("""{"primitives":[${primitive(first, 0)}]}""")
                add("""{"primitives":[${primitive(second, 1)}]}""")
            } else {
                add("""{"primitives":[${primitive(first, 0)},${primitive(second, 1)}]}""")
                add("""{"primitives":[${primitive(second, 1)}]}""")
            }
            add("""{"primitives":[{"attributes":{"POSITION":$first,"NORMAL":$normals},"indices":$indices}]}""")
        }
        val skinned = if (onePrimitivePerNode) {
            """{"name":"Body","mesh":0,"skin":0},{"name":"Cloak","mesh":1,"skin":0}"""
        } else {
            """{"name":"Body","mesh":0,"skin":0},{"name":"Unused"}"""
        }
        val quarterX = sin(QUARTER_TURN / 2f)
        val hand = if (rigid) """{"name":"Hand","translation":[0,1,0],"children":[4]}""" else """{"name":"Hand","translation":[0,1,0]}"""
        val rigidNodes = if (rigid) {
            """,{"name":"Prop","mesh":2,"translation":[0.5,0,0],"rotation":[$quarterX,0,0,${cos(QUARTER_TURN / 2f)}]},""" +
                """{"name":"Crate","mesh":2,"translation":[3,0,0]},{"name":"Stray","mesh":2}"""
        } else {
            ""
        }
        val roots = if (rigid) "0,2,3,5" else "0,2,3"
        return fixture.json(
            """
            "materials": [
              {"pbrMetallicRoughness":{"baseColorFactor":[${RED.joinToString(",")}]}},
              {"pbrMetallicRoughness":{"baseColorFactor":[${BLUE.joinToString(",")}]}}
            ],
            "meshes": [${meshes.joinToString(",")}],
            "nodes": [
              {"name":"Root","translation":[0,0,2],"children":[1]},
              $hand,
              $skinned$rigidNodes
            ],
            "skins": [{"inverseBindMatrices":$inverseBinds,"joints":[0,1]}],
            "scenes": [{"nodes":[$roots]}],
            "scene": 0
            """,
        )
    }

    private fun translation(x: Float, y: Float, z: Float) = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, x, y, z, 1f)

    private companion object {
        val RED = floatArrayOf(0.9f, 0.1f, 0.1f, 1f)
        val BLUE = floatArrayOf(0.1f, 0.2f, 0.9f, 1f)
        const val QUARTER_TURN = (PI / 2).toFloat()
    }
}

/** Builds a small embedded-buffer glTF in code: each call adds an accessor over one buffer, and [json] wraps a body around them. */
@OptIn(ExperimentalEncodingApi::class)
internal class GltfFixture {
    private var buffer = ByteArray(0)
    private val views = mutableListOf<String>()
    private val accessors = mutableListOf<String>()

    /** A `FLOAT` accessor of [type] elements over [values]; returns its index. */
    fun floats(type: String, vararg values: Float): Int = add(type, COMPONENT_FLOAT, values.size) { bytes ->
        values.forEachIndexed { index, value ->
            val bits = value.toRawBits()
            for (byte in 0 until 4) bytes[index * 4 + byte] = (bits ushr (byte * 8)).toByte()
        }
    }

    /** An `UNSIGNED_BYTE` accessor, as `JOINTS_0` is written; returns its index. */
    fun unsignedBytes(type: String, vararg values: Int): Int =
        add(type, COMPONENT_UNSIGNED_BYTE, values.size) { bytes -> values.forEachIndexed { index, value -> bytes[index] = value.toByte() } }

    /** An `UNSIGNED_SHORT` scalar accessor, as indices are written; returns its index. */
    fun unsignedShorts(vararg values: Int): Int = add("SCALAR", COMPONENT_UNSIGNED_SHORT, values.size) { bytes ->
        values.forEachIndexed { index, value ->
            bytes[index * 2] = value.toByte()
            bytes[index * 2 + 1] = (value ushr 8).toByte()
        }
    }

    /** The document: [body]'s members after the buffer, its views and accessors. */
    fun json(body: String): String = """
        {
          "asset": {"version": "2.0"},
          "buffers": [{"uri":"data:application/octet-stream;base64,${Base64.encode(buffer)}","byteLength":${buffer.size}}],
          "bufferViews": [${views.joinToString(",")}],
          "accessors": [${accessors.joinToString(",")}],
          ${body.trimIndent()}
        }
    """.trimIndent()

    private fun add(type: String, componentType: Int, componentCount: Int, write: (ByteArray) -> Unit): Int {
        val size = componentCount * componentSize(componentType)
        val offset = buffer.size
        val bytes = ByteArray(size).also(write)
        buffer += bytes + ByteArray((4 - size % 4) % 4)
        views += """{"buffer":0,"byteOffset":$offset,"byteLength":$size}"""
        accessors += """{"bufferView":${views.size - 1},"componentType":$componentType,"count":${componentCount / components(type)},"type":"$type"}"""
        return accessors.size - 1
    }

    private fun componentSize(componentType: Int) = when (componentType) {
        COMPONENT_UNSIGNED_BYTE -> 1
        COMPONENT_UNSIGNED_SHORT -> 2
        else -> 4
    }

    private fun components(type: String) = when (type) {
        "SCALAR" -> 1
        "VEC2" -> 2
        "VEC3" -> 3
        "VEC4" -> 4
        "MAT4" -> 16
        else -> error("Unsupported accessor type $type")
    }

    private companion object {
        const val COMPONENT_UNSIGNED_BYTE = 5121
        const val COMPONENT_UNSIGNED_SHORT = 5123
        const val COMPONENT_FLOAT = 5126
    }
}
