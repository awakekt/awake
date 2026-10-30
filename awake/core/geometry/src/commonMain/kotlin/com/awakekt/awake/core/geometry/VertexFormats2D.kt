/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry

/**
 * Standardized vertex formats, strides, quad geometries, and buffer constants for 2D UI and draw rendering.
 */
object VertexFormats2D {

    /** 2D Colored Quad: pos (vec2) + color (vec4) + transform (vec4) = 10 floats (40 bytes). */
    val Quad = VertexFormat(
        listOf(
            VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec2, location = 0),
            VertexAttribute(VertexSemantic.Color, GpuDataShape.Vec4, location = 1),
            VertexAttribute(VertexSemantic.Transform, GpuDataShape.Vec4, location = 2),
        ),
    )

    /** 2D Textured Glyph: pos (vec2) + uv (vec2) + color (vec4) + transform (vec4) = 12 floats (48 bytes). */
    val Glyph = VertexFormat(
        listOf(
            VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec2, location = 0),
            VertexAttribute(VertexSemantic.Uv, GpuDataShape.Vec2, location = 1),
            VertexAttribute(VertexSemantic.Color, GpuDataShape.Vec4, location = 2),
            VertexAttribute(VertexSemantic.Transform, GpuDataShape.Vec4, location = 3),
        ),
    )

    /**
     * 2D decorated glyph (#1061), one quad drawing a glyph's fill, outline and shadow from one atlas page: pos (vec2) +
     * uv (vec2) + uvRect (vec4) + fieldUv (vec2) + fieldRect (vec4) + color (vec4) + outlineColor (vec4) + shadowColor
     * (vec4) + effect (vec4: outline width, shadow blur, shadow offset u, v; all in field uv) + transform (vec4) = 34
     * floats (136 bytes). Both uvs extend past their rects over the grown quad; the shader clamps each to its rect.
     */
    val DecoratedGlyph = VertexFormat(
        listOf(
            VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec2, location = 0),
            VertexAttribute(VertexSemantic.Uv, GpuDataShape.Vec2, location = 1),
            VertexAttribute(VertexSemantic.Custom, GpuDataShape.Vec4, location = 2),
            VertexAttribute(VertexSemantic.Custom, GpuDataShape.Vec2, location = 3),
            VertexAttribute(VertexSemantic.Custom, GpuDataShape.Vec4, location = 4),
            VertexAttribute(VertexSemantic.Color, GpuDataShape.Vec4, location = 5),
            VertexAttribute(VertexSemantic.Custom, GpuDataShape.Vec4, location = 6),
            VertexAttribute(VertexSemantic.Custom, GpuDataShape.Vec4, location = 7),
            VertexAttribute(VertexSemantic.Custom, GpuDataShape.Vec4, location = 8),
            VertexAttribute(VertexSemantic.Transform, GpuDataShape.Vec4, location = 9),
        ),
    )

    /** 2D Rounded Quad: pos (vec2) + localPos (vec2) + halfSize (vec2) + radius (float) + smoothing (float) + color (vec4) + transform (vec4) = 16 floats (64 bytes). */
    val RoundedQuad = VertexFormat(
        listOf(
            VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec2, location = 0),
            VertexAttribute(VertexSemantic.LocalPosition, GpuDataShape.Vec2, location = 1),
            VertexAttribute(VertexSemantic.Size, GpuDataShape.Vec2, location = 2),
            VertexAttribute(VertexSemantic.Radius, GpuDataShape.Float, location = 3),
            VertexAttribute(VertexSemantic.Smoothing, GpuDataShape.Float, location = 4),
            VertexAttribute(VertexSemantic.Color, GpuDataShape.Vec4, location = 5),
            VertexAttribute(VertexSemantic.Transform, GpuDataShape.Vec4, location = 6),
        ),
    )

    /** Colored quad layout float count. */
    const val FLOATS_PER_VERTEX = 10

    /** Textured glyph quad layout float count. */
    const val GLYPH_FLOATS_PER_VERTEX = 12

    /** Decorated glyph quad layout float count. */
    const val DECORATED_GLYPH_FLOATS_PER_VERTEX = 34

    /** Rounded quad layout float count. */
    const val ROUNDED_QUAD_FLOATS_PER_VERTEX = 16

    /** Standard quad geometry vertex count. */
    const val VERTICES_PER_QUAD = 4

    /** Number of triangle indices required to index a standard two-triangle quad. */
    const val INDICES_PER_QUAD = 6
}

typealias UiVertexLayout = VertexFormats2D
