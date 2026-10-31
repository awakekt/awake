/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

/** Writes a [TestMesh] as a one-node GLB, skinned to two joint nodes when it has joints. */
object TestGlb {
    fun write(mesh: TestMesh, nodeName: String = "Model", extraNodes: String = ""): ByteArray {
        val bin = ArrayList<Byte>()
        val views = ArrayList<String>()
        fun view(bytes: List<Byte>): Int {
            while (bin.size % 4 != 0) bin += 0
            views += """{"buffer":0,"byteOffset":${bin.size},"byteLength":${bytes.size}}"""
            bin += bytes
            return views.lastIndex
        }
        val accessors = ArrayList<String>()
        fun floats(values: FloatArray, type: String, extra: String = ""): Int {
            val v = view(values.flatMap { le(it.toRawBits(), 4) })
            val components = mapOf("VEC2" to 2, "VEC3" to 3, "VEC4" to 4, "MAT4" to 16).getValue(type)
            accessors += """{"bufferView":$v,"componentType":5126,"count":${values.size / components},"type":"$type"$extra}"""
            return accessors.lastIndex
        }
        val min = (0 until 3).map { axis -> (0 until mesh.vertexCount).minOf { mesh.positions[it * 3 + axis] } }
        val max = (0 until 3).map { axis -> (0 until mesh.vertexCount).maxOf { mesh.positions[it * 3 + axis] } }
        val position = floats(mesh.positions, "VEC3", ""","min":$min,"max":$max""")
        val normal = floats(mesh.normals, "VEC3")
        val uv = floats(mesh.uvs, "VEC2")
        val indexView = view(mesh.indices.flatMap { le(it, 4) })
        accessors += """{"bufferView":$indexView,"componentType":5125,"count":${mesh.indices.size},"type":"SCALAR"}"""
        val indices = accessors.lastIndex
        var skinAttributes = ""
        var skin = ""
        var skinNodes = ""
        if (mesh.joints != null) {
            val jointView = view(mesh.joints.flatMap { le(it, 2) })
            accessors += """{"bufferView":$jointView,"componentType":5123,"count":${mesh.vertexCount},"type":"VEC4"}"""
            val joints = accessors.lastIndex
            val weights = floats(mesh.weights!!, "VEC4")
            val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            val bind = floats(identity + identity, "MAT4")
            skinAttributes = ""","JOINTS_0":$joints,"WEIGHTS_0":$weights"""
            skin = ""","skins":[{"joints":[1,2],"inverseBindMatrices":$bind}]"""
            skinNodes = """,{"name":"Body","children":[2]},{"name":"Head","translation":[0,2,0]}"""
        }
        val nodeSkin = if (mesh.joints != null) ""","skin":0""" else ""
        val roots = if (mesh.joints != null) "[0,1]" else "[0]"
        val json = """{"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":$roots}],""" +
            """"nodes":[{"name":"$nodeName","mesh":0$nodeSkin}$skinNodes$extraNodes],""" +
            """"meshes":[{"name":"$nodeName","primitives":[{"attributes":{"POSITION":$position,"NORMAL":$normal,""" +
            """"TEXCOORD_0":$uv$skinAttributes},"indices":$indices}]}]$skin,""" +
            """"buffers":[{"byteLength":${bin.size}}],"bufferViews":[${views.joinToString(",")}],""" +
            """"accessors":[${accessors.joinToString(",")}]}"""
        return GlbFile(json, bin.toByteArray()).encode()
    }

    private fun le(value: Int, bytes: Int): List<Byte> = (0 until bytes).map { (value ushr (it * 8)).toByte() }
}
