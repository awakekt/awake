// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.passes2d

import io.github.ronjunevaldoz.awake.core.geometry.VertexFormat
import io.github.ronjunevaldoz.awake.core.geometry.VertexFormats2D

/**
 * The four flavors of 2D draw primitive pipeline, and the vertex layout each one draws through.
 */
enum class DrawPipelineKind(val vertexFormat: VertexFormat) {
    Quad(VertexFormats2D.Quad),
    Glyph(VertexFormats2D.Glyph),
    Texture(VertexFormats2D.Glyph),
    RoundedQuad(VertexFormats2D.RoundedQuad),
    ;

    /** Derived from the format, never hand-counted. */
    val floatsPerVertex: Int get() = vertexFormat.strideBytes / Float.SIZE_BYTES
}

typealias UiPipelineKind = DrawPipelineKind
