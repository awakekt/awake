/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics.drawscope

import com.awakekt.awake.compose.ui.graphics.RenderEffect
import com.awakekt.awake.compose.ui.graphics.Shape
import com.awakekt.awake.compose.ui.graphics.ShapeOutline
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.UiLinearGradient
import com.awakekt.awake.core.graphics2d.bounds
import com.awakekt.awake.core.graphics2d.transform
import com.awakekt.awake.core.graphics2d.translatedBy
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.intersect

/**
 * Where a node paints itself.
 *
 * Coordinates are **node-local**: `0, 0` is this node's top-left, and the scope adds its
 * tree-space origin on the way out. A draw node never needs to know where it sits, the same way a
 * measure policy never needs to know -- which is what lets a subtree be drawn in isolation.
 */
interface DrawScope {
    /** The node's own size, already measured. */
    val width: Int
    val height: Int

    val density: Float

    val layoutDirection: com.awakekt.awake.compose.ui.unit.LayoutDirection
        get() = com.awakekt.awake.compose.ui.unit.LayoutDirection.Ltr

    fun drawRect(
        x: Float = 0f,
        y: Float = 0f,
        width: Float = this.width.toFloat(),
        height: Float = this.height.toFloat(),
        color: Color,
    )

    fun drawRoundedRect(
        x: Float = 0f,
        y: Float = 0f,
        width: Float = this.width.toFloat(),
        height: Float = this.height.toFloat(),
        color: Color,
        radius: Float,
    )

    /**
     * Draws a rounded-rectangle drop shadow beneath this node's content.
     *
     * Coordinates are node-local. This is Awake's low-level primitive; applications should prefer
     * `Modifier.dropShadow`, whose shape and density-aware parameters follow Compose's public API.
     */
    @Suppress("LongParameterList")
    fun drawShadow(
        x: Float = 0f,
        y: Float = 0f,
        width: Float = this.width.toFloat(),
        height: Float = this.height.toFloat(),
        color: Color,
        radius: Float = 0f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        blurRadius: Float = 0f,
        spread: Float = 0f,
        gradient: UiLinearGradient? = null,
    )

    /** Clips everything drawn inside [block] to this node's bounds, inset by [inset]. */
    fun clipped(inset: Float = 0f, block: () -> Unit)

    /**
     * Dims everything drawn inside [block] by [alpha], multiplying with any enclosing dim.
     *
     * A CPU-side field multiply on each primitive's colour, which is what the render backends
     * already expect -- see `UiPrimitiveTransform`'s note that alpha, unlike scale, needs no GPU
     * work. Nesting composes, which `ui-core`'s transform stack documented that it does not.
     *
     * **Not a layer.** Two overlapping children under one dim darken where they overlap, because
     * each is dimmed on its own rather than the pair being composited and then dimmed. Real layer
     * semantics need render-to-texture -- see `10-graphics-layer.md`.
     */
    fun withAlpha(alpha: Float, block: () -> Unit)

    /**
     * Scales everything drawn inside [block] about this node's centre, composing with any enclosing
     * scale.
     *
     * Applied to coordinates as they are emitted rather than handed to the GPU as a per-primitive
     * `DrawTransform`. That transform exists and the shaders honour it, but only four of the nine
     * primitive kinds carry the field -- a gradient, a path or a shadow inside a scaled subtree
     * would silently not scale. Doing it here covers all nine.
     *
     * **Nesting composes multiplicatively**, which is the divergence `ui-core`'s own transform stack
     * documents that it does not have.
     */
    fun withScale(scaleX: Float, scaleY: Float, block: () -> Unit)

    /**
     * Draws one glyph from an atlas cell.
     *
     * Takes UVs rather than a character because the scope knows nothing about fonts -- resolving a
     * character to an atlas cell is the caller's job, and doing it here would put font lookup in the
     * paint path of every primitive.
     *
     * Nine loose floats rather than a rect and a UV pair: this runs once per glyph per frame, and
     * grouping them would allocate two objects per character on every frame.
     */
    @Suppress("LongParameterList")
    fun drawGlyph(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        color: Color,
    )

