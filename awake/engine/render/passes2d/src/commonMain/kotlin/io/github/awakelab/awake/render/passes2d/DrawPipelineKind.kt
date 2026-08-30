/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.passes2d

import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.geometry.VertexFormats2D
import io.github.awakelab.awake.render.pipeline.UiPipelineVariant

/**
 * The four flavors of 2D draw primitive pipeline, and the vertex layout each one draws through.
 */
enum class DrawPipelineKind(val vertexFormat: VertexFormat, val pipelineVariant: UiPipelineVariant) {
    Quad(VertexFormats2D.Quad, UiPipelineVariant.Quad),
    Glyph(VertexFormats2D.Glyph, UiPipelineVariant.Glyph),
    Texture(VertexFormats2D.Glyph, UiPipelineVariant.Texture),
    RoundedQuad(VertexFormats2D.RoundedQuad, UiPipelineVariant.RoundedQuad),
    ;

    /** Derived from the format, never hand-counted. */
    val floatsPerVertex: Int get() = vertexFormat.strideBytes / Float.SIZE_BYTES
}

typealias UiPipelineKind = DrawPipelineKind
