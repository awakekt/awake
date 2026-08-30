/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.gltf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The subset of the glTF 2.0 JSON schema (https://registry.khronos.org/glTF/specs/2.0/
 * glTF-2.0.html) this parser actually reads: enough to pull mesh vertex attributes/indices,
 * a node hierarchy, skeletal skinning/animation, and base color/metallic-roughness/normal/
 * occlusion/emissive textures out of a `.gltf` file -- see [GltfParser]'s doc comment for
 * exactly which parts of [materials]/[textures]/[images] are read: still-encoded image bytes
 * per channel, plus [GltfPbrMetallicRoughness]'s `baseColorFactor`/`metallicFactor`/
 * `roughnessFactor` and [GltfMaterial.emissiveFactor] -- the scalar/vector factors a
 * texture-free (or partially textured) material tints or replaces its maps with. No other
 * material fields (normal `scale`, occlusion `strength`) are read.
 */
@Serializable
data class GltfDocument(
    /** The list of buffers. */
    val buffers: List<GltfBuffer> = emptyList(),
    /** The list of buffer views. */
    val bufferViews: List<GltfBufferView> = emptyList(),
    /** The list of accessors. */
    val accessors: List<GltfAccessor> = emptyList(),
    /** The list of mesh definitions. */
    val meshes: List<GltfMeshDef> = emptyList(),
    /** Index into [scenes] of the scene to load when none is explicitly requested -- glTF
     * default is `0` when the document has any scenes at all. */
    val scene: Int? = null,
    /** The list of scenes. */
    val scenes: List<GltfScene> = emptyList(),
    /** The list of nodes. */
    val nodes: List<GltfNode> = emptyList(),
    /** The list of skins. */
    val skins: List<GltfSkin> = emptyList(),
    /** The list of animations. */
    val animations: List<GltfAnimation> = emptyList(),
    /** The list of materials. */
    val materials: List<GltfMaterial> = emptyList(),
    /** The list of textures. */
    val textures: List<GltfTexture> = emptyList(),
    /** The list of images. */
    val images: List<GltfImage> = emptyList(),
)

/**
 * A glTF material.
 */
@Serializable
data class GltfMaterial(
    /** The name of the material. */
    val name: String? = null,
    /** PBR metallic roughness properties. */
    val pbrMetallicRoughness: GltfPbrMetallicRoughness? = null,
    /** Normal texture reference. */
    val normalTexture: GltfTextureRef? = null,
    /** Occlusion texture reference. */
    val occlusionTexture: GltfTextureRef? = null,
    /** Emissive texture reference. */
    val emissiveTexture: GltfTextureRef? = null,
    /** Linear RGB `[r, g, b]`, multiplied into [emissiveTexture]'s sample (or standing alone
     * when there's no emissive texture) -- glTF 2.0 spec default `[0, 0, 0]` (no emission). */
    val emissiveFactor: List<Float>? = null,
)

/**
 * PBR metallic roughness material properties.
 */
@Serializable
data class GltfPbrMetallicRoughness(
    /** Base color texture reference. */
    val baseColorTexture: GltfTextureRef? = null,
    /** Linear RGBA `[r, g, b, a]`, multiplied into [baseColorTexture]'s sample (or standing
     * alone when there's no base color texture) -- glTF 2.0 spec default `[1, 1, 1, 1]`
     * (opaque white, i.e. the texture's own color passes through unmodified). */
    val baseColorFactor: List<Float>? = null,
    /** Green channel = roughness, blue channel = metalness (glTF 2.0 spec) -- read as one
     * still-encoded image. */
    val metallicRoughnessTexture: GltfTextureRef? = null,
    /** Multiplies [metallicRoughnessTexture]'s blue channel (or stands alone when there's no
     * texture) -- glTF 2.0 spec default `1.0`. */
    val metallicFactor: Float? = null,
    /** Multiplies [metallicRoughnessTexture]'s green channel (or stands alone when there's no
     * texture) -- glTF 2.0 spec default `1.0`. */
    val roughnessFactor: Float? = null,
)

/**
 * A reference to a texture.
 */
@Serializable
data class GltfTextureRef(
    /** The index of the texture. */
    val index: Int,
    /** The index of the texture's TEXCOORD attribute used for texture coordinate mapping. */
    @SerialName("texCoord") val texCoordSet: Int = 0,
)

/**
 * A glTF texture.
 */
@Serializable
data class GltfTexture(
    /** The index of the image source. */
    val source: Int? = null,
    /** The index of the sampler. */
    val sampler: Int? = null,
)

/**
 * A glTF image.
 */
@Serializable
data class GltfImage(
    /** A `data:image/png;base64,...` (or `image/jpeg`) data URI -- external image file
     * references are not supported. */
    val uri: String? = null,
    /** The image's MIME type. */
    val mimeType: String? = null,
)

/**
 * A glTF skin.
 */
@Serializable
data class GltfSkin(
    /** The index of the accessor containing the floating-point 4x4 inverse-bind matrices. */
    val inverseBindMatrices: Int? = null,
    /** Indices of skeleton nodes, used as joints in this skin. */
    val joints: List<Int> = emptyList(),
    /** The index of the node used as a skeleton root. */
    val skeleton: Int? = null,
)

