/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.render.pipeline.UiPipelineVariant

/**
 * The four flavors of 2D draw primitive pipeline, and the vertex layout each one draws through.
 */
enum class DrawPipelineKind(
    /** Vertex layout format consumed by this pipeline. */
    val vertexFormat: VertexFormat,
    /** The corresponding UI pipeline descriptor variant. */
    val pipelineVariant: UiPipelineVariant,
) {
    /** Solid colored flat rectangle pipeline. */
    Quad(VertexFormats2D.Quad, UiPipelineVariant.Quad),
    /** Text glyph rasterization pipeline. */
    Glyph(VertexFormats2D.Glyph, UiPipelineVariant.Glyph),
    /** Textured 2D quad pipeline. */
    Texture(VertexFormats2D.Glyph, UiPipelineVariant.Texture),
    /** Rounded rectangle pipeline with corner radius and antialiasing. */
    RoundedQuad(VertexFormats2D.RoundedQuad, UiPipelineVariant.RoundedQuad),
    ;

    /** Derived from the format, never hand-counted. */
    val floatsPerVertex: Int get() = vertexFormat.strideBytes / Float.SIZE_BYTES
}

typealias UiPipelineKind = DrawPipelineKind