    /**
     * Fills [path], whose coordinates are node-local like every other call here.
     *
     * Compose's own `DrawScope.drawPath`, and the reason it exists rather than leaving callers on
     * [emit]: every method above maps node-local coordinates into tree space, and [emit] does not.
     * A caller reaching for the escape hatch to draw a vector therefore painted it at the frame's
     * origin instead of its own node -- which is how `shadcnIcon` first rendered, at the top of the
     * screen regardless of where it was placed.
     */
    fun drawPath(path: DrawPath, color: Color)

    /**
     * Strokes [path]'s outline, node-local like every other call here.
     *
     * The stroke sibling of [drawPath]: a decoration that traces a shape's boundary (a border, a
     * ring) belongs here, as one continuous stroked path, rather than being assembled from several
     * filled rects -- a filled strip can't represent a corner arc, only a real stroke can.
     */
    fun drawStrokedPath(path: DrawPath, stroke: DrawStroke, color: Color)

    /**
     * Draws triangles the caller already tessellated, node-local like every other call here.
     *
     * For geometry whose shape outlives the frame that produced it -- an icon, a chart series --
     * where [drawPath] would hand the coalescer the same shape to re-tessellate 60 times a second.
     * The caller owns the mesh and is responsible for rebuilding it when its shape or colours
     * change; see `VectorPainter`, which is the reason this exists.
     */
    fun drawMesh(mesh: ColoredTriangleMesh)

    /**
     * Clips [block] to a node-local path, balancing the clip even if [block] throws.
     *
     * [safeInteriorRect], node-local like [path], is a caller-provided rect guaranteed to lie
     * entirely inside [path] -- e.g. a rounded rectangle's bounds inset by its corner radius.
     * It lets the coalescer skip real polygon clipping for every primitive fully inside it
     * (`DrawRunCoalescer.canSkipExactClip`), which is the majority of content under a shape
     * clip: only primitives actually near the curved/irregular boundary need it. Omitting this
     * (the default) is always correct, just slower -- every primitive under the clip pays for
     * exact clipping against the full path, confirmed by profiling to dominate frame time on a
     * clip that wraps a whole screen.
     */
    fun clippedPath(path: DrawPath, safeInteriorRect: Rectangle? = null, block: () -> Unit)

    /**
     * Emits an already-built primitive, **in tree space**.
     *
     * The escape hatch for anything the helpers do not cover, and the one call here that does *not*
     * map node-local coordinates -- a primitive arrives already positioned. Prefer a helper; reach
     * for this only when no helper covers the primitive, and position it yourself.
     */

    /**
     * Draws a backend texture into this node's bounds.
     *
     * A helper rather than an [emit] at the call site, because a caller cannot position one: the
     * origin and transform this scope maps against are its own, and nothing on this interface
     * exposes them. `emit`'s "position it yourself" is only reachable from inside.
     */
    fun drawTexture(
        material: Any,
        x: Float = 0f,
        y: Float = 0f,
        width: Float = this.width.toFloat(),
        height: Float = this.height.toFloat(),
    )

    fun emit(primitive: UiDrawPrimitive)
}

/** A renderer-neutral offscreen paint pass requested by [Modifier.graphicsLayer]. */
data class GraphicsLayerFrame(
    val id: Int,
    val x: Float,
    val y: Float,
    val width: Int,
    val height: Int,
    val alpha: Float,
    val blurRadiusX: Float = 0f,
    val blurRadiusY: Float = 0f,
    /** Extra transparent pixels around the layer content reserved for an expanding effect. */
    val effectInsetX: Int = 0,
    val effectInsetY: Int = 0,
    val shadowElevation: Float = 0f,
    val primitives: List<UiDrawPrimitive>,
)

/** The texture slot Painter emits until the renderer-aware host resolves it. */
data class GraphicsLayerPlaceholder(val id: Int)

/** Internal bridge used by the graphics-layer draw modifier. */
internal interface LayerDrawScope {
    fun drawLayer(
        alpha: Float,
        scaleX: Float,
        scaleY: Float,
        translationX: Float,
        translationY: Float,
        rotationDegrees: Float,
        blendMode: BlendMode,
        renderEffect: RenderEffect?,
        shadowElevation: Dp,
        shape: Shape,
        drawContent: () -> Unit,
    )

