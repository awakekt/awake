// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.core.graphics2d

import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle

/**
 * Per-command GPU-applied scale, threaded from a `graphicsLayer(scale(...))` block (see
 * `modifier/GraphicsLayer.kt`'s `UiScaleEffect`) all the way to the backend's draw call --
 * unlike alpha, scale changes geometry so it can't be resolved as a CPU-side field multiply.
 * [pivotX]/[pivotY] are in the same pixel-space coordinates as the carrying command's own
 * `x`/`y`. Applied in the vertex shader as `pivot + (position - pivot) * scale`, BEFORE the
 * screen-to-NDC transform (see `ui_quad.vert`/`.wgsl` and its 3 siblings) -- rotation is
 * explicitly out of scope for this first pass (see
 * docs/tasks/2026-08-02-graphicslayer-rotation-scale.md).
 */
data class DrawTransform(
    val scaleX: Float,
    val scaleY: Float,
    val pivotX: Float,
    val pivotY: Float,
)

typealias UiPrimitiveTransform = DrawTransform
typealias PrimitiveTransform = DrawTransform
typealias DrawPrimitive = DrawCommand

/**
 * Backend-neutral 2D draw command emitted by UI or 2D rendering systems -- each backend's `Renderer.drawUi`
 * converts these into its own dynamic vertex/index buffer or command buffer dispatches.
 * Pixel-space coordinates (screen-space, Y-down), not NDC.
 */
sealed class DrawCommand {

    data class Quad(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val color: Color,
        val tokenId: String? = null,
        val transform: DrawTransform? = null,
    ) : UiDrawPrimitive()

    data class GradientQuad(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val gradient: LinearGradient,
        val tokenId: String? = null,
    ) : UiDrawPrimitive()

    /** Rounded-corner sibling of [Quad] -- kept as a separate type rather than a `radius`
     * field on [Quad] so the hot-path flat rect every existing widget already emits every
     * frame never pays a corner-test cost (kool-engine's own `RectBackground` vs
     * `RoundRectBackground` split backs this). */
    data class RoundedQuad(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val color: Color,
        val radius: Float,
        val smoothing: Float = 0.0f,
        val tokenId: String? = null,
        val transform: DrawTransform? = null,
    ) : UiDrawPrimitive()

    /** One glyph quad sampling a font atlas, drawn via textured pipeline. */
    data class Glyph(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
        val color: Color,
        val tokenId: String? = null,
        val transform: DrawTransform? = null,
    ) : UiDrawPrimitive()

    /** Renderer-neutral filled shape primitive. */
    data class FilledPath(
        val path: DrawPath,
        val color: Color,
        val tokenId: String? = null,
    ) : UiDrawPrimitive()

    /** Stroke sibling of [FilledPath]. */
    data class StrokedPath(
        val path: DrawPath,
        val stroke: DrawStroke,
        val color: Color,
        val tokenId: String? = null,
    ) : UiDrawPrimitive()

    /** One screen-space quad sampling an arbitrary render-target-backed material. */
    data class Texture(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val material: Any,
        val tokenId: String? = null,
        val transform: DrawTransform? = null,
    ) : UiDrawPrimitive()

    /** Drop-shadow primitive for elevation support. */
    data class ShadowQuad(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val radius: Float,
        val offsetX: Float,
        val offsetY: Float,
        val blurRadius: Float,
        val spread: Float,
        val color: Color,
        val tokenId: String? = null,
    ) : UiDrawPrimitive()

    /** Path-based clip sibling of [ClipPush]. */
    data class ClipPathPush(
        val path: DrawPath,
        val boundsRect: Rectangle,
        val safeInteriorRect: Rectangle? = null,
    ) : UiDrawPrimitive()

    /** Marks the start of a clipped region. */
    data class ClipPush(val rect: Rectangle) : UiDrawPrimitive()

    /** Restores the scissor rect that was active before the matching [ClipPush]. */
    data class ClipPop(val restoreRect: Rectangle) : UiDrawPrimitive()
}

sealed class UiDrawPrimitive : DrawCommand() {
    typealias Quad = DrawCommand.Quad
    typealias GradientQuad = DrawCommand.GradientQuad
    typealias RoundedQuad = DrawCommand.RoundedQuad
    typealias Glyph = DrawCommand.Glyph
    typealias FilledPath = DrawCommand.FilledPath
    typealias StrokedPath = DrawCommand.StrokedPath
    typealias Texture = DrawCommand.Texture
    typealias ShadowQuad = DrawCommand.ShadowQuad
    typealias ClipPathPush = DrawCommand.ClipPathPush
    typealias ClipPush = DrawCommand.ClipPush
    typealias ClipPop = DrawCommand.ClipPop
}

@Suppress("UNCHECKED_CAST")
fun <T : DrawCommand> T.scaledByAlpha(factor: Float): T {
    if (factor >= 1f) return this
    val res = when (this) {
        is DrawCommand.Quad -> copy(color = color.withAlpha(color.a * factor))
        is DrawCommand.RoundedQuad -> copy(color = color.withAlpha(color.a * factor))
        is DrawCommand.Glyph -> copy(color = color.withAlpha(color.a * factor))
        is DrawCommand.FilledPath -> copy(color = color.withAlpha(color.a * factor))
        is DrawCommand.StrokedPath -> copy(color = color.withAlpha(color.a * factor))
        is DrawCommand.GradientQuad -> copy(
            gradient = gradient.copy(
                topLeft = gradient.topLeft.withAlpha(gradient.topLeft.a * factor),
                topRight = gradient.topRight.withAlpha(gradient.topRight.a * factor),
                bottomRight = gradient.bottomRight.withAlpha(gradient.bottomRight.a * factor),
                bottomLeft = gradient.bottomLeft.withAlpha(gradient.bottomLeft.a * factor),
            ),
        )
        is DrawCommand.ShadowQuad -> copy(color = color.withAlpha(color.a * factor))
        is DrawCommand.Texture, is DrawCommand.ClipPathPush,
        is DrawCommand.ClipPush, is DrawCommand.ClipPop,
        -> this
    }
    return res as T
}

@Suppress("UNCHECKED_CAST")
fun <T : DrawCommand> T.withTransform(transform: DrawTransform?): T {
    if (transform == null) return this
    val res = when (this) {
        is DrawCommand.Quad -> copy(transform = transform)
        is DrawCommand.RoundedQuad -> copy(transform = transform)
        is DrawCommand.Glyph -> copy(transform = transform)
        is DrawCommand.Texture -> copy(transform = transform)
        else -> this
    }
    return res as T
}
