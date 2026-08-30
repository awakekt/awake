/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.graphics2d

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.math2d.Rectangle

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

/**
 * Texture compositing operations supported by Awake's backend-neutral 2D pass.
 *
 * Source-over and plus use fixed-function blending. Destination-colour modes are valid only for
 * an isolated graphics layer; its host routes them through a sampled target-composite pass.
 */
enum class BlendMode {
    /** Standard straight-alpha source-over compositing. */
    SourceOver,

    /** Adds the source's covered colour to the destination, clamped by the attachment format. */
    Plus,

    /** Lightens source and destination by sampling both colours in an isolated layer pass. */
    Screen,

    /** Uses destination luminance to choose multiply or screen in an isolated layer pass. */
    Overlay,
}

/** True when this operation requires the already-painted destination to be sampled. */
val BlendMode.requiresDestinationSampling: Boolean
    get() = this == BlendMode.Screen || this == BlendMode.Overlay

/** Blend state required to composite a textured primitive without mixing alpha conventions. */
data class TextureCompositeMode(val blendMode: BlendMode, val premultiplied: Boolean)

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

    /**
     * Triangles the caller already tessellated, placed by [scaleX]/[scaleY]/[offsetX]/[offsetY].
     *
     * [FilledPath] and [StrokedPath] hand the coalescer a shape to tessellate *every frame*, which
     * is right for geometry that changes every frame and ruinous for geometry that never does: a
     * 16px outline icon tessellates into ~2,400 vertices, and Studio spent 20.2 ms of a 43.9 ms
     * frame on 38 of them. A caller that can hold its triangles still -- `VectorPainter` -- sends
     * them through here instead.
     *
     * The placement is kept beside the mesh rather than baked into it so that moving the node costs
     * four floats, not a re-tessellation. Vertex colours are baked in, so [alpha] dims them at
     * staging time the way `color.dimmedBy(alpha)` does for the other primitives.
     */
    data class Mesh(
        val mesh: ColoredTriangleMesh,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val alpha: Float = 1f,
        val tokenId: String? = null,
    ) : UiDrawPrimitive() {
        /**
         * These triangles with the placement and alpha folded in.
         *
         * The one definition of where a mesh lands, so a backend staging it and a rasterizer
         * checking it cannot drift apart. A staging pass already copying vertex by vertex applies
         * the same arithmetic inline rather than calling this, to avoid materialising the list.
         */
        fun placedMesh(): ColoredTriangleMesh = ColoredTriangleMesh(
            mesh.vertices.map {
                ColoredVertex(
                    position = DrawPoint(it.position.x * scaleX + offsetX, it.position.y * scaleY + offsetY),
                    color = if (alpha >= 1f) it.color else it.color.withAlpha(it.color.a * alpha),
                )
            },
            mesh.indices,
        )
    }

    /** One screen-space quad sampling an arbitrary render-target-backed material. */
    data class Texture(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val material: Any,
        /** Multiplies the sampled texture once at composite time. */
        val alpha: Float = 1f,
        /** Clockwise screen-space rotation about this texture's centre. */
        val rotationDegrees: Float = 0f,
        /** Composite operation used when this texture is painted into its parent target. */
        val blendMode: BlendMode = BlendMode.SourceOver,
        /** Render targets are premultiplied; uploaded image assets remain straight-alpha. */
        val premultiplied: Boolean = false,
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
        /** Four-corner gradient over the emitted shadow footprint, or null for [color]. */
        val gradient: LinearGradient? = null,
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
    typealias Mesh = DrawCommand.Mesh
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

        is DrawCommand.Texture -> copy(alpha = alpha * factor)

        is DrawCommand.Mesh -> copy(alpha = alpha * factor)

        is DrawCommand.ShadowQuad -> copy(
            color = color.withAlpha(color.a * factor),
            gradient = gradient?.copy(
                topLeft = gradient.topLeft.withAlpha(gradient.topLeft.a * factor),
                topRight = gradient.topRight.withAlpha(gradient.topRight.a * factor),
                bottomRight = gradient.bottomRight.withAlpha(gradient.bottomRight.a * factor),
                bottomLeft = gradient.bottomLeft.withAlpha(gradient.bottomLeft.a * factor),
            ),
        )
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

/** Returns this primitive translated in its pixel-space coordinate system. */
fun UiDrawPrimitive.translatedBy(dx: Float, dy: Float): UiDrawPrimitive = when (this) {
    is UiDrawPrimitive.Quad -> copy(x = x + dx, y = y + dy, transform = transform?.translatedBy(dx, dy))
    is UiDrawPrimitive.GradientQuad -> copy(x = x + dx, y = y + dy)
    is UiDrawPrimitive.RoundedQuad -> copy(x = x + dx, y = y + dy, transform = transform?.translatedBy(dx, dy))
    is UiDrawPrimitive.Glyph -> copy(x = x + dx, y = y + dy, transform = transform?.translatedBy(dx, dy))
    is UiDrawPrimitive.FilledPath -> copy(path = path.transform(translateX = dx, translateY = dy))
    is UiDrawPrimitive.StrokedPath -> copy(path = path.transform(translateX = dx, translateY = dy))
    is UiDrawPrimitive.Mesh -> copy(offsetX = offsetX + dx, offsetY = offsetY + dy)
    is UiDrawPrimitive.Texture -> copy(x = x + dx, y = y + dy, transform = transform?.translatedBy(dx, dy))
    is UiDrawPrimitive.ShadowQuad -> copy(x = x + dx, y = y + dy)
    is UiDrawPrimitive.ClipPathPush -> copy(
        path = path.transform(translateX = dx, translateY = dy),
        boundsRect = boundsRect.translatedBy(dx, dy),
        safeInteriorRect = safeInteriorRect?.translatedBy(dx, dy),
    )
    is UiDrawPrimitive.ClipPush -> copy(rect = rect.translatedBy(dx, dy))
    is UiDrawPrimitive.ClipPop -> copy(restoreRect = restoreRect.translatedBy(dx, dy))
}

private fun DrawTransform.translatedBy(dx: Float, dy: Float) =
    copy(pivotX = pivotX + dx, pivotY = pivotY + dy)

private fun Rectangle.translatedBy(dx: Float, dy: Float) = Rectangle(x + dx, y + dy, width, height)