    fun drawPathShadow(
        path: DrawPath,
        color: Color,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        offsetX: Float,
        offsetY: Float,
        blurRadius: Float,
        rotationDegrees: Float = 0f,
    )
}

/**
 * Collects a frame's primitives while walking the placed tree.
 *
 * One instance per paint, reused across every node: the node-local origin is swapped in as the walk
 * descends rather than a scope being allocated per node.
 */
// Seven of the members below are `DrawScope` itself and four are the painter's own hooks, so the
// count is the interface rather than a class doing too much. Splitting it would mean two objects
// sharing one origin/alpha/transform triple, which is worse.
@Suppress("TooManyFunctions")
internal class PaintScope :
    DrawScope,
    LayerDrawScope {
    private val clipStack = ArrayDeque<Rectangle>()
    private val fullClip = Rectangle(-1e9f, -1e9f, 2e9f, 2e9f)
    private var output = mutableListOf<UiDrawPrimitive>()
    private val layers = mutableListOf<GraphicsLayerFrame>()
    private var nextLayerId = 0
    private var originX = 0f
    private var originY = 0f

    override var width: Int = 0
        private set

    override var height: Int = 0
        private set

    override var density: Float = 1f
        private set

    override var layoutDirection: com.awakekt.awake.compose.ui.unit.LayoutDirection =
        com.awakekt.awake.compose.ui.unit.LayoutDirection.Ltr
        private set

    private var alpha = 1f

    private val transform = DrawTransformState()

    fun primitives(): List<UiDrawPrimitive> = output

    fun layers(): List<GraphicsLayerFrame> = layers

    fun reset() {
        output.clear()
        layers.clear()
        clipStack.clear()
        nextLayerId = 0
        alpha = 1f
        transform.reset()
    }

    override fun drawLayer(
        alpha: Float,
        scaleX: Float,
        scaleY: Float,
        translationX: Float,
        translationY: Float,
        rotationDegrees: Float,
        blendMode: BlendMode,
        renderEffect: RenderEffect?,
        shadowElevation: Dp,
        shape: Shape,
        drawContent: () -> Unit,
    ) {
        val layerId = nextLayerId++
        val layerX = originX
        val layerY = originY
        val layerWidth = width
        val layerHeight = height
        val layerAlpha = (this.alpha * alpha).coerceIn(0f, 1f)
        val blur = renderEffect as? RenderEffect.Blur
        val blurRadiusX = (blur?.radiusX?.value ?: 0f) * density
        val blurRadiusY = (blur?.radiusY?.value ?: 0f) * density
        val effectInsetX = kotlin.math.ceil(blurRadiusX).toInt()
        val effectInsetY = kotlin.math.ceil(blurRadiusY).toInt()
        val elevation = shadowElevation.value.coerceAtLeast(0f) * density
        val outline = shape.createOutline(
            com.awakekt.awake.core.math2d.Size2D(layerWidth.toFloat(), layerHeight.toFloat()),
            density,
            layoutDirection,
        )
        val shadowRadius = when (outline) {
            is ShapeOutline.Rectangle -> 0f
            is ShapeOutline.Rounded -> outline.radius
            is ShapeOutline.RoundedCorners,
            is ShapeOutline.Generic,
            -> 0f
        }
        val compositeX = layerX + (1f - scaleX) * layerWidth / 2f + translationX
        val compositeY = layerY + (1f - scaleY) * layerHeight / 2f + translationY
        val parentOutput = output
        val layerOutput = ArrayList<UiDrawPrimitive>()
        output = layerOutput
        val enclosingAlpha = this.alpha
        this.alpha = 1f
        try {
            drawContent()
        } finally {
            this.alpha = enclosingAlpha
            output = parentOutput
        }
        layers += GraphicsLayerFrame(
            id = layerId,
            x = layerX,
            y = layerY,
            width = layerWidth,
            height = layerHeight,
            alpha = layerAlpha,
            blurRadiusX = blurRadiusX,
            blurRadiusY = blurRadiusY,
            effectInsetX = effectInsetX,
            effectInsetY = effectInsetY,
            shadowElevation = elevation,
            primitives = layerOutput.map { it.translatedBy(-layerX, -layerY) },
        )
        if (elevation > 0f) {
            if (outline is ShapeOutline.Generic || outline is ShapeOutline.RoundedCorners) {
                val path = when (outline) {
                    is ShapeOutline.Generic -> outline.path
                    is ShapeOutline.RoundedCorners -> outline.path
                }
                drawPathShadow(
                    path = path,
                    color = Color(0f, 0f, 0f, layerAlpha * 0.25f),
                    x = 0f,
                    y = 0f,
                    width = layerWidth.toFloat(),
                    height = layerHeight.toFloat(),
                    offsetX = 0f,
                    offsetY = elevation / 2f,
                    blurRadius = elevation,
                    rotationDegrees = rotationDegrees,
                )
            } else {
                parentOutput += UiDrawPrimitive.ShadowQuad(
                    x = compositeX,
                    y = compositeY,
                    w = layerWidth * scaleX,
                    h = layerHeight * scaleY,
                    radius = shadowRadius * minOf(scaleX, scaleY),
                    offsetX = 0f,
                    offsetY = elevation / 2f,
                    blurRadius = elevation,
                    spread = 0f,
                    color = Color(0f, 0f, 0f, layerAlpha * 0.25f),
                )
            }
        }
        parentOutput += UiDrawPrimitive.Texture(
            x = compositeX - effectInsetX * scaleX,
            y = compositeY - effectInsetY * scaleY,
            w = (layerWidth + effectInsetX * 2) * scaleX,
            h = (layerHeight + effectInsetY * 2) * scaleY,
            material = GraphicsLayerPlaceholder(layerId),
            alpha = layerAlpha,
            rotationDegrees = rotationDegrees,
            blendMode = blendMode,
            premultiplied = true,
        )
    }

    override fun drawPathShadow(
        path: DrawPath,
        color: Color,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        offsetX: Float,
        offsetY: Float,
        blurRadius: Float,
        rotationDegrees: Float,
    ) {
        val inset = kotlin.math.ceil(blurRadius).toInt().coerceAtLeast(0)
        val layerId = nextLayerId++
        layers += GraphicsLayerFrame(
            id = layerId,
            x = originX + x,
            y = originY + y,
            width = width.toInt().coerceAtLeast(1),
            height = height.toInt().coerceAtLeast(1),
            alpha = 1f,
            blurRadiusX = blurRadius,
            blurRadiusY = blurRadius,
            effectInsetX = inset,
            effectInsetY = inset,
            primitives = listOf(UiDrawPrimitive.FilledPath(path, color.dimmedBy(alpha))),
        )
        output += UiDrawPrimitive.Texture(
            x = transform.mapX(originX + x) - inset * transform.scaleX + offsetX * transform.scaleX,
            y = transform.mapY(originY + y) - inset * transform.scaleY + offsetY * transform.scaleY,
            w = (width + inset * 2) * transform.scaleX,
            h = (height + inset * 2) * transform.scaleY,
            material = GraphicsLayerPlaceholder(layerId),
            rotationDegrees = rotationDegrees,
            premultiplied = true,
        )
    }

    override fun withScale(scaleX: Float, scaleY: Float, block: () -> Unit) {
        // Pivot is the node's own centre, in the untransformed coordinates `enter` resolved -- so a
        // nested scale pivots about where its node actually sits, not about where an outer scale
        // moved it to.
        transform.withScale(scaleX, scaleY, originX + width / 2f, originY + height / 2f, block)
    }

    override fun withAlpha(alpha: Float, block: () -> Unit) {
        val enclosing = this.alpha
        this.alpha = enclosing * alpha.coerceIn(0f, 1f)
        try {
            block()
        } finally {
            // Restored even on a throw, the same reason `clipped` pops its rect in a finally: one
            // escaped dim silently darkens the rest of the frame.
            this.alpha = enclosing
        }
    }

    // Reused: drawBoundsAt writes into it once per draw link per node per frame.
    private val bounds = IntArray(4)

    /**
     * True when [x]/[y]/[w]/[h] (already node-local-to-canvas transformed, same space as
     * [clipStack]'s entries) cannot possibly be visible under the active clip.
     *
     * [clipStack]'s top is the intersected AABB of every enclosing rect clip *and* path clip's
     * bounds -- a superset of the true visible region for a path clip (which can carve away
     * corners inside that box), never a subset. So non-intersection here is a sound trivial
     * reject: nothing that fails this check could have painted a pixel. It says nothing about
     * primitives that DO intersect -- those still need real clipping if a path clip is active.
     *
     * Exists because a `verticalScroll` clips with a plain rect scissor (`clipped`), which -- unlike
     * [safeInteriorRect] skipping the *cost* of an active path clip -- does nothing to stop
     * scrolled-past content from being emitted at all: every primitive between the scissor push
     * and its pop used to reach the coalescer regardless of scroll position, tessellated and
     * clip-tested like anything on screen. A tall scrollable page pays for its off-screen rows
     * every frame, not just its visible ones.
     */
    private fun isFullyOutsideActiveClip(x: Float, y: Float, w: Float, h: Float): Boolean {
        val clip = clipStack.lastOrNull() ?: return false
        return x + w <= clip.x || x >= clip.x + clip.width || y + h <= clip.y || y >= clip.y + clip.height
    }

    fun enter(node: LayoutNode) = enter(node, depth = 0)

    /**
     * Points the scope at what a draw link [depth] layout links deep should paint into.
     *
     * A link inside a `padding` paints the padded box, not the node -- which is what makes
     * `padding().background()` differ from `background().padding()`.
     */
    fun enter(node: LayoutNode, depth: Int) {
        node.drawBoundsAt(depth, bounds)
        originX = (node.absoluteX + bounds[0]).toFloat()
        originY = (node.absoluteY + bounds[1]).toFloat()
        width = bounds[2]
        height = bounds[3]
        density = node.density
        layoutDirection = node.layoutDirection
    }

    override fun drawRect(x: Float, y: Float, width: Float, height: Float, color: Color) {
        val px = transform.mapX(originX + x)
        val py = transform.mapY(originY + y)
        val pw = width * transform.scaleX
        val ph = height * transform.scaleY
        if (isFullyOutsideActiveClip(px, py, pw, ph)) return
        output += UiDrawPrimitive.Quad(px, py, pw, ph, color.dimmedBy(alpha))
    }

    override fun drawTexture(material: Any, x: Float, y: Float, width: Float, height: Float) {
        val px = transform.mapX(originX + x)
        val py = transform.mapY(originY + y)
        val pw = width * transform.scaleX
        val ph = height * transform.scaleY
        if (isFullyOutsideActiveClip(px, py, pw, ph)) return
        output += UiDrawPrimitive.Texture(px, py, pw, ph, material)
    }

    override fun drawRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color,
        radius: Float,
    ) {
        // A zero radius is a plain rect: emitting a RoundedQuad would make the backend run the
        // rounded path for every square background on screen.
        if (radius <= 0f) {
            drawRect(x, y, width, height, color)
            return
        }
        val px = transform.mapX(originX + x)
        val py = transform.mapY(originY + y)
        val pw = width * transform.scaleX
        val ph = height * transform.scaleY
        if (isFullyOutsideActiveClip(px, py, pw, ph)) return
        output += UiDrawPrimitive.RoundedQuad(
            px,
            py,
            pw,
            ph,
            color.dimmedBy(alpha),
            // Scaled by the smaller axis: a radius is a single number, and taking the larger would
            // let a corner arc exceed the shorter side and invert.
            radius * minOf(transform.scaleX, transform.scaleY),
        )
    }

    @Suppress("LongParameterList")
    override fun drawShadow(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color,
        radius: Float,
        offsetX: Float,
        offsetY: Float,
        blurRadius: Float,
        spread: Float,
        gradient: UiLinearGradient?,
    ) {
        val scalarScale = minOf(transform.scaleX, transform.scaleY)
        output += UiDrawPrimitive.ShadowQuad(
            x = transform.mapX(originX + x),
            y = transform.mapY(originY + y),
            w = width * transform.scaleX,
            h = height * transform.scaleY,
            radius = radius * scalarScale,
            offsetX = offsetX * transform.scaleX,
            offsetY = offsetY * transform.scaleY,
            blurRadius = blurRadius * scalarScale,
            spread = spread * scalarScale,
            color = color.dimmedBy(alpha),
            gradient = gradient?.dimmedBy(alpha),
        )
    }

    @Suppress("LongParameterList")
    override fun drawGlyph(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        color: Color,
    ) {
        val px = transform.mapX(originX + x)
        val py = transform.mapY(originY + y)
        val pw = width * transform.scaleX
        val ph = height * transform.scaleY
        if (isFullyOutsideActiveClip(px, py, pw, ph)) return
        output += UiDrawPrimitive.Glyph(px, py, pw, ph, u0, v0, u1, v1, color.dimmedBy(alpha))
    }

    override fun clipped(inset: Float, block: () -> Unit) {
        val rect = Rectangle(
            transform.mapX(originX + inset),
            transform.mapY(originY + inset),
            ((width - inset * 2).coerceAtLeast(0f)) * transform.scaleX,
            ((height - inset * 2).coerceAtLeast(0f)) * transform.scaleY,
        )
        val previous = clipStack.lastOrNull() ?: fullClip
        val effective = rect.intersect(previous)
        clipStack.addLast(effective)
        output += UiDrawPrimitive.ClipPush(effective)
        try {
            block()
        } finally {
            clipStack.removeLast()
            // Restore the enclosing clip, rather than leaving the GPU scissor at this clip.
            output += UiDrawPrimitive.ClipPop(previous)
        }
    }

    /**
     * Dims every colour the primitive carries.
     *
     * [UiDrawPrimitive.Texture] is the one exception: its colour lives inside an opaque
     * `material: Any` that this module cannot interpret. A texture emitted under a dim comes out
     * undimmed, and that is called out in `15-compose-parity.md` rather than left to look like it
     * worked. Clip primitives carry no colour and pass through by definition.
     */
    override fun drawPath(path: DrawPath, color: Color) {
        // The same mapping every other helper applies, expressed once against the path's own
        // transform: node origin first, then the scope's scale.
        val placed = path.transform(
            scaleX = transform.scaleX,
            scaleY = transform.scaleY,
            translateX = transform.mapX(originX),
            translateY = transform.mapY(originY),
        )
        output += UiDrawPrimitive.FilledPath(placed, color.dimmedBy(alpha))
    }

    override fun drawMesh(mesh: ColoredTriangleMesh) {
        // The same mapping drawPath applies, carried beside the mesh instead of baked into it --
        // the caller is holding these triangles across frames, so they must not be rewritten here.
        output += UiDrawPrimitive.Mesh(
            mesh = mesh,
            offsetX = transform.mapX(originX),
            offsetY = transform.mapY(originY),
            scaleX = transform.scaleX,
            scaleY = transform.scaleY,
            alpha = alpha,
        )
    }

    override fun drawStrokedPath(path: DrawPath, stroke: DrawStroke, color: Color) {
        val placed = path.transform(
            scaleX = transform.scaleX,
            scaleY = transform.scaleY,
            translateX = transform.mapX(originX),
            translateY = transform.mapY(originY),
        )
        val scaledStroke = stroke.copy(width = stroke.width * minOf(transform.scaleX, transform.scaleY))
        output += UiDrawPrimitive.StrokedPath(placed, scaledStroke, color.dimmedBy(alpha))
    }

    override fun clippedPath(path: DrawPath, safeInteriorRect: Rectangle?, block: () -> Unit) {
        val tx = transform.mapX(originX)
        val ty = transform.mapY(originY)
        val placed = path.transform(
            scaleX = transform.scaleX,
            scaleY = transform.scaleY,
            translateX = tx,
            translateY = ty,
        )
        val bounds = placed.bounds()
        val previous = clipStack.lastOrNull() ?: fullClip
        val effective = bounds.intersect(previous)
        clipStack.addLast(effective)
        val placedSafeInterior = safeInteriorRect?.let {
            Rectangle(
                x = it.x * transform.scaleX + tx,
                y = it.y * transform.scaleY + ty,
                width = it.width * transform.scaleX,
                height = it.height * transform.scaleY,
            )
        }
        output += UiDrawPrimitive.ClipPathPush(placed, effective, placedSafeInterior)
        try {
            block()
        } finally {
            clipStack.removeLast()
            output += UiDrawPrimitive.ClipPop(previous)
        }
    }

    override fun emit(primitive: UiDrawPrimitive) {
        output += if (alpha >= 1f) primitive else primitive.dimmedBy(alpha)
    }
}

