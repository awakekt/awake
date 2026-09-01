/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.gltf

import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.geometry.InterleavedVertices
import io.github.awakelab.awake.core.geometry.VertexSemantic
import io.github.awakelab.awake.core.geometry.VertexFormat

/**
 * A single decoded glTF primitive's attribute arrays, before interleaving -- one `Float`
 * per component, row-major (`positions[i*3]`/`[i*3+1]`/`[i*3+2]` is vertex `i`'s x/y/z).
 * [normals]/[colors]/[uvs] are `null` when the source glTF primitive didn't have that
 * attribute at all, not zero-filled -- see [toInterleavedPositionColorUv] for the default
 * values used when interleaving a primitive that's missing one. [jointIndices]/[jointWeights]
 * are `null` unless the primitive had `JOINTS_0`/`WEIGHTS_0` (a skinned mesh) -- 4 values per
 * vertex either way, `jointIndices[i*4 + k]` is vertex `i`'s `k`-th joint index (widened from
 * glTF's `ubyte4`/`ushort4`), `jointWeights[i*4 + k]` its blend weight. [baseColorImageBytes]
 * is the primitive's material's `baseColorTexture` image, still encoded (PNG/JPEG, whatever
 * bytes the glTF file embedded) -- `null` unless the primitive has a material with one.
 * Decoding is deliberately not this parser's job (a platform concern, not a glTF-parsing one)
 * -- see `io.github.awakelab.awake.core.image.createBitmap`. [metallicRoughnessImageBytes]/
 * [normalImageBytes]/[occlusionImageBytes]/[emissiveImageBytes] are the same kind of
 * still-encoded, per-material image bytes, one per glTF PBR texture channel -- `null` unless
 * the primitive's material has that channel. [baseColorFactor] (`[r,g,b,a]`)/[metallicFactor]/
 * [roughnessFactor]/[emissiveFactor] (`[r,g,b]`) are the corresponding scalar/vector factors,
 * always present (glTF 2.0 spec default when the primitive has no material, or the material
 * doesn't set that field: `[1,1,1,1]`/`1f`/`1f`/`[0,0,0]`) -- multiplied into their texture's
 * sample, or standing alone when that channel has no texture at all.
 */
