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
            doubleSided = doubleSided,
            normals = normals,
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
    doubleSided = doubleSided,
    normals = normals,
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
        val authored = primitive.normals?.takeIf { it.size == count * 3 }
        val normals = authored?.let { primitive.worldNormals(it) } ?: faceNormalSums(positions, primitive.indices)
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

/**
 * Authored glTF vertex normals moved by the inverse-transpose of [localTransform]'s upper-left 3x3
 * (the cofactor matrix divided by its determinant), preserving smooth shading across unshared
 * vertices and handling non-uniform scale or mirrored node transforms accurately.
 */
private fun LoadedPrimitive.worldNormals(authored: FloatArray): FloatArray {
    val m = localTransform.data
    val a00 = m[0]
    val a10 = m[1]
    val a20 = m[2]
    val a01 = m[4]
    val a11 = m[5]
    val a21 = m[6]
    val a02 = m[8]
    val a12 = m[9]
    val a22 = m[10]

    val c00 = a11 * a22 - a12 * a21
    val c10 = a02 * a21 - a01 * a22
    val c20 = a01 * a12 - a02 * a11
    val c01 = a12 * a20 - a10 * a22
    val c11 = a00 * a22 - a02 * a20
    val c21 = a02 * a10 - a00 * a12
    val c02 = a10 * a21 - a11 * a20
    val c12 = a01 * a20 - a00 * a21
    val c22 = a00 * a11 - a01 * a10
    val det = a00 * c00 + a10 * c10 + a20 * c20

    val useCofactor = kotlin.math.abs(det) > SINGULAR_DETERMINANT_EPSILON
    val invDet = if (useCofactor) 1f / det else 1f
    val n00 = if (useCofactor) c00 * invDet else a00
    val n10 = if (useCofactor) c10 * invDet else a10
    val n20 = if (useCofactor) c20 * invDet else a20
    val n01 = if (useCofactor) c01 * invDet else a01
    val n11 = if (useCofactor) c11 * invDet else a11
    val n21 = if (useCofactor) c21 * invDet else a21
    val n02 = if (useCofactor) c02 * invDet else a02
    val n12 = if (useCofactor) c12 * invDet else a12
    val n22 = if (useCofactor) c22 * invDet else a22

    val out = FloatArray(authored.size)
    var i = 0
    while (i < authored.size) {
        val nx = authored[i]
        val ny = authored[i + 1]
        val nz = authored[i + 2]
        out[i] = n00 * nx + n01 * ny + n02 * nz
        out[i + 1] = n10 * nx + n11 * ny + n12 * nz
        out[i + 2] = n20 * nx + n21 * ny + n22 * nz
        i += 3
    }
    return out
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
private const val SINGULAR_DETERMINANT_EPSILON = 1e-8f
private const val JSON_PADDING: Byte = 0x20
