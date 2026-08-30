/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.gltf

import io.github.awakelab.awake.core.math.Mat4

/**
 * A single decoded glTF primitive, ready to hand to a backend's vertex/index buffer upload --
 * see [GltfParser.parseScene]. [vertices] is interleaved pos3+color3+uv2 (8 floats/vertex),
 * the same layout [GltfMesh.toInterleavedPositionColorUv] already produces. [baseColorImageBytes]
 * is this primitive's own material's base color texture, still encoded -- `null` unless this
 * primitive has a material with one -- same "per-primitive, not per-mesh" scoping [GltfMesh]
 * already uses, since sibling primitives of one mesh (e.g. skin vs. clothing) commonly use
 * different materials. [metallicRoughnessImageBytes]/[normalImageBytes]/[occlusionImageBytes]/
 * [emissiveImageBytes] are the same per-primitive scoping for the rest of [GltfMesh]'s PBR
 * texture channels, and [baseColorFactor]/[metallicFactor]/[roughnessFactor]/[emissiveFactor]
 * for its factor scalars/vectors (see [GltfMesh]'s own doc comment for their glTF spec
 * defaults).
 */
data class LoadedPrimitive(
    /** The interleaved vertex data. */
    val vertices: FloatArray,
    /** The index data. */
    val indices: IntArray,
    /** The local transform of the node carrying this primitive. */
    val localTransform: Mat4,
    /** Still-encoded base color texture bytes (PNG/JPEG), or null. */
    val baseColorImageBytes: ByteArray? = null,
    /** Still-encoded metallic-roughness texture bytes, or null. */
    val metallicRoughnessImageBytes: ByteArray? = null,
    /** Still-encoded normal texture bytes, or null. */
    val normalImageBytes: ByteArray? = null,
    /** Still-encoded occlusion texture bytes, or null. */
    val occlusionImageBytes: ByteArray? = null,
    /** Still-encoded emissive texture bytes, or null. */
    val emissiveImageBytes: ByteArray? = null,
    /** The base color factor (RGBA). */
    val baseColorFactor: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
    /** The metallic factor. */
    val metallicFactor: Float = 1f,
    /** The roughness factor. */
    val roughnessFactor: Float = 1f,
    /** The emissive factor (RGB). */
    val emissiveFactor: FloatArray = floatArrayOf(0f, 0f, 0f),
)

/**
 * A mesh loaded from a glTF scene, containing one or more primitives.
 */
data class LoadedMesh(
    /** The name of the mesh. */
    val name: String,
    /** The list of primitives in the mesh. */
    val primitives: List<LoadedPrimitive>,
)

/**
 * A full scene loaded from a glTF document.
 */
data class LoadedScene(
    /** The list of meshes in the scene. */
    val meshes: List<LoadedMesh>,
)