data class GltfMesh(
    /** Decoded vertex positions (3 floats per vertex). */
    val positions: FloatArray,
    /** Decoded vertex normals (3 floats per vertex), or null if absent. */
    val normals: FloatArray?,
    /** Decoded vertex colors (3 floats per vertex), or null if absent. */
    val colors: FloatArray?,
    /** Decoded vertex UVs (2 floats per vertex), or null if absent. */
    val uvs: FloatArray?,
    /** Primitive indices. */
    val indices: IntArray,
    /** Decoded joint indices (4 ints per vertex), or null if absent. */
    val jointIndices: IntArray? = null,
    /** Decoded joint weights (4 floats per vertex), or null if absent. */
    val jointWeights: FloatArray? = null,
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
) {
    /** The total number of vertices in this mesh. */
    val vertexCount: Int get() = positions.size / POSITION_COMPONENTS

    /**
     * Interleaves into the position(vec3)+color(vec3)+uv(vec2) -- 8 floats/vertex --
     * layout `VulkanApplication.cubeVertices` already uses (matching `triangle.vert`'s
     * location 0/1/2 inputs), so a parsed glTF mesh can feed the exact same
     * `Mesh(graphicsDevice, runOneTimeCommands, vertices, indices)` constructor the
     * hardcoded demo cube does. Missing [colors] default to white (`1, 1, 1`), missing
     * [uvs] default to `(0, 0)` -- both harmless, visible defaults rather than silently
     * wrong-looking geometry.
     */
    fun toInterleavedPositionColorUv(): FloatArray {
        val vertices = InterleavedVertices(VertexFormat.PositionColorUv, vertexCount)
        writeCommonAttributes(vertices)
        return vertices.toFloatArray()
    }

    /**
     * Interleaves into the position(vec3)+normal(vec3)+color(vec3) -- 9 floats/vertex --
     * layout [io.github.awakelab.awake.core.geometry.VertexFormat.PositionNormalColor]
     * describes, for a shader that shades with real per-vertex normals instead of flat unlit
     * vertex color. Missing [normals] default to world-up (`0, 1, 0`) -- an arbitrary but
     * harmless fallback (no glTF exporter omits `NORMAL` on a mesh meant to be shaded, so this
     * mainly guards a malformed/synthetic file); missing [colors] default to white, same as
     * [toInterleavedPositionColorUv].
     */
    fun toInterleavedPositionNormalColor(): FloatArray {
        val vertices = InterleavedVertices(VertexFormat.PositionNormalColor, vertexCount)
        writeCommonAttributes(vertices)
        return vertices.toFloatArray()
    }

    /**
     * Interleaves into position(vec3)+normal(vec3)+color(vec3)+uv(vec2) -- 11 floats/vertex --
     * layout [io.github.awakelab.awake.core.geometry.VertexFormat.PositionNormalColorUv]
     * describes, for a shader that samples [baseColorImageBytes] using [uvs]. Missing
     * [normals]/[colors] default the same way [toInterleavedPositionNormalColor] does; missing
     * [uvs] default to `(0, 0)`, same as [toInterleavedPositionColorUv].
     */
    fun toInterleavedPositionNormalColorUv(): FloatArray {
        val vertices = InterleavedVertices(VertexFormat.PositionNormalColorUv, vertexCount)
        writeCommonAttributes(vertices)
        return vertices.toFloatArray()
    }

    /**
     * Interleaves into position(vec3)+normal(vec3)+color(vec3)+jointIndices(uint4)+
     * jointWeights(vec4) -- 17 floats/vertex, matching
     * [io.github.awakelab.awake.core.geometry.VertexFormat.PositionNormalColorSkin]. Missing
     * [normals]/[colors] default the same way [toInterleavedPositionNormalColor] does. Requires
     * [jointIndices]/[jointWeights] to be present (only called for an actually-skinned
     * primitive). Joint indices are packed via [Float.fromBits] -- the interleaved buffer is a
     * `FloatArray` end to end, but [VertexAttributeFormat.UInt4][io.github.awakelab.awake.core.geometry.VertexAttributeFormat.UInt4]
     * tells the GPU to read those 4 bytes back as a raw `uint32`, not a float value, so the bit
     * pattern -- not the numeric float value -- has to equal the joint index.
     */
    fun toInterleavedSkinned(): FloatArray {
        val vertices = InterleavedVertices(VertexFormat.PositionNormalColorSkin, vertexCount)
        writeCommonAttributes(vertices)
        writeJoints(vertices)
        return vertices.toFloatArray()
    }

    /**
     * Interleaves into position(vec3)+normal(vec3)+color(vec3)+uv(vec2)+jointIndices(uint4)+
     * jointWeights(vec4) -- 19 floats/vertex, matching
     * [io.github.awakelab.awake.core.geometry.VertexFormat.PositionNormalColorUvSkin].
     */
    fun toInterleavedPositionNormalColorUvSkin(): FloatArray {
        val vertices = InterleavedVertices(VertexFormat.PositionNormalColorUvSkin, vertexCount)
        writeCommonAttributes(vertices)
        writeJoints(vertices)
        return vertices.toFloatArray()
    }

    /**
     * Position, normal, colour and UV, for whichever of them the target format actually has.
     *
     * One method for five layouts, because the layouts differ only in which slots exist -- and
     * `InterleavedVertices` skips a bulk copy for an attribute the format has no room for. The
     * defaults are the glTF-absent ones: an up normal, white, and a zero UV, all visible and
     * harmless rather than silently wrong-looking geometry.
     */
    private fun writeCommonAttributes(vertices: InterleavedVertices) {
        vertices.copyOrFill(VertexSemantic.Position, positions, POSITION_COMPONENTS)
        vertices.copyOrFill(VertexSemantic.Normal, normals, NORMAL_COMPONENTS, 0f, 1f, 0f)
        vertices.copyOrFill(VertexSemantic.Color, colors, COLOR_COMPONENTS, 1f, 1f, 1f)
        vertices.copyOrFill(VertexSemantic.Uv, uvs, UV_COMPONENTS, 0f, 0f)
    }

    /**
     * Joint indices and weights, required rather than defaulted.
     *
     * A primitive with no `JOINTS_0` has no business in a skinned layout: zero-filling would hand
     * the GPU joint 0 at weight 0 for every vertex, collapsing the mesh onto the root bone instead
     * of saying what went wrong.
     *
     * Indices are written as bit patterns via [Float.fromBits]. The buffer is a `FloatArray` end
     * to end, but the format declares this slot `UInt4`, so the GPU reads those four bytes back as
     * a `uint32` -- writing the numeric value would hand a shader 0x3F800000 as joint 1.
     */
    private fun writeJoints(vertices: InterleavedVertices) {
        val joints = requireNotNull(jointIndices) { "toInterleavedSkinned() requires jointIndices (JOINTS_0)." }
        val weights = requireNotNull(jointWeights) { "toInterleavedSkinned() requires jointWeights (WEIGHTS_0)." }
        for (i in 0 until vertexCount) {
            val base = i * JOINT_COMPONENTS
            vertices.put(
                i,
                VertexSemantic.JointIndices,
                Float.fromBits(joints[base]),
                Float.fromBits(joints[base + 1]),
                Float.fromBits(joints[base + 2]),
                Float.fromBits(joints[base + 3]),
            )
            vertices.put(
                i,
                VertexSemantic.JointWeights,
                weights[base],
                weights[base + 1],
                weights[base + 2],
                weights[base + 3],
            )
        }
    }

    private companion object {
        // Derived from the formats these interleavers write, not counted by hand. Each stride
        // was previously a literal duplicating a VertexFormat this module could not see, so
        // changing a format silently produced the wrong interleaving -- the same defect as the
        // rounded-quad stride drift, where webgpu held 15 against a shared 16.
        val POSITION_COMPONENTS = GpuDataShape.Vec3.componentCount
        val NORMAL_COMPONENTS = GpuDataShape.Vec3.componentCount
        val COLOR_COMPONENTS = GpuDataShape.Vec3.componentCount
        val UV_COMPONENTS = GpuDataShape.Vec2.componentCount
        val JOINT_COMPONENTS = GpuDataShape.UInt4.componentCount

        val VERTEX_STRIDE_COMPONENTS = VertexFormat.PositionColorUv.strideFloats
        val NORMAL_VERTEX_STRIDE_COMPONENTS = VertexFormat.PositionNormalColor.strideFloats
        val TEXTURED_VERTEX_STRIDE_COMPONENTS = VertexFormat.PositionNormalColorUv.strideFloats
        val SKINNED_VERTEX_STRIDE_COMPONENTS = VertexFormat.PositionNormalColorSkin.strideFloats
        val SKINNED_TEXTURED_VERTEX_STRIDE_COMPONENTS = VertexFormat.PositionNormalColorUvSkin.strideFloats
    }
}
