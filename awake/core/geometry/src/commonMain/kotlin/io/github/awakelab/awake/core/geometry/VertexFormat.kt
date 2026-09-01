/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.geometry

/**
 * An ordered, tightly-packed interleaved vertex layout -- [attributes] in declaration order,
 * each attribute's byte offset computed from the cumulative size of the attributes before it
 * (no manual offset bookkeeping, unlike the hand-written offset tables this replaces in a
 * backend's own vertex-input-state setup). [strideBytes] is the total per-vertex byte size --
 * the same value backends used to thread through as a bare `vertexStride: Int` parameter.
 */
data class VertexFormat(val attributes: List<VertexAttribute>) {
    data class Entry(val attribute: VertexAttribute, val offsetBytes: Int)

    val entries: List<Entry> = attributes
        .runningFold(0) { offset, attribute -> offset + attribute.format.vertexByteSize }
        .zip(attributes) { offset, attribute -> Entry(attribute, offset) }
    val strideBytes: Int = attributes.sumOf { it.format.vertexByteSize }

    /** One vertex's float count -- what a CPU-side interleaved `FloatArray` strides by, where
     * [strideBytes] is what the GPU vertex buffer strides by. Derived, so an interleaver cannot
     * disagree with the format it claims to write. */
    val strideFloats: Int = strideBytes / Float.SIZE_BYTES

    /**
     * Where [semantic] starts within a vertex, counted in floats, or -1 when this format has no
     * such attribute.
     *
     * For code that reads or edits an already-packed buffer, where [InterleavedVertices] does not
     * apply -- tagging every vertex's colour channel after a merge, for instance. The alternative
     * is a hand-maintained `COLOR_OFFSET = 6` beside a hand-maintained `STRIDE = 11`, which is two
     * more restatements of this format to keep in step with it.
     */
    fun floatOffsetOf(semantic: VertexSemantic): Int =
        entries.firstOrNull { it.attribute.semantic == semantic }
            ?.let { it.offsetBytes / Float.SIZE_BYTES }
            ?: -1

    companion object {
        /**
         * No vertex buffer at all -- the vertex shader generates its own positions from
         * `vertex_index`, as a full-screen triangle does (see `skybox.wgsl`).
         *
         * Not a degenerate case to guard against: it is how a pipeline that draws without
         * geometry is spelled, so such a pipeline can be an ordinary [VertexFormat]-keyed
         * `PipelineSpec` instead of a hand-written class per backend. Both backends skip the
         * vertex-rate binding entirely when [attributes] is empty -- binding a stride-0 buffer
         * that no attribute reads is not the same thing.
         */
        val None = VertexFormat(emptyList())

        /** Position vec3 @ 0, color vec3 @ 12, stride 24 -- for an unlit colored mesh or
         * triangle shader with no normal or UV attributes (e.g. `samples:ui-showcase`). */
        val PositionColor = VertexFormat(
            listOf(
                VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec3, location = 0),
                VertexAttribute(VertexSemantic.Color, GpuDataShape.Vec3, location = 1),
            ),
        )

