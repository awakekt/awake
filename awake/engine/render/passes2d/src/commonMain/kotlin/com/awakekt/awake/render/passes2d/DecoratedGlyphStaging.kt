/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawTransform
import com.awakekt.awake.core.graphics2d.GlyphDecoration
import com.awakekt.awake.core.graphics2d.IDENTITY_TRANSFORM
import com.awakekt.awake.core.graphics2d.TexturedTriangleMesh
import com.awakekt.awake.core.text.font.UiFont

/**
 * Stages glyphs that carry a [GlyphDecoration] (#1061) in [DrawPipelineKind.DecoratedGlyph]'s layout: one quad a glyph,
 * its own quad grown to the decoration's reach ([GlyphDecoration.drawnBounds]). Both the atlas uv and the effect-field
 * uv are affine in position, so every vertex, a clipped one too, takes them from where it lands.
 */
internal object DecoratedGlyphStaging {
    private const val STRIDE = VertexFormats2D.DECORATED_GLYPH_FLOATS_PER_VERTEX

    /** One run of [glyphs], every one decorated, sampling [font]'s atlas. */
    fun run(glyphs: List<DrawCommand.Glyph>, font: UiFont?): StagedDrawRun.GlyphRun {
        val vertices = FloatArray(glyphs.size * VertexFormats2D.VERTICES_PER_QUAD * STRIDE)
        val indices = IntArray(glyphs.size * VertexFormats2D.INDICES_PER_QUAD)
        val bounds = FloatArray(4)
        for (index in glyphs.indices) {
            val glyph = glyphs[index]
            decorationOf(glyph).drawnBounds(glyph.x, glyph.y, glyph.w, glyph.h, bounds)
            val base = index * VertexFormats2D.VERTICES_PER_QUAD
            write(vertices, base * STRIDE, glyph, bounds[0], bounds[1])
            write(vertices, (base + 1) * STRIDE, glyph, bounds[2], bounds[1])
            write(vertices, (base + 2) * STRIDE, glyph, bounds[2], bounds[3])
            write(vertices, (base + 3) * STRIDE, glyph, bounds[0], bounds[3])
            for (corner in QUAD_INDICES.indices) indices[index * VertexFormats2D.INDICES_PER_QUAD + corner] = base + QUAD_INDICES[corner]
        }
        return StagedDrawRun.GlyphRun(vertices, indices, font, decorated = true)
    }

    /** [glyph]'s drawn quad as a mesh to clip, carrying only positions; [run]'s attributes follow from them. */
    fun quad(glyph: DrawCommand.Glyph): TexturedTriangleMesh {
        val bounds = FloatArray(4)
        decorationOf(glyph).drawnBounds(glyph.x, glyph.y, glyph.w, glyph.h, bounds)
        return DrawRunCoalescer.texturedQuadMesh(bounds[0], bounds[1], bounds[2] - bounds[0], bounds[3] - bounds[1])
    }

    /** One run of [clipped] decorated glyphs, each the mesh [quad] gave and a path clip cut, sampling [font]'s atlas. */
    fun clippedRun(clipped: List<Pair<TexturedTriangleMesh, DrawCommand.Glyph>>, font: UiFont?): StagedDrawRun.GlyphRun {
        val vertices = FloatArray(clipped.sumOf { it.first.vertices.size } * STRIDE)
        val indices = IntArray(clipped.sumOf { it.first.indices.size })
        var vertexCount = 0
        var indexCount = 0
        for ((mesh, glyph) in clipped) {
            for (index in mesh.indices) indices[indexCount++] = vertexCount + index
            for (vertex in mesh.vertices) write(vertices, vertexCount++ * STRIDE, glyph, vertex.position.x, vertex.position.y)
        }
        return StagedDrawRun.GlyphRun(vertices, indices, font, decorated = true)
    }

    private fun decorationOf(glyph: DrawCommand.Glyph): GlyphDecoration =
        requireNotNull(glyph.decoration) { "A decorated glyph run holds only decorated glyphs." }

    /**
     * One vertex at ([px], [py]), in `VertexFormats2D.DecoratedGlyph`'s order: position, atlas uv and rect, field uv
     * and rect, the three colours, then the effect in field uv (outline width, blur, shadow offset) and the transform.
     */
    private fun write(out: FloatArray, offset: Int, glyph: DrawCommand.Glyph, px: Float, py: Float) {
        val d = decorationOf(glyph)
        val fieldPerPxU = (d.fieldU1 - d.fieldU0) / d.fieldW
        val fieldPerPxV = (d.fieldV1 - d.fieldV0) / d.fieldH
        out[offset] = px
        out[offset + 1] = py
        out[offset + 2] = glyph.u0 + (px - glyph.x) / glyph.w * (glyph.u1 - glyph.u0)
        out[offset + 3] = glyph.v0 + (py - glyph.y) / glyph.h * (glyph.v1 - glyph.v0)
        putRect(out, offset + 4, glyph.u0, glyph.v0, glyph.u1, glyph.v1)
        out[offset + 8] = d.fieldU0 + (px - d.fieldX) * fieldPerPxU
        out[offset + 9] = d.fieldV0 + (py - d.fieldY) * fieldPerPxV
        putRect(out, offset + 10, d.fieldU0, d.fieldV0, d.fieldU1, d.fieldV1)
        putColor(out, offset + 14, glyph.color)
        putColor(out, offset + 18, d.outlineColor)
        putColor(out, offset + 22, d.shadowColor)
        out[offset + 26] = if (d.outlineColor.isTransparent()) 0f else d.outlineWidth * fieldPerPxU
        out[offset + 27] = d.shadowBlur * fieldPerPxU
        out[offset + 28] = d.shadowOffsetX * fieldPerPxU
        out[offset + 29] = d.shadowOffsetY * fieldPerPxV
        val transform: DrawTransform = glyph.transform ?: IDENTITY_TRANSFORM
        out[offset + 30] = transform.scaleX
        out[offset + 31] = transform.scaleY
        out[offset + 32] = transform.pivotX
        out[offset + 33] = transform.pivotY
    }

    private fun putRect(out: FloatArray, at: Int, u0: Float, v0: Float, u1: Float, v1: Float) {
        out[at] = minOf(u0, u1)
        out[at + 1] = minOf(v0, v1)
        out[at + 2] = maxOf(u0, u1)
        out[at + 3] = maxOf(v0, v1)
    }

    private fun putColor(out: FloatArray, at: Int, color: Color) {
        out[at] = color.r
        out[at + 1] = color.g
        out[at + 2] = color.b
        out[at + 3] = color.a
    }

    private val QUAD_INDICES = intArrayOf(0, 1, 2, 2, 3, 0)
}
