/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.passes2d

import io.github.awakelab.awake.core.graphics2d.BlendMode
import io.github.awakelab.awake.core.graphics2d.TextureCompositeMode
import io.github.awakelab.awake.core.math2d.Rectangle
import io.github.awakelab.awake.render.pipeline.UiPipelineDescriptor

/**
 * A typed, coalesced 2D draw command in paint order, ready for GPU buffer upload and recording.
 */
sealed class StagedDrawRun {
    /**
     * Flat colored 2D quads, gradients, clipped paths, or AA-tessellated convex polygons.
     *
     * @property vertices The interleaved vertex data.
     * @property indices The index data.
     */
    class QuadRun(val vertices: FloatArray, val indices: IntArray) : StagedDrawRun()

    /**
     * SDF rounded rectangle and shadow quads.
     *
     * @property vertices The interleaved vertex data.
     * @property indices The index data.
     */
    class RoundedQuadRun(val vertices: FloatArray, val indices: IntArray) : StagedDrawRun()

    /**
     * Textured font glyph quads.
     *
     * @property vertices The interleaved vertex data.
     * @property indices The index data.
     */
    class GlyphRun(val vertices: FloatArray, val indices: IntArray) : StagedDrawRun()

    /**
     * Textured image or render target primitives.
     *
     * @property primitives The list of textured primitives.
     */
    class TextureRun(val primitives: List<TexturedDrawRun>) : StagedDrawRun()

    /**
     * Scissor rectangle update in original emission order.
     *
     * @property rect The clip rectangle.
     */
    class ClipRun(val rect: Rectangle) : StagedDrawRun()
}

/** Alias for [StagedDrawRun]. */
typealias UiStagedRun = StagedDrawRun

/**
 * A single textured primitive within a [StagedDrawRun.TextureRun].
 *
 * @property texture The texture handle (implementation specific).
 * @property vertices The interleaved vertex data.
 * @property indices The index data.
 * @property blendMode The blend mode.
 * @property premultiplied Whether the texture has premultiplied alpha.
 * @property compositeMode The derived texture composite mode.
 */
data class TexturedDrawRun(
    val texture: Any,
    val vertices: FloatArray,
    val indices: IntArray,
    val blendMode: BlendMode,
    val premultiplied: Boolean,
    val compositeMode: TextureCompositeMode = TextureCompositeMode(blendMode, premultiplied),
)

/** Alias for [TexturedDrawRun]. */
typealias TexturedPrimitiveRun = TexturedDrawRun

/**
 * A GPU-uploaded 2D draw run ready for command recording, in original paint order.
 * Parameterized by mesh handle [M] (e.g. `DynamicMesh`).
 */
sealed class DrawRun<out M> {
    /**
     * A quad draw run.
     *
     * @param M The backend's own mesh handle type.
     * @property mesh The mesh handle.
     */
    class QuadRun<M>(val mesh: M) : DrawRun<M>()

    /**
     * A rounded quad draw run.
     *
     * @param M The backend's own mesh handle type.
     * @property mesh The mesh handle.
     */
    class RoundedQuadRun<M>(val mesh: M) : DrawRun<M>()

    /**
     * A glyph draw run.
     *
     * @param M The backend's own mesh handle type.
     * @property mesh The mesh handle.
     */
    class GlyphRun<M>(val mesh: M) : DrawRun<M>()

    /**
     * A texture draw run.
     *
     * @property primitives The list of textured primitives.
     */
    class TextureRun(val primitives: List<TexturedDrawRun>) : DrawRun<Nothing>()

    /**
     * A clip update run.
     *
     * @property rect The clip rectangle.
     */
    class ClipRun(val rect: Rectangle) : DrawRun<Nothing>()
}

/** Alias for [DrawRun]. */
typealias UiRun<M> = DrawRun<M>

/**
 * Returns the backend-neutral [UiPipelineDescriptor] for this staged draw run, or `null` for non-pipeline commands like [StagedDrawRun.ClipRun].
 */
val StagedDrawRun.pipelineDescriptor: UiPipelineDescriptor?
    get() = when (this) {
        is StagedDrawRun.QuadRun -> UiPipelineDescriptor(
            variant = UiPipelineKind.Quad.pipelineVariant,
            vertexFormat = UiPipelineKind.Quad.vertexFormat,
        )
        is StagedDrawRun.RoundedQuadRun -> UiPipelineDescriptor(
            variant = UiPipelineKind.RoundedQuad.pipelineVariant,
            vertexFormat = UiPipelineKind.RoundedQuad.vertexFormat,
        )
        is StagedDrawRun.GlyphRun -> UiPipelineDescriptor(
            variant = UiPipelineKind.Glyph.pipelineVariant,
            vertexFormat = UiPipelineKind.Glyph.vertexFormat,
        )
        is StagedDrawRun.TextureRun -> UiPipelineDescriptor(
            variant = UiPipelineKind.Texture.pipelineVariant,
            vertexFormat = UiPipelineKind.Texture.vertexFormat,
        )
        is StagedDrawRun.ClipRun -> null
    }

/**
 * Returns the backend-neutral [UiPipelineDescriptor] for this textured primitive run.
 */
val TexturedDrawRun.pipelineDescriptor: UiPipelineDescriptor
    get() = UiPipelineDescriptor(
        variant = UiPipelineKind.Texture.pipelineVariant,
        vertexFormat = UiPipelineKind.Texture.vertexFormat,
        blendMode = blendMode,
        isPremultiplied = premultiplied,
    )