/**
 * Multiplies a colour's own alpha by the live dim, allocating only when there is a dim to apply.
 *
 * The guard is the whole point: nothing is dimmed on a normal frame, and constructing a `Color` per
 * quad per frame regardless cost 2.5 kB/frame -- measured, not guessed.
 */
private fun Color.dimmedBy(alpha: Float): Color =
    if (alpha >= 1f) this else Color(r, g, b, a * alpha)

/** All four stops, so a dimmed gradient keeps its ramp instead of flattening. */
private fun UiLinearGradient.dimmedBy(alpha: Float): UiLinearGradient = UiLinearGradient(
    topLeft = topLeft.dimmedBy(alpha),
    topRight = topRight.dimmedBy(alpha),
    bottomRight = bottomRight.dimmedBy(alpha),
    bottomLeft = bottomLeft.dimmedBy(alpha),
)

private fun UiDrawPrimitive.dimmedBy(alpha: Float): UiDrawPrimitive = when (this) {
    is UiDrawPrimitive.Quad -> copy(color = color.dimmedBy(alpha))
    is UiDrawPrimitive.RoundedQuad -> copy(color = color.dimmedBy(alpha))
    is UiDrawPrimitive.Glyph -> copy(color = color.dimmedBy(alpha))
    is UiDrawPrimitive.FilledPath -> copy(color = color.dimmedBy(alpha))
    is UiDrawPrimitive.StrokedPath -> copy(color = color.dimmedBy(alpha))
    is UiDrawPrimitive.ShadowQuad -> copy(color = color.dimmedBy(alpha))
    is UiDrawPrimitive.GradientQuad -> copy(gradient = gradient.dimmedBy(alpha))
    else -> this
}