/**
 * A glTF animation.
 */
@Serializable
data class GltfAnimation(
    /** The list of animation channels. */
    val channels: List<GltfAnimationChannel> = emptyList(),
    /** The list of animation samplers. */
    val samplers: List<GltfAnimationSampler> = emptyList(),
    /** The name of the animation. */
    val name: String? = null,
)

/**
 * A glTF animation channel.
 */
@Serializable
data class GltfAnimationChannel(
    /** The index of the sampler used in this channel. */
    val sampler: Int,
    /** The target of the animation channel. */
    val target: GltfAnimationTarget,
)

/**
 * The target of an animation channel.
 */
@Serializable
data class GltfAnimationTarget(
    /** The index of the node to animate. */
    val node: Int? = null,
    /** `"translation"`, `"rotation"`, `"scale"`, or `"weights"`. */
    val path: String,
)

/**
 * A glTF animation sampler.
 */
@Serializable
data class GltfAnimationSampler(
    /** The index of an accessor containing keyframe timestamps. */
    val input: Int,
    /** The index of an accessor containing keyframe values. */
    val output: Int,
    /** `"LINEAR"`, `"STEP"`, or `"CUBICSPLINE"`. */
    val interpolation: String = "LINEAR",
)

/**
 * A glTF scene.
 */
@Serializable
data class GltfScene(
    /** The indices of each root node in the scene. */
    val nodes: List<Int> = emptyList(),
)

/**
 * A glTF node.
 */
@Serializable
data class GltfNode(
    /** The name of the node. */
    val name: String? = null,
    /** The index of the mesh in this node. */
    val mesh: Int? = null,
    /** Index into [GltfDocument.skins] -- present on the node that carries a skinned mesh. */
    val skin: Int? = null,
    /** 16 column-major floats -- mutually exclusive with [translation]/[rotation]/[scale]. */
    val matrix: List<Float>? = null,
    /** Translation `[x, y, z]`. */
    val translation: List<Float>? = null,
    /** Quaternion `[x, y, z, w]`. */
    val rotation: List<Float>? = null,
    /** Scale `[x, y, z]`. */
    val scale: List<Float>? = null,
    /** The indices of this node's children. */
    val children: List<Int> = emptyList(),
)

/**
 * A glTF buffer.
 */
@Serializable
data class GltfBuffer(
    /** A `data:application/octet-stream;base64,...` (or similar) data URI. */
    val uri: String? = null,
    /** The total length of the buffer in bytes. */
    val byteLength: Int = 0,
)

/**
 * A glTF buffer view.
 */
@Serializable
data class GltfBufferView(
    /** The index of the buffer. */
    val buffer: Int,
    /** The offset into the buffer in bytes. */
    val byteOffset: Int = 0,
    /** The length of the buffer view in bytes. */
    val byteLength: Int = 0,
    /** The stride, in bytes, between components. */
    val byteStride: Int? = null,
)

/**
 * A glTF accessor.
 */
@Serializable
data class GltfAccessor(
    /** The index of the bufferView. */
    val bufferView: Int? = null,
    /** The offset relative to the start of the bufferView in bytes. */
    val byteOffset: Int = 0,
    /** glTF's numeric component type constants. */
    val componentType: Int,
    /** The number of elements in the accessor. */
    val count: Int,
    /** `"SCALAR"`, `"VEC2"`, `"VEC3"`, or `"VEC4"`. */
    val type: String,
    /** Whether an integer [componentType] vertex attribute packs a normalized float. */
    val normalized: Boolean = false,
)

/**
 * glTF primitive attribute indices.
 */
@Serializable
data class GltfPrimitiveAttributes(
    /** Index of the POSITION attribute accessor. */
    @SerialName("POSITION") val position: Int? = null,
    /** Index of the NORMAL attribute accessor. */
    @SerialName("NORMAL") val normal: Int? = null,
    /** Index of the COLOR_0 attribute accessor. */
    @SerialName("COLOR_0") val color0: Int? = null,
    /** Index of the TEXCOORD_0 attribute accessor. */
    @SerialName("TEXCOORD_0") val texCoord0: Int? = null,
    /** Index of the JOINTS_0 attribute accessor. */
    @SerialName("JOINTS_0") val joints0: Int? = null,
    /** Index of the WEIGHTS_0 attribute accessor. */
    @SerialName("WEIGHTS_0") val weights0: Int? = null,
)

/**
 * A glTF primitive.
 */
@Serializable
data class GltfPrimitive(
    /** The attributes of the primitive. */
    val attributes: GltfPrimitiveAttributes,
    /** The index of the indices accessor. */
    val indices: Int? = null,
    /** The index of the material. */
    val material: Int? = null,
)

/**
 * A glTF mesh definition.
 */
@Serializable
data class GltfMeshDef(
    /** The name of the mesh. */
    val name: String? = null,
    /** The list of primitives in the mesh. */
    val primitives: List<GltfPrimitive> = emptyList(),
)
