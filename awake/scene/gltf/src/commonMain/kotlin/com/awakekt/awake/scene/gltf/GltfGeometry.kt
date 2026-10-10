/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.asset.gltf.GltfMesh
import com.awakekt.awake.asset.gltf.LoadedPrimitive
import com.awakekt.awake.asset.gltf.LoadedScene
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.math.transformPosition

internal fun LoadedScene.toStaticGeometry(textured: Boolean = false): MeshGeometry =
    meshes.flatMap { it.primitives }.toStaticGeometry(textured)

internal fun GltfMesh.toStaticGeometry(): MeshGeometry =
    listOf(
        LoadedPrimitive(
            vertices = toInterleavedPositionColorUv(),
            indices = indices,
            localTransform = com.awakekt.awake.core.math.Mat4(),
            baseColorFactor = baseColorFactor,
        ),
    ).toStaticGeometry()

/** This primitive as [com.awakekt.awake.asset.gltf.GltfParser.parseScene] gives one, placed by [transform]. */
internal fun GltfMesh.toLoadedPrimitive(transform: Mat4): LoadedPrimitive = LoadedPrimitive(
    vertices = toInterleavedPositionColorUv(),
    indices = indices,
    localTransform = transform,
    baseColorImageBytes = baseColorImageBytes,
    metallicRoughnessImageBytes = metallicRoughnessImageBytes,
    normalImageBytes = normalImageBytes,
    occlusionImageBytes = occlusionImageBytes,
    emissiveImageBytes = emissiveImageBytes,
    baseColorFactor = baseColorFactor,
    metallicFactor = metallicFactor,
    roughnessFactor = roughnessFactor,
    emissiveFactor = emissiveFactor,
    alphaMode = alphaMode,
)

/** Converts parsed scene primitives into the vertex format the `lit-shadow` pipeline draws. */
internal fun List<LoadedPrimitive>.toStaticGeometry(textured: Boolean = false): MeshGeometry {
    require(isNotEmpty()) { "glTF scene contains no mesh primitives." }
    val vertices = mutableListOf<Float>()
    val indices = mutableListOf<Int>()
    for (primitive in this) {
        val stride = 8
        val count = primitive.vertices.size / stride
        val positions = primitive.worldPositions(stride)
        val normals = faceNormalSums(positions, primitive.indices)
        val vertexOffset = vertices.size / if (textured) 11 else 9
        for (index in 0 until count) {
            val source = index * stride
            val normal = index * 3
            val length = kotlin.math.sqrt(
                normals[normal] * normals[normal] +
                    normals[normal + 1] * normals[normal + 1] +
                    normals[normal + 2] * normals[normal + 2],
            ).takeIf { it > 0f } ?: 1f
            vertices += positions[normal]
            vertices += positions[normal + 1]
            vertices += positions[normal + 2]
            vertices += normals[normal] / length
            vertices += normals[normal + 1] / length
            vertices += normals[normal + 2] / length
            vertices += primitive.vertices[source + 3] * primitive.baseColorFactor.getOrElse(0) { 1f }
            vertices += primitive.vertices[source + 4] * primitive.baseColorFactor.getOrElse(1) { 1f }
            vertices += primitive.vertices[source + 5] * primitive.baseColorFactor.getOrElse(2) { 1f }
            if (textured) {
                vertices += primitive.vertices[source + 6]
                vertices += primitive.vertices[source + 7]
            }
        }
        indices += primitive.indices.map { it + vertexOffset }
    }
    return MeshGeometry(
        vertices = vertices.toFloatArray(),
        indices = indices.toIntArray(),
        format = if (textured) VertexFormat.PositionNormalColorUv else VertexFormat.PositionNormalColor,
    )
}

/** Vertex positions moved by the primitive's node transform, packed xyz. */
private fun LoadedPrimitive.worldPositions(stride: Int): FloatArray {
    val count = vertices.size / stride
    val positions = FloatArray(count * 3)
    for (index in 0 until count) {
        val source = index * stride
        val transformed = localTransform.transformPosition(
            Vec4(vertices[source], vertices[source + 1], vertices[source + 2], 1f),
        )
        val target = index * 3
        positions[target] = transformed.x
        positions[target + 1] = transformed.y
        positions[target + 2] = transformed.z
    }
    return positions
}

/** Per-vertex sums of the face normals around it, unnormalised, packed xyz. */
private fun faceNormalSums(positions: FloatArray, indices: IntArray): FloatArray {
    val normals = FloatArray(positions.size)
    for (triangle in 0 until indices.size / 3) {
        val a = indices[triangle * 3] * 3
        val b = indices[triangle * 3 + 1] * 3
        val c = indices[triangle * 3 + 2] * 3
        val abx = positions[b] - positions[a]
        val aby = positions[b + 1] - positions[a + 1]
        val abz = positions[b + 2] - positions[a + 2]
        val acx = positions[c] - positions[a]
        val acy = positions[c + 1] - positions[a + 1]
        val acz = positions[c + 2] - positions[a + 2]
        val nx = aby * acz - abz * acy
        val ny = abz * acx - abx * acz
        val nz = abx * acy - aby * acx
        for (vertex in intArrayOf(a, b, c)) {
            normals[vertex] += nx
            normals[vertex + 1] += ny
            normals[vertex + 2] += nz
        }
    }
    return normals
}

/** Wraps an embedded-buffer JSON glTF in the smallest valid GLB container. */
internal fun String.toEmbeddedGlb(): ByteArray {
    val jsonBytes = encodeToByteArray()
    val padding = (4 - (jsonBytes.size % 4)) % 4
    val paddedJson = jsonBytes + ByteArray(padding) { JSON_PADDING }
    val totalLength = GLB_HEADER_BYTES + GLB_CHUNK_HEADER_BYTES + paddedJson.size
    return ByteArray(totalLength).also { output ->
        writeLittleEndianInt(output, 0, GLB_MAGIC)
        writeLittleEndianInt(output, 4, GLB_VERSION)
        writeLittleEndianInt(output, 8, totalLength)
        writeLittleEndianInt(output, 12, paddedJson.size)
        writeLittleEndianInt(output, 16, GLB_JSON_CHUNK)
        paddedJson.copyInto(output, destinationOffset = GLB_HEADER_BYTES + GLB_CHUNK_HEADER_BYTES)
    }
}

private fun writeLittleEndianInt(target: ByteArray, offset: Int, value: Int) {
    target[offset] = value.toByte()
    target[offset + 1] = (value ushr 8).toByte()
    target[offset + 2] = (value ushr 16).toByte()
    target[offset + 3] = (value ushr 24).toByte()
}

private const val GLB_MAGIC = 0x46546C67
private const val GLB_VERSION = 2
private const val GLB_HEADER_BYTES = 12
private const val GLB_CHUNK_HEADER_BYTES = 8
private const val GLB_JSON_CHUNK = 0x4E4F534A
private const val JSON_PADDING: Byte = 0x20
