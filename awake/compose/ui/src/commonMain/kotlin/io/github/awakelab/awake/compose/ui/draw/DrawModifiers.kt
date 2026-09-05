/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.draw

import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.graphics.BlendMode
import io.github.awakelab.awake.compose.ui.graphics.Brush
import io.github.awakelab.awake.compose.ui.graphics.RectangleShape
import io.github.awakelab.awake.compose.ui.graphics.RenderEffect
import io.github.awakelab.awake.compose.ui.graphics.Shape
import io.github.awakelab.awake.compose.ui.graphics.ShapeOutline
import io.github.awakelab.awake.compose.ui.graphics.drawscope.DrawScope
import io.github.awakelab.awake.compose.ui.graphics.drawscope.LayerDrawScope
import io.github.awakelab.awake.compose.ui.graphics.shadow.Shadow
import io.github.awakelab.awake.compose.ui.node.DrawModifierNode
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.toPath
import io.github.awakelab.awake.core.math2d.Dp
import io.github.awakelab.awake.core.math2d.Size2D
import io.github.awakelab.awake.core.math2d.dp

/**
 * Draws underneath this node's content, in node-local coordinates.
 *
 * Immediate-mode drawing inside a retained tree. What `ui-core` got wrong was layout by
 * re-execution, not painting -- emitting primitives for one frame is stateless and always was
 * fine. A debug overlay or a gizmo wants exactly this and gains nothing from retained identity.
 */
fun Modifier.drawBehind(onDraw: DrawScope.() -> Unit): Modifier = this then DrawBehindElement(onDraw)

/**
 * Draws [shadow] behind this node, using [shape] as its caster outline.
 *
 * This matches Compose's `Modifier.dropShadow` layering: it paints before the rest of this
 * modifier chain, so place it before `background` when the background should sit above it.
 * Rectangle and uniformly rounded outlines retain the renderer's native shadow path. Generic
 * outlines render through a padded path mask and the graphics-layer blur path.
 */
fun Modifier.dropShadow(shape: Shape, shadow: Shadow): Modifier = this then DropShadowElement(shape, shadow)

/**
 * Draws around this node's content, calling [ContentDrawScope.drawContent] where it should appear.
 *
 * `drawBehind` is the common case with the call site fixed at the end. This is the general one: an
 * overlay calls `drawContent()` first, a background calls it last, and a scrim can draw on both
 * sides of it.
 *
 * Compose's name is `Modifier.drawWithContent`, and `clipToBounds` below is the same rename --
 * Compose's own `clip(shape)` takes a `Shape` this engine does not have, so the shapeless spelling
 * is the honest one to carry.
 */
fun Modifier.drawWithContent(onDraw: ContentDrawScope.() -> Unit): Modifier =
    this then DrawWithContentElement(onDraw)

/** Clips everything this node draws, including its children, to its own bounds. */
fun Modifier.clipToBounds(): Modifier = this then ClipElement

/** Clips this node and its descendants to [shape]. */
fun Modifier.clip(shape: Shape): Modifier =
    if (shape == RectangleShape) clipToBounds() else this then ShapeClipElement(shape)

/**
 * Scales this node's drawing about its own centre, composing with any enclosing scale.
 *
 * Applied to the coordinates as they are emitted rather than handed to the GPU as a per-primitive
 * transform. `DrawTransform` exists and the shaders honour it, but it reaches only four of the nine
 * primitive kinds -- a gradient, a path or a shadow under a scaled subtree would silently not
 * scale. Doing it here covers all nine and composes exactly.
 *
 * **Nesting composes**, which is the divergence `ui-core` documented and this engine closes: its
 * transform stack states outright that nested scale blocks do not compose multiplicatively.
 *
 * Not a layer: this scales what each child draws, so it does not resample a composited image.
 * Scaled text is a stretched atlas rather than re-shaped glyphs, and loses pixel snapping.
 */
fun Modifier.scale(scaleX: Float, scaleY: Float): Modifier =
    if (scaleX == 1f && scaleY == 1f) this else this then ScaleElement(scaleX, scaleY)

fun Modifier.scale(scale: Float): Modifier = scale(scale, scale)