/**
 * The accumulated draw transform, as an affine `v -> v * scale + translate` in absolute coordinates.
 *
 * Flat rather than a scale-plus-pivot pair, because scale+pivot cannot represent every composition
 * of two of itself: solving for a combined pivot divides by `1 - s1 * s2`, which is exactly zero
 * whenever a scale and its inverse nest. The flat form has no such hole.
 */
private class DrawTransformState {
    var scaleX = 1f
        private set
    var scaleY = 1f
        private set

    private var translateX = 0f
    private var translateY = 0f

    fun reset() {
        scaleX = 1f
        scaleY = 1f
        translateX = 0f
        translateY = 0f
    }

    fun mapX(x: Float): Float = x * scaleX + translateX

    fun mapY(y: Float): Float = y * scaleY + translateY

    /** Composes [sx]/[sy] about [pivotX]/[pivotY] on top of whatever is already accumulated. */
    fun withScale(sx: Float, sy: Float, pivotX: Float, pivotY: Float, block: () -> Unit) {
        val outerX = scaleX
        val outerY = scaleY
        val outerTx = translateX
        val outerTy = translateY
        scaleX = outerX * sx
        scaleY = outerY * sy
        translateX = pivotX * (1f - sx) * outerX + outerTx
        translateY = pivotY * (1f - sy) * outerY + outerTy
        try {
            block()
        } finally {
            // Restored even on a throw, for the same reason the alpha and clip stacks are: one
            // escaped transform silently moves the rest of the frame.
            scaleX = outerX
            scaleY = outerY
            translateX = outerTx
            translateY = outerTy
        }
    }
}
