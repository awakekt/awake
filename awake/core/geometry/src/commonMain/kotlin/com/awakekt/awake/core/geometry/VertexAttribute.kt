/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry

/**
 * The natural, UNPADDED component shape of one GPU value -- [Vec3] is always 3 components. How
 * many floats/bytes that actually occupies in a given buffer depends on THAT buffer's own
 * alignment rules, not on this type -- see [vertexByteSize] (vertex buffers, no padding) vs
 * [com.awakekt.awake.render.renderer.uniformFloats] (uniform buffers, std140/WGSL's
 * mandatory vec3->vec4 padding). One shared shape, two buffer-specific sizing extensions -- not
 * two separate enums that happen to mean almost the same thing. Which meaning applies at any
 * given call site comes from the CONTAINING field ([VertexAttribute.format] vs a uniform
 * layout's own field type), not from this enum's case names.
 *
 * @property componentCount Number of scalar components in this shape.
 * @property bytesPerComponent Size in bytes of each scalar component.
 */
enum class GpuDataShape(val componentCount: Int, val bytesPerComponent: Int = FLOAT_BYTES) {
    // Float/Vec2/Vec3/Vec4 shadow well-known names (kotlin.Float, and Vec2/Vec3/Vec4 in
    // core.math) -- always reference these qualified as GpuDataShape.Float/.Vec3/etc (as every
    // call site in this codebase already does), never with an unqualified import, or a bare
    // `Float` inside a `when (this)` branch silently resolves to the wrong type.

    /** Single 32-bit floating-point value. */
    Float(1),

    /** 2-component 32-bit floating-point vector. */
    Vec2(2),

    /** 3-component 32-bit floating-point vector. */
    Vec3(3),

    /** 4-component 32-bit floating-point vector. */
    Vec4(4),

    /** 4x4 32-bit floating-point matrix consisting of 16 float components. */
    Mat4(16),

    /**
     * Four-component unsigned integer (4 bytes per component).
     *
     * glTF's `JOINTS_0` is `ubyte4`/`ushort4`; this widens either to 4 bytes/component at
     * import time so there's one integer vertex format to map per backend instead of two.
     * Vertex-buffer only -- no uniform buffer in this codebase has an integer field.
     */
    UInt4(4),
}

private const val FLOAT_BYTES = 4

/**
 * Unpadded vertex-buffer byte size -- what [VertexFormat] uses to compute each attribute's
 * offset/stride.
 */
val GpuDataShape.vertexByteSize: Int get() = componentCount * bytesPerComponent

/**
 * What role an attribute's data plays -- distinct from [GpuDataShape], which is just the
 * numeric shape; a mesh importer/builder picks the semantic, [GpuDataShape] follows.
 */
enum class VertexSemantic {
    /** Vertex position in model or world space. */
    Position,

    /** Surface normal vector for lighting calculations. */
    Normal,

    /** Texture coordinates for UV mapping. */
    Uv,

    /** Per-vertex color value. */
    Color,

    /** Skeletal animation joint indices. */
    JointIndices,

    /** Skeletal animation joint weights. */
    JointWeights,

    /** Tangent vector for normal mapping. */
    Tangent,

    /** Instance or model transformation data. */
    Transform,

    /** Local coordinate position within an element or primitive. */
    LocalPosition,

    /** Size or dimensions of a primitive or billboard. */
    Size,

    /** Corner or boundary radius for SDF or rounded primitives. */
    Radius,

    /** Edge smoothing or anti-aliasing factor. */
    Smoothing,

    /** Custom application-defined shader attribute. */
    Custom,
}

/**
 * One named slot in an interleaved vertex buffer -- a [semantic] role plus the [format]
 * backing it. [location] is the shader input location this attribute binds to
 * (`layout(location = N)` in GLSL / `@location(N)` in WGSL) -- caller-assigned, not
 * auto-numbered from list position, so a shader's location layout stays an explicit, readable
 * contract instead of an implicit array-index one.
 *
 * @property semantic The semantic role this attribute serves.
 * @property format The natural component shape of the attribute data.
 * @property location The shader input location index.
 */
data class VertexAttribute(
    val semantic: VertexSemantic,
    val format: GpuDataShape,
    val location: Int,
)