/**
 * Controls the drawing order for the children of the same parent.
 *
 * Children with higher [zIndex] values are drawn on top of children with smaller [zIndex] values.
 * Children with identical [zIndex] values are drawn in declaration order.
 */
fun Modifier.zIndex(zIndex: Float): Modifier = this then ZIndexElement(zIndex)

/**
 * Renders this node's content into an isolated texture before compositing it into the parent.
 *
 * The Compose tree only records this request. Target allocation and destruction belong to the
 * renderer-aware host, keeping `:compose:ui` backend-neutral.
 */
fun Modifier.graphicsLayer(
    alpha: Float = 1f,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    translationX: Float = 0f,
    translationY: Float = 0f,
    rotationDegrees: Float = 0f,
    blendMode: BlendMode = BlendMode.SourceOver,
    renderEffect: RenderEffect? = null,
    shadowElevation: Dp = 0.dp,
    shape: Shape = RectangleShape,
): Modifier = this then GraphicsLayerElement(
    alpha = alpha.coerceIn(0f, 1f),
    scaleX = scaleX,
    scaleY = scaleY,
    translationX = translationX,
    translationY = translationY,
    rotationDegrees = rotationDegrees,
    blendMode = blendMode,
    renderEffect = renderEffect,
    shadowElevation = shadowElevation,
    shape = shape,
)

private class DrawBehindElement(
    private val onDraw: DrawScope.() -> Unit,
) : ModifierNodeElement<DrawBehindNode>() {
    override fun create(): DrawBehindNode = DrawBehindNode()
    override fun update(node: DrawBehindNode) {
        node.onDraw = onDraw
    }
    override fun toString(): String = "drawBehind()"
}

private class DrawBehindNode :
    Modifier.Node(),
    DrawModifierNode {
    lateinit var onDraw: DrawScope.() -> Unit
    override fun DrawScope.draw(drawContent: () -> Unit) {
        onDraw()
        drawContent()
    }

    override fun toString(): String = "drawBehind()"
}

private class DropShadowElement(
    private val shape: Shape,
    private val shadow: Shadow,
) : ModifierNodeElement<DropShadowNode>() {
    override fun create(): DropShadowNode = DropShadowNode()
    override fun update(node: DropShadowNode) {
        node.shape = shape
        node.shadow = shadow
    }
    override fun toString(): String = "dropShadow($shape, $shadow)"
}

private class DropShadowNode :
    Modifier.Node(),
    DrawModifierNode {
    lateinit var shape: Shape
    lateinit var shadow: Shadow
    override fun DrawScope.draw(drawContent: () -> Unit) {
        val outline = shape.createOutline(Size2D(width.toFloat(), height.toFloat()), density)
        val cornerRadius = when (outline) {
            is ShapeOutline.Rectangle -> 0f
            is ShapeOutline.Rounded -> outline.radius
            is ShapeOutline.Generic -> null
        }
        val brush = shadow.brush
        val color = (brush as? Brush.SolidColor)?.color ?: shadow.color
        val gradient = (brush as? Brush.LinearGradient)?.colors
        if (cornerRadius != null) {
            drawShadow(
                color = color.withAlpha(color.a * shadow.alpha),
                radius = cornerRadius,
                offsetX = shadow.offset.x.value * density,
                offsetY = shadow.offset.y.value * density,
                blurRadius = shadow.radius.value * density,
                spread = shadow.spread.value * density,
                gradient = gradient?.withShadowAlpha(shadow.alpha),
            )
        } else {
            require(gradient == null && shadow.spread.value == 0f) {
                "Generic shape shadows currently support solid, zero-spread masks only."
            }
            (this as LayerDrawScope).drawPathShadow(
                path = (outline as ShapeOutline.Generic).path,
                color = color.withAlpha(color.a * shadow.alpha),
                x = 0f,
                y = 0f,
                width = width.toFloat(),
                height = height.toFloat(),
                offsetX = shadow.offset.x.value * density,
                offsetY = shadow.offset.y.value * density,
                blurRadius = shadow.radius.value * density,
            )
        }
        drawContent()
    }

    override fun toString(): String = "dropShadow($shape, $shadow)"
}