        /** Position vec3 @ 0, color vec3 @ 12, uv vec2 @ 24, stride 32 -- the exact layout every
         * backend's vertex pipeline hardcoded before this type existed. Kept as the default
         * [io.github.awakelab.awake.core.geometry.MeshGeometry] format so every pre-existing
         * mesh/shader pair keeps working unchanged. */
        val PositionColorUv = VertexFormat(
            listOf(
                VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec3, location = 0),
                VertexAttribute(VertexSemantic.Color, GpuDataShape.Vec3, location = 1),
                VertexAttribute(VertexSemantic.Uv, GpuDataShape.Vec2, location = 2),
            ),
        )

        /** Position vec3 @ 0, normal vec3 @ 12, color vec3 @ 24, stride 36 -- for a shader that
         * shades with real per-vertex normals (Lambertian diffuse) instead of flat unlit vertex
         * color. Not the [MeshGeometry] default -- opt in per bootstrap (see
         * `Scene3DPlaygroundVulkanBootstrap.kt`'s own `vertexFormat` choice) since every mesh
         * drawn through one [io.github.awakelab.awake.render.renderer.Renderer] shares its
         * one bound pipeline's vertex layout; switching this changes every mesh in that renderer,
         * not just one. */
        val PositionNormalColor = VertexFormat(
            listOf(
                VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec3, location = 0),
                VertexAttribute(VertexSemantic.Normal, GpuDataShape.Vec3, location = 1),
                VertexAttribute(VertexSemantic.Color, GpuDataShape.Vec3, location = 2),
            ),
        )

        /** [PositionNormalColor] plus joint indices (uint4 @ 36) and joint weights (vec4 @ 52),
         * stride 68 -- for a GPU-skinned mesh's vertex shader, which blends up to 4 joint
         * matrices by weight before applying MVP (see `skinned.wgsl`). Matches
         * [io.github.awakelab.awake.asset.gltf.GltfMesh.toInterleavedSkinned]'s layout. */
        val PositionNormalColorSkin = VertexFormat(
            listOf(
                VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec3, location = 0),
                VertexAttribute(VertexSemantic.Normal, GpuDataShape.Vec3, location = 1),
                VertexAttribute(VertexSemantic.Color, GpuDataShape.Vec3, location = 2),
                VertexAttribute(VertexSemantic.JointIndices, GpuDataShape.UInt4, location = 3),
                VertexAttribute(VertexSemantic.JointWeights, GpuDataShape.Vec4, location = 4),
            ),
        )

        /** [PositionNormalColorSkin] plus a uv vec2 @ 36 (shifts JointIndices to location 4 and JointWeights to location 5),
         * stride 76 -- for a GPU-skinned mesh that samples a diffuse texture. */
        val PositionNormalColorUvSkin = VertexFormat(
            listOf(
                VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec3, location = 0),
                VertexAttribute(VertexSemantic.Normal, GpuDataShape.Vec3, location = 1),
                VertexAttribute(VertexSemantic.Color, GpuDataShape.Vec3, location = 2),
                VertexAttribute(VertexSemantic.Uv, GpuDataShape.Vec2, location = 3),
                VertexAttribute(VertexSemantic.JointIndices, GpuDataShape.UInt4, location = 4),
                VertexAttribute(VertexSemantic.JointWeights, GpuDataShape.Vec4, location = 5),
            ),
        )

        /** [PositionNormalColor] plus a uv vec2 @ 36, stride 44 -- for a mesh whose material has
         * a real `baseColorTexture` to sample (see `textured.wgsl`). Matches
         * [io.github.awakelab.awake.asset.gltf.GltfMesh.toInterleavedPositionNormalColorUv]'s
         * layout. */
        val PositionNormalColorUv = VertexFormat(
            listOf(
                VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec3, location = 0),
                VertexAttribute(VertexSemantic.Normal, GpuDataShape.Vec3, location = 1),
                VertexAttribute(VertexSemantic.Color, GpuDataShape.Vec3, location = 2),
                VertexAttribute(VertexSemantic.Uv, GpuDataShape.Vec2, location = 3),
            ),
        )

        /** Position vec3 @ 0, uv vec2 @ 12, stride 20 -- no normal/color, for a shared unit
         * billboard quad whose per-instance model matrix (location 3+, see the instanced vertex
         * buffer a particle pipeline binds alongside this) supplies world position/scale and
         * whose per-instance alpha (a separate small vertex buffer, one f32/instance) supplies
         * fade -- the quad itself contributes only its local corner position and uv (see
         * `particle.wgsl`). */
        val PositionUv = VertexFormat(
            listOf(
                VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec3, location = 0),
                VertexAttribute(VertexSemantic.Uv, GpuDataShape.Vec2, location = 1),
            ),
        )
    }
}
