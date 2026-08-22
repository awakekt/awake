// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.passes2d

import io.github.ronjunevaldoz.awake.core.math2d.Rectangle

/**
 * A typed, coalesced 2D draw command in paint order, ready for GPU buffer upload and recording.
 */
sealed class StagedDrawRun {
    /** Flat colored 2D quads, gradients, clipped paths, or AA-tessellated convex polygons. */
    class QuadRun(val vertices: FloatArray, val indices: IntArray) : StagedDrawRun()

    /** SDF rounded rectangle and shadow quads. */
    class RoundedQuadRun(val vertices: FloatArray, val indices: IntArray) : StagedDrawRun()

    /** Textured font glyph quads. */
    class GlyphRun(val vertices: FloatArray, val indices: IntArray) : StagedDrawRun()

    /** Textured image or render target primitives. */
    class TextureRun(val primitives: List<TexturedDrawRun>) : StagedDrawRun()

    /** Scissor rectangle update in original emission order. */
    class ClipRun(val rect: Rectangle) : StagedDrawRun()
}

typealias UiStagedRun = StagedDrawRun

/**
 * A single textured primitive within a [StagedDrawRun.TextureRun].
 */
data class TexturedDrawRun(
    val texture: Any,
    val vertices: FloatArray,
    val indices: IntArray,
)

typealias TexturedPrimitiveRun = TexturedDrawRun

/**
 * A GPU-uploaded 2D draw run ready for command recording, in original paint order.
 * Parameterized by mesh handle [M] (e.g. `DynamicMesh`).
 */
sealed class DrawRun<out M> {
    class QuadRun<M>(val mesh: M) : DrawRun<M>()
    class RoundedQuadRun<M>(val mesh: M) : DrawRun<M>()
    class GlyphRun<M>(val mesh: M) : DrawRun<M>()
    class TextureRun(val primitives: List<TexturedDrawRun>) : DrawRun<Nothing>()
    class ClipRun(val rect: Rectangle) : DrawRun<Nothing>()
}

typealias UiRun<M> = DrawRun<M>