private fun io.github.awakelab.awake.core.graphics2d.UiLinearGradient.withShadowAlpha(alpha: Float) = copy(
    topLeft = topLeft.withAlpha(topLeft.a * alpha),
    topRight = topRight.withAlpha(topRight.a * alpha),
    bottomRight = bottomRight.withAlpha(bottomRight.a * alpha),
    bottomLeft = bottomLeft.withAlpha(bottomLeft.a * alpha),
)

private class DrawWithContentElement(
    private val onDraw: ContentDrawScope.() -> Unit,
) : ModifierNodeElement<DrawWithContentNode>() {
    override fun create(): DrawWithContentNode = DrawWithContentNode()
    override fun update(node: DrawWithContentNode) {
        node.onDraw = onDraw
    }
    override fun toString(): String = "drawWithContent()"
}

private class DrawWithContentNode :
    Modifier.Node(),
    DrawModifierNode {
    lateinit var onDraw: ContentDrawScope.() -> Unit
    override fun DrawScope.draw(drawContent: () -> Unit) {
        // Allocated per draw link per frame, which is once per node that uses this modifier -- the
        // alternative is a reusable scope on the painter, and it would have to be re-entrant because
        // a drawWithContent may nest inside another.
        ContentDrawScopeImpl(this, drawContent).onDraw()
    }

    override fun toString(): String = "drawWithContent()"
}

private class ScaleElement(
    private val scaleX: Float,
    private val scaleY: Float,
) : ModifierNodeElement<ScaleNode>() {
    override fun create(): ScaleNode = ScaleNode()
    override fun update(node: ScaleNode) {
        node.scaleX = scaleX
        node.scaleY = scaleY
    }
    override fun toString(): String = "scale($scaleX, $scaleY)"
}

private class ScaleNode :
    Modifier.Node(),
    DrawModifierNode {
    var scaleX: Float = 1f
    var scaleY: Float = 1f
    override fun DrawScope.draw(drawContent: () -> Unit) {
        withScale(scaleX, scaleY) { drawContent() }
    }

    override fun toString(): String = "scale($scaleX, $scaleY)"
}

private class GraphicsLayerElement(
    private val alpha: Float,
    private val scaleX: Float,
    private val scaleY: Float,
    private val translationX: Float,
    private val translationY: Float,
    private val rotationDegrees: Float,
    private val blendMode: BlendMode,
    private val renderEffect: RenderEffect?,
    private val shadowElevation: Dp,
    private val shape: Shape,
) : ModifierNodeElement<GraphicsLayerNode>() {
    override fun create(): GraphicsLayerNode = GraphicsLayerNode()
    override fun update(node: GraphicsLayerNode) {
        node.alpha = alpha
        node.scaleX = scaleX
        node.scaleY = scaleY
        node.translationX = translationX
        node.translationY = translationY
        node.rotationDegrees = rotationDegrees
        node.blendMode = blendMode
        node.renderEffect = renderEffect
        node.shadowElevation = shadowElevation
        node.shape = shape
    }
    override fun toString(): String =
        "graphicsLayer(alpha=$alpha, scaleX=$scaleX, scaleY=$scaleY, rotation=$rotationDegrees, blendMode=$blendMode, renderEffect=$renderEffect)"
}

private class GraphicsLayerNode :
    Modifier.Node(),
    DrawModifierNode {
    var alpha: Float = 1f
    var scaleX: Float = 1f
    var scaleY: Float = 1f
    var translationX: Float = 0f
    var translationY: Float = 0f
    var rotationDegrees: Float = 0f
    lateinit var blendMode: BlendMode
    var renderEffect: RenderEffect? = null
    var shadowElevation: Dp = 0.dp
    lateinit var shape: Shape
    override fun DrawScope.draw(drawContent: () -> Unit) {
        (this as LayerDrawScope).drawLayer(
            alpha, scaleX, scaleY, translationX, translationY, rotationDegrees, blendMode, renderEffect, shadowElevation, shape, drawContent,
        )
    }

    override fun toString(): String = "graphicsLayer(alpha=$alpha, scaleX=$scaleX, scaleY=$scaleY, rotation=$rotationDegrees, blendMode=$blendMode, renderEffect=$renderEffect)"
}

private object ClipElement : ModifierNodeElement<ClipNode>() {
    override fun create(): ClipNode = ClipNode()
    override fun update(node: ClipNode) = Unit
    override fun toString(): String = "clip()"
}

private class ClipNode :
    Modifier.Node(),
    DrawModifierNode {
    override fun DrawScope.draw(drawContent: () -> Unit) {
        clipped { drawContent() }
    }

    override fun toString(): String = "clip()"
}

private class ShapeClipElement(
    private val shape: Shape,
) : ModifierNodeElement<ShapeClipNode>() {
    override fun create(): ShapeClipNode = ShapeClipNode()
    override fun update(node: ShapeClipNode) {
        node.shape = shape
    }
    override fun toString(): String = "clip($shape)"
}

private class ShapeClipNode :
    Modifier.Node(),
    DrawModifierNode {
    lateinit var shape: Shape
    override fun DrawScope.draw(drawContent: () -> Unit) {
        when (val outline = shape.createOutline(Size2D(width.toFloat(), height.toFloat()), density)) {
            is ShapeOutline.Rectangle -> clipped { drawContent() }
            is ShapeOutline.Rounded -> {
                val b = outline.bounds
                val r = outline.radius
                // Inset-by-radius on every side always lands inside a uniform-radius rounded
                // rect: at that inset, a corner's straight edges pass through the arc's own
                // center, and every other point is past the curved region entirely. Lets the
                // vast majority of content (everything but what's actually near a corner) skip
                // real polygon clipping -- see `clippedPath`'s doc for why that matters.
                val safeInterior = if (b.width > 2 * r && b.height > 2 * r) {
                    io.github.awakelab.awake.core.math2d.Rectangle(b.x + r, b.y + r, b.width - 2 * r, b.height - 2 * r)
                } else {
                    null
                }
                clippedPath(
                    io.github.awakelab.awake.core.graphics2d.DrawShape.RoundedRectangle(r.dp).toPath(b),
                    safeInterior,
                ) { drawContent() }
            }
            is ShapeOutline.Generic -> clippedPath(outline.path) { drawContent() }
        }
    }

    override fun toString(): String = "clip($shape)"
}

/**
 * Dims this node and everything under it by [alpha].
 *
 * Chain position matters: `alpha(0.5f).background(red)` dims the background, while
 * `background(red).alpha(0.5f)` leaves it opaque and dims only what comes after.
 *
 * A per-primitive multiply, not a layer -- overlapping children double-darken where they overlap.
 * See `10-graphics-layer.md` for what a real layer would need.
 */
fun Modifier.alpha(alpha: Float): Modifier =
    if (alpha >= 1f) this else this then AlphaElement(alpha)

private class AlphaElement(
    private val alpha: Float,
) : ModifierNodeElement<AlphaNode>() {
    override fun create(): AlphaNode = AlphaNode()
    override fun update(node: AlphaNode) {
        node.alpha = alpha
    }
    override fun toString(): String = "alpha($alpha)"
}

private class AlphaNode :
    Modifier.Node(),
    DrawModifierNode {
    var alpha: Float = 1f
    override fun DrawScope.draw(drawContent: () -> Unit) {
        withAlpha(alpha) { drawContent() }
    }

    override fun toString(): String = "alpha($alpha)"
}

private class ZIndexElement(
    private val zIndex: Float,
) : ModifierNodeElement<ZIndexNode>() {
    override fun create(): ZIndexNode = ZIndexNode(zIndex)
    override fun update(node: ZIndexNode) {
        node.zIndex = zIndex
    }
    override fun toString(): String = "zIndex($zIndex)"
}

private class ZIndexNode(
    var zIndex: Float,
) : Modifier.Node(),
    io.github.awakelab.awake.compose.ui.node.ZIndexModifierNode {
    override fun modifyZIndex(current: Float): Float = zIndex
    override fun toString(): String = "zIndex($zIndex)"
}
