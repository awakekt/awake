/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics.drawscope

import com.awakekt.awake.compose.ui.graphics.BlurEffect
import com.awakekt.awake.compose.ui.graphics.RenderEffect
import com.awakekt.awake.compose.ui.graphics.Shape
import com.awakekt.awake.compose.ui.graphics.ShapeOutline
import com.awakekt.awake.compose.ui.graphics.TileMode
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.core.graphics2d.GlyphDecoration
import com.awakekt.awake.core.graphics2d.TextureRegion
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.UiLinearGradient
import com.awakekt.awake.core.graphics2d.backdropFallbackColor
import com.awakekt.awake.core.graphics2d.bounds
import com.awakekt.awake.core.graphics2d.transform
import com.awakekt.awake.core.graphics2d.translatedBy
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.intersect
import com.awakekt.awake.core.text.font.UiFont
import kotlin.math.floor

/** Private bridge for scopes that can preserve geometry the caller guarantees is immutable. */
internal interface RetainedMeshDrawScope {
    fun drawRetainedMeshInternal(mesh: ColoredTriangleMesh, retentionKey: Any)
}

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

    /** The node's own height in pixels, already measured. */
    val height: Int

    /** Pixels per dp: multiply a dp value by this to get the pixel size to draw. */
    val density: Float

    /** The node's layout direction. Defaults to left to right for scopes that do not track one. */
    val layoutDirection: com.awakekt.awake.compose.ui.unit.LayoutDirection
        get() = com.awakekt.awake.compose.ui.unit.LayoutDirection.Ltr

    /**
     * Whether a whole node-local pixel lands on a whole device pixel, so a caller that wants crisp
     * edges may round a position it is about to draw at.
     *
     * Node origins are whole pixels, so this is false only inside [withScale] (or any other
     * transform that is not a whole-pixel translation): there a rounded position would move in
     * steps while the scale animates, and the position should stay fractional so the motion is
     * smooth. Text reads it to put each line's start and baseline on the pixel grid.
     */
    val isDevicePixelAligned: Boolean
        get() = true

    /**
     * Whether an enclosing [withScale] is stretching node-local coordinates away from 1:1 device
     * pixels. Unlike [isDevicePixelAligned], a fractional [translate] at unit scale keeps this false
     * so text can slide its unscaled pixel glyphs smoothly instead of switching to the distance field.
     */
    val isScaled: Boolean
        get() = !isDevicePixelAligned

    /** Fills a rectangle with [color]. Position and size are node-local pixels; the default is the whole node. */
    fun drawRect(
        x: Float = 0f,
        y: Float = 0f,
        width: Float = this.width.toFloat(),
        height: Float = this.height.toFloat(),
        color: Color,
    )

    /**
     * Fills a rectangle with [color], rounding every corner by [radius].
     *
     * Position, size and [radius] are node-local pixels. A [radius] of zero or less draws a plain
     * rectangle.
     */
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
     * Clips everything drawn inside [block] to this box grown by a margin on each side, each side
     * stopping at the node's own bounds: the growth reaches only into what a layout modifier such as
     * `padding` placed between this box and the node's edge, never past it.
     *
     * CSS's `overflow-clip-margin`, for content whose ink spills past the box it is laid out in -- an
     * accent or a leading `j`'s tail past a text box -- where clipping at the box itself would cut it.
     * A margin of [Float.POSITIVE_INFINITY] clips that side at the node's own bounds; 0 clips it at
     * this box, as a side whose content scrolls past it should be.
     *
     * A scope that does not know its node's bounds clips at this box, as [clipped] does.
     */
    fun clippedWithMargin(left: Float, top: Float, right: Float, bottom: Float, block: () -> Unit) {
        clipped(block = block)
    }

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
     * Shifts everything drawn inside [block] by ([left], [top]) node-local pixels, composing with any
     * enclosing scale or translation.
     */
    fun translate(left: Float = 0f, top: Float = 0f, block: () -> Unit) {
        block()
    }

    /**
     * Draws one glyph from an atlas cell of [font].
     *
     * Takes UVs rather than a character because the scope looks nothing up -- resolving a character
     * to an atlas cell is the caller's job, and doing it here would put font lookup in the paint path
     * of every primitive. [font] only says which atlas those UVs index; null draws from the font the
     * frame is drawn with.
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
        font: UiFont? = null,
        page: Int = 0,
    )

    /**
     * [drawGlyph] with an outline and a shadow under it, drawn in the glyph's own draw from its font's effect field
     * (#1061). [decoration]'s rects and lengths are node-local pixels, as [x] and [y] are. A scope that cannot draw a
     * decoration draws the glyph alone.
     */
    @Suppress("LongParameterList")
    fun drawDecoratedGlyph(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        color: Color,
        font: UiFont?,
        page: Int,
        decoration: GlyphDecoration,
    ) = drawGlyph(x, y, width, height, u0, v0, u1, v1, color, font, page)

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
     * Temporarily suspends enclosing scissor clips ([clipped], [clippedWithMargin]) and path clips
     * ([clippedPath]), including canvas masks built on them, within the active render target while
     * running [block], then restores both the clip stack and visibility-culling bounds even if
     * [block] throws.
     *
     * This never escapes an ancestor offscreen render-target layer (`Modifier.graphicsLayer`,
     * `drawLayer`, or a layer-backed effect): primitives drawn inside an offscreen layer remain
     * isolated to and bounded by that layer's target texture.
     */
    fun unclipped(block: () -> Unit) = block()

    /**
     * Draws [region] of a backend texture into this node's bounds, its colour multiplied by
     * [tint] and its texels sampled as [filterQuality] says. [premultiplied] says its colours are
     * already multiplied by its alpha, as a render target's are.
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
        region: TextureRegion = TextureRegion.Whole,
        tint: Color = Color.White,
        filterQuality: FilterQuality = FilterQuality.Low,
        premultiplied: Boolean = false,
    )

    /**
     * Draws [region] of [material] rotated clockwise by [rotationDegrees] around its quad centre
     * and composited with [blendMode]. Leaves the 9-parameter [drawTexture] binary signature
     * untouched while defaulting custom [DrawScope] implementations to an unrotated draw.
     */
    @Suppress("LongParameterList")
    fun drawTexture(
        material: Any,
        x: Float = 0f,
        y: Float = 0f,
        width: Float = this.width.toFloat(),
        height: Float = this.height.toFloat(),
        region: TextureRegion = TextureRegion.Whole,
        tint: Color = Color.White,
        filterQuality: FilterQuality = FilterQuality.Low,
        premultiplied: Boolean = false,
        rotationDegrees: Float,
        blendMode: BlendMode = BlendMode.SourceOver,
    ) = drawTexture(material, x, y, width, height, region, tint, filterQuality, premultiplied)

    /**
     * Draws a backdrop frosted-glass blur beneath this node's layout bounds.
     *
     * Coordinates are node-local. Applications should prefer `Modifier.backdropEffect`, whose
     * shape and density-aware parameters follow Compose's public API.
     *
     * @param x Node-local X coordinate.
     * @param y Node-local Y coordinate.
     * @param width Width in pixels.
     * @param height Height in pixels.
     * @param blurRadius Blur radius in pixels.
     * @param tint Translucent overlay tint color.
     * @param saturation Saturation boost multiplier.
     * @param noise Noise intensity between 0 and 1.
     * @param cornerRadius Uniform corner radius in pixels (when [path] is null).
     * @param smoothing Corner curvature smoothing factor between 0 (circular) and 1 (squircle).
     * @param path Outline path, or null for rectangle / rounded rectangle.
     * @param fallback Optional fallback color when blur cannot be executed.
     */
    @Suppress("LongParameterList")
    fun drawBackdropBlur(
        x: Float = 0f,
        y: Float = 0f,
        width: Float = this.width.toFloat(),
        height: Float = this.height.toFloat(),
        blurRadius: Float,
        tint: Color = Color.Transparent,
        saturation: Float = 1f,
        noise: Float = 0f,
        cornerRadius: Float = 0f,
        smoothing: Float = 0f,
        path: DrawPath? = null,
        fallback: Color? = null,
    )

    /**
     * Emits an already-built primitive, **in tree space**.
     *
     * The escape hatch for anything the helpers do not cover, and the one call here that does *not*
     * map node-local coordinates -- a primitive arrives already positioned. Prefer a helper; reach
     * for this only when no helper covers the primitive, and position it yourself.
     */
    fun emit(primitive: UiDrawPrimitive)
}

/**
 * Draws immutable, already-tessellated [mesh] using [retentionKey] to identify its geometry
 * across frames. Replace the key whenever the mesh's vertices or indices change. Renderers that
 * support retained UI runs can then reuse the staged geometry without hashing every vertex.
 */
fun DrawScope.drawRetainedMesh(mesh: ColoredTriangleMesh, retentionKey: Any) {
    val retainedScope = this as? RetainedMeshDrawScope
    if (retainedScope != null) {
        retainedScope.drawRetainedMeshInternal(mesh, retentionKey)
    } else {
        drawMesh(mesh)
    }
}

/**
 * A renderer-neutral offscreen paint pass requested by [Modifier.graphicsLayer].
 *
 * @property id Identifies the layer's texture slot, matching a [GraphicsLayerPlaceholder] in the parent.
 * @property x Tree-space x of the layer's top-left corner, in pixels.
 * @property y Tree-space y of the layer's top-left corner, in pixels.
 * @property width Width of the layer content in pixels, not counting the effect inset.
 * @property height Height of the layer content in pixels, not counting the effect inset.
 * @property alpha Opacity applied when the layer is composited, from 0 to 1.
 * @property blurRadiusX Horizontal blur radius in pixels, or 0 for none.
 * @property blurRadiusY Vertical blur radius in pixels, or 0 for none.
 * @property effectInsetX Pixels reserved left and right of the node's box, for expanding effects and for
 *   content that paints past the node (a glyph's ink past its text box), whichever reaches further.
 * @property effectInsetY Pixels reserved above and below the node's box, as [effectInsetX] is beside it.
 * @property shadowElevation Elevation shadow size in pixels, or 0 for none.
 * @property primitives What the layer paints, positioned relative to its top-left corner.
 * @property renderEffect Optional effect applied to the layer texture.
 * @property tileMode Strategy used when sampling outside layer bounds.
 */
data class GraphicsLayerFrame(
    val id: Int,
    val x: Float,
    val y: Float,
    val width: Int,
    val height: Int,
    val alpha: Float,
    val blurRadiusX: Float = 0f,
    val blurRadiusY: Float = 0f,
    val effectInsetX: Int = 0,
    val effectInsetY: Int = 0,
    val shadowElevation: Float = 0f,
    val primitives: List<UiDrawPrimitive>,
    val renderEffect: RenderEffect? = null,
    val tileMode: TileMode = TileMode.Clamp,
)

/**
 * The texture slot Painter emits until the renderer-aware host resolves it.
 *
 * @property id The [GraphicsLayerFrame.id] of the layer this placeholder stands in for.
 */
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
    LayerDrawScope,
    RetainedMeshDrawScope {
    private val clips = ClipStackState()
    private var output = mutableListOf<UiDrawPrimitive>()
    private val layers = mutableListOf<GraphicsLayerFrame>()
    private var nextLayerId = 0
    private var originX = 0f
    private var originY = 0f

    // The whole node the current draw link belongs to, in tree space: where clippedWithMargin stops.
    private var nodeX = 0f
    private var nodeY = 0f
    private var nodeWidth = 0f
    private var nodeHeight = 0f

    override var width: Int = 0
        private set

    override var height: Int = 0
        private set

    override var density: Float = 1f
        private set

    override var layoutDirection: com.awakekt.awake.compose.ui.unit.LayoutDirection =
        com.awakekt.awake.compose.ui.unit.LayoutDirection.Ltr
        private set

    override val isDevicePixelAligned: Boolean
        get() = transform.isWholePixelTranslation

    override val isScaled: Boolean
        get() = transform.scaleX != 1f || transform.scaleY != 1f

    private var alpha = 1f

    private val transform = DrawTransformState()

    fun primitives(): List<UiDrawPrimitive> = output

    fun layers(): List<GraphicsLayerFrame> = layers

    fun reset() {
        output.clear()
        layers.clear()
        clips.reset()
        nextLayerId = 0
        alpha = 1f
        transform.reset()
    }

    @Suppress("DEPRECATION", "LongMethod")
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
        val blur = resolveLayerBlur(renderEffect, density)
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
        val compositeScaleX = scaleX * transform.scaleX
        val compositeScaleY = scaleY * transform.scaleY
        val compositeX = transform.mapX(layerX + (1f - scaleX) * layerWidth / 2f + translationX)
        val compositeY = transform.mapY(layerY + (1f - scaleY) * layerHeight / 2f + translationY)
        val parentOutput = output
        val layerOutput = recordLayerPrimitives(drawContent)
        // The target grows to what the content paints, not just the node: Compose's graphicsLayer
        // does not clip, and a target the node's size cut every glyph whose ink passes its text box
        // as soon as a fade or a press scale started. Grown evenly on both sides, so the composite's
        // centre, which rotation and scale turn about, stays the node's. A clamped blur keeps its
        // edge at the node, as its tile mode says. When the composite quad lands between pixels or
        // resamples under scale or rotation, a one-pixel transparent border keeps CLAMP_TO_EDGE from
        // smearing ink that touches the target's edge.
        measureOverflow(
            layerOutput,
            Rectangle(layerX, layerY, layerWidth.toFloat(), layerHeight.toFloat()),
            renderEffect,
            blur.tileMode,
        )
        val allowsEdgePad = renderEffect == null || blur.tileMode == TileMode.Decal
        val resampleInsetX = resampleEdgeInset(allowsEdgePad, compositeX, layerWidth * compositeScaleX)
        val resampleInsetY = resampleEdgeInset(allowsEdgePad, compositeY, layerHeight * compositeScaleY)
        val effectInsetX = maxOf(blur.insetX, resampleInsetX, overflowInset(maxOf(overflow.left, overflow.right), layerWidth))
        val effectInsetY = maxOf(blur.insetY, resampleInsetY, overflowInset(maxOf(overflow.top, overflow.bottom), layerHeight))
        layers += GraphicsLayerFrame(
            id = layerId,
            x = layerX,
            y = layerY,
            width = layerWidth,
            height = layerHeight,
            alpha = layerAlpha,
            blurRadiusX = blur.radiusX,
            blurRadiusY = blur.radiusY,
            effectInsetX = effectInsetX,
            effectInsetY = effectInsetY,
            shadowElevation = elevation,
            primitives = layerOutput.map { it.translatedBy(-layerX, -layerY) },
            renderEffect = renderEffect,
            tileMode = blur.tileMode,
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
                    w = layerWidth * compositeScaleX,
                    h = layerHeight * compositeScaleY,
                    radius = shadowRadius * minOf(compositeScaleX, compositeScaleY),
                    offsetX = 0f,
                    offsetY = elevation / 2f,
                    blurRadius = elevation,
                    spread = 0f,
                    color = Color(0f, 0f, 0f, layerAlpha * 0.25f),
                )
            }
        }
        parentOutput += UiDrawPrimitive.Texture(
            x = compositeX - effectInsetX * compositeScaleX,
            y = compositeY - effectInsetY * compositeScaleY,
            w = (layerWidth + effectInsetX * 2) * compositeScaleX,
            h = (layerHeight + effectInsetY * 2) * compositeScaleY,
            material = GraphicsLayerPlaceholder(layerId),
            alpha = layerAlpha,
            rotationDegrees = rotationDegrees,
            blendMode = blendMode,
            premultiplied = true,
        )
    }

    private fun recordLayerPrimitives(drawContent: () -> Unit): ArrayList<UiDrawPrimitive> {
        val parentOutput = output
        val layerOutput = ArrayList<UiDrawPrimitive>()
        output = layerOutput
        val enclosingAlpha = this.alpha
        this.alpha = 1f
        try {
            clips.withIsolatedLayerClips { transform.withIdentity(drawContent) }
        } finally {
            this.alpha = enclosingAlpha
            output = parentOutput
        }
        return layerOutput
    }

    // Reused for every layer: what its content paints past its node.
    private val overflow = ContentOverflow()

    /**
     * Measures what [content] paints past the layer's [box] into [overflow]. A clamped blur
     * ([renderEffect] with [tileMode] other than decal) keeps its edge at the node, and measures nothing.
     */
    private fun measureOverflow(content: List<UiDrawPrimitive>, box: Rectangle, renderEffect: RenderEffect?, tileMode: TileMode) {
        if (renderEffect == null || tileMode == TileMode.Decal) {
            overflow.measure(content, box.x, box.y, box.width, box.height)
        } else {
            overflow.reset()
        }
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
            renderEffect = RenderEffect.createBlurEffect(blurRadius, blurRadius, TileMode.Decal),
            tileMode = TileMode.Decal,
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

    override fun translate(left: Float, top: Float, block: () -> Unit) {
        transform.withTranslation(left, top, block)
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

    private fun isFullyOutsideActiveClip(x: Float, y: Float, w: Float, h: Float): Boolean =
        clips.isFullyOutside(x, y, w, h)

    fun enter(node: LayoutNode) = enter(node, depth = 0)

    /**
     * Points the scope at what a draw link [depth] layout links deep should paint into.
     *
     * A link inside a `padding` paints the padded box, not the node -- which is what makes
     * `padding().background()` differ from `background().padding()`.
     */
    fun enter(node: LayoutNode, depth: Int) {
        node.drawBoundsAt(depth, bounds)
        nodeX = node.absoluteX.toFloat()
        nodeY = node.absoluteY.toFloat()
        nodeWidth = node.width.toFloat()
        nodeHeight = node.height.toFloat()
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

    override fun drawTexture(
        material: Any,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        region: TextureRegion,
        tint: Color,
        filterQuality: FilterQuality,
        premultiplied: Boolean,
    ) {
        drawTexture(
            material = material,
            x = x,
            y = y,
            width = width,
            height = height,
            region = region,
            tint = tint,
            filterQuality = filterQuality,
            premultiplied = premultiplied,
            rotationDegrees = 0f,
            blendMode = BlendMode.SourceOver,
        )
    }

    @Suppress("LongParameterList")
    override fun drawTexture(
        material: Any,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        region: TextureRegion,
        tint: Color,
        filterQuality: FilterQuality,
        premultiplied: Boolean,
        rotationDegrees: Float,
        blendMode: BlendMode,
    ) {
        val px = transform.mapX(originX + x)
        val py = transform.mapY(originY + y)
        val pw = width * transform.scaleX
        val ph = height * transform.scaleY
        if (clips.isRotatedQuadOutside(px, py, pw, ph, rotationDegrees)) return
        output += UiDrawPrimitive.Texture(
            x = px,
            y = py,
            w = pw,
            h = ph,
            material = material,
            alpha = alpha,
            rotationDegrees = rotationDegrees,
            blendMode = blendMode,
            premultiplied = premultiplied,
            region = region,
            tint = tint,
            filterQuality = filterQuality,
        )
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
    override fun drawBackdropBlur(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        blurRadius: Float,
        tint: Color,
        saturation: Float,
        noise: Float,
        cornerRadius: Float,
        smoothing: Float,
        path: DrawPath?,
        fallback: Color?,
    ) {
        val px = transform.mapX(originX + x)
        val py = transform.mapY(originY + y)
        val pw = width * transform.scaleX
        val ph = height * transform.scaleY
        if (isFullyOutsideActiveClip(px, py, pw, ph)) return
        val scalarScale = minOf(transform.scaleX, transform.scaleY)
        val placedPath = path?.transform(
            scaleX = transform.scaleX,
            scaleY = transform.scaleY,
            translateX = px,
            translateY = py,
        )
        val resolvedFallback = fallback ?: backdropFallbackColor(tint)
        output += UiDrawPrimitive.BackdropBlur(
            x = px,
            y = py,
            w = pw,
            h = ph,
            blurRadius = blurRadius * scalarScale,
            tint = tint,
            saturation = saturation,
            noise = noise,
            cornerRadius = cornerRadius * scalarScale,
            smoothing = smoothing,
            path = placedPath,
            alpha = alpha,
            fallback = resolvedFallback,
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
        font: UiFont?,
        page: Int,
    ) {
        val px = transform.mapX(originX + x)
        val py = transform.mapY(originY + y)
        val pw = width * transform.scaleX
        val ph = height * transform.scaleY
        if (isFullyOutsideActiveClip(px, py, pw, ph)) return
        output += UiDrawPrimitive.Glyph(px, py, pw, ph, u0, v0, u1, v1, color.dimmedBy(alpha), font = font, page = page)
    }

    override fun drawDecoratedGlyph(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        color: Color,
        font: UiFont?,
        page: Int,
        decoration: GlyphDecoration,
    ) {
        val px = transform.mapX(originX + x)
        val py = transform.mapY(originY + y)
        val pw = width * transform.scaleX
        val ph = height * transform.scaleY
        val mapped = decoration.scaled(transform.scaleX, transform.scaleY)
        // Culled by what it draws, its quad grown by the decoration's reach, not by the glyph alone.
        val reach = mapped.reach
        if (isFullyOutsideActiveClip(px - reach, py - reach, pw + 2 * reach, ph + 2 * reach)) return
        output += UiDrawPrimitive.Glyph(
            px, py, pw, ph, u0, v0, u1, v1, color.dimmedBy(alpha),
            font = font,
            page = page,
            decoration = mapped.dimmedBy(alpha),
        )
    }

    override fun clipped(inset: Float, block: () -> Unit) {
        clipTo(
            originX + inset,
            originY + inset,
            (width - inset * 2).coerceAtLeast(0f),
            (height - inset * 2).coerceAtLeast(0f),
            block,
        )
    }

    override fun clippedWithMargin(left: Float, top: Float, right: Float, bottom: Float, block: () -> Unit) {
        val x0 = originX - left.coerceIn(0f, (originX - nodeX).coerceAtLeast(0f))
        val y0 = originY - top.coerceIn(0f, (originY - nodeY).coerceAtLeast(0f))
        val x1 = originX + width + right.coerceIn(0f, (nodeX + nodeWidth - originX - width).coerceAtLeast(0f))
        val y1 = originY + height + bottom.coerceIn(0f, (nodeY + nodeHeight - originY - height).coerceAtLeast(0f))
        clipTo(x0, y0, x1 - x0, y1 - y0, block)
    }

    /** Clips [block] to a tree-space rect, before this scope's transform maps it. */
    private fun clipTo(x: Float, y: Float, w: Float, h: Float, block: () -> Unit) {
        val rect = Rectangle(
            transform.mapX(x),
            transform.mapY(y),
            w * transform.scaleX,
            h * transform.scaleY,
        )
        clips.pushRect(rect, output, block)
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
        emitMesh(mesh, retentionKey = null)
    }

    override fun drawRetainedMeshInternal(mesh: ColoredTriangleMesh, retentionKey: Any) {
        emitMesh(mesh, retentionKey)
    }

    private fun emitMesh(mesh: ColoredTriangleMesh, retentionKey: Any?) {
        // The same mapping drawPath applies, carried beside the mesh instead of baked into it --
        // the caller is holding these triangles across frames, so they must not be rewritten here.
        val primitive = UiDrawPrimitive.Mesh(
            mesh = mesh,
            offsetX = transform.mapX(originX),
            offsetY = transform.mapY(originY),
            scaleX = transform.scaleX,
            scaleY = transform.scaleY,
            alpha = alpha,
        )
        primitive.retainedGeometryKey = retentionKey
        output += primitive
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
        val placedSafeInterior = safeInteriorRect?.let {
            Rectangle(
                x = it.x * transform.scaleX + tx,
                y = it.y * transform.scaleY + ty,
                width = it.width * transform.scaleX,
                height = it.height * transform.scaleY,
            )
        }
        clips.pushPath(placed, placedSafeInterior, output, block)
    }

    override fun unclipped(block: () -> Unit) {
        clips.unclipped(output, block)
    }

    override fun emit(primitive: UiDrawPrimitive) {
        output += if (alpha >= 1f) primitive else primitive.dimmedBy(alpha)
    }
}

private data class LayerBlurSpec(
    val radiusX: Float,
    val radiusY: Float,
    val tileMode: TileMode,
    val insetX: Int,
    val insetY: Int,
)

@Suppress("DEPRECATION")
private fun resolveLayerBlur(renderEffect: RenderEffect?, density: Float): LayerBlurSpec {
    val radiusX: Float
    val radiusY: Float
    val tileMode: TileMode
    when (renderEffect) {
        is BlurEffect -> {
            radiusX = renderEffect.radiusX
            radiusY = renderEffect.radiusY
            tileMode = renderEffect.edgeTreatment
        }
        is RenderEffect.Blur -> {
            radiusX = renderEffect.radiusX.value * density
            radiusY = renderEffect.radiusY.value * density
            tileMode = TileMode.Decal
        }
        else -> {
            radiusX = 0f
            radiusY = 0f
            tileMode = TileMode.Clamp
        }
    }
    val insetX = if (tileMode == TileMode.Decal) kotlin.math.ceil(radiusX).toInt() else 0
    val insetY = if (tileMode == TileMode.Decal) kotlin.math.ceil(radiusY).toInt() else 0
    return LayerBlurSpec(radiusX, radiusY, tileMode, insetX, insetY)
}

private fun resampleEdgeInset(allowsEdgePad: Boolean, start: Float, span: Float): Int =
    if (!allowsEdgePad) {
        0
    } else {
        val end = start + span
        if (start != floor(start) || end != floor(end)) 1 else 0
    }

/**
 * Whole pixels to reserve either side of a [size]-pixel layer for content painted [reach] past it,
 * never so many that the target passes [MAX_LAYER_TARGET_SIZE].
 */
private fun overflowInset(reach: Float, size: Int): Int =
    kotlin.math.ceil(reach).toInt().coerceIn(0, ((MAX_LAYER_TARGET_SIZE - size) / 2).coerceAtLeast(0))

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
    is UiDrawPrimitive.Glyph -> copy(color = color.dimmedBy(alpha), decoration = decoration?.dimmedBy(alpha))
    is UiDrawPrimitive.FilledPath -> copy(color = color.dimmedBy(alpha))
    is UiDrawPrimitive.StrokedPath -> copy(color = color.dimmedBy(alpha))
    is UiDrawPrimitive.ShadowQuad -> copy(color = color.dimmedBy(alpha))
    is UiDrawPrimitive.GradientQuad -> copy(gradient = gradient.dimmedBy(alpha))
    is UiDrawPrimitive.Mesh -> copy(alpha = this.alpha * alpha)
    is UiDrawPrimitive.BackdropBlur -> copy(alpha = this.alpha * alpha)
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

    /** True when the transform only moves things by whole pixels, so nothing is resampled. */
    val isWholePixelTranslation: Boolean
        get() = scaleX == 1f && scaleY == 1f && translateX == floor(translateX) && translateY == floor(translateY)

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

    /** Shifts node-local coordinates by [dx]/[dy], scaled by whatever scale is already accumulated. */
    fun withTranslation(dx: Float, dy: Float, block: () -> Unit) {
        if (dx == 0f && dy == 0f) {
            block()
            return
        }
        val outerTx = translateX
        val outerTy = translateY
        translateX = outerTx + dx * scaleX
        translateY = outerTy + dy * scaleY
        try {
            block()
        } finally {
            translateX = outerTx
            translateY = outerTy
        }
    }

    private val isUnscaled: Boolean
        get() = scaleX == 1f && scaleY == 1f

    private val isUntranslated: Boolean
        get() = translateX == 0f && translateY == 0f

    /** Suspends any enclosing scale or translation while recording an offscreen layer's own pass. */
    fun withIdentity(block: () -> Unit) {
        if (isUnscaled && isUntranslated) {
            block()
            return
        }
        val outerX = scaleX
        val outerY = scaleY
        val outerTx = translateX
        val outerTy = translateY
        scaleX = 1f
        scaleY = 1f
        translateX = 0f
        translateY = 0f
        try {
            block()
        } finally {
            scaleX = outerX
            scaleY = outerY
            translateX = outerTx
            translateY = outerTy
        }
    }
}

/**
 * Tracks active scissor and path clips, their intersected visibility-culling AABBs, temporary
 * `unclipped {}` escapes within the current render target, and per-layer clip isolation.
 */
private class ClipStackState {
    private val clipStack = ArrayDeque<Rectangle>()
    private val clipCommandStack = ArrayDeque<UiDrawPrimitive>()

    // The stacks unclipped() sets aside, one pair per nesting depth, kept for reuse so a call makes no lists.
    private val savedBoundsByDepth = ArrayList<ArrayList<Rectangle>>()
    private val savedCommandsByDepth = ArrayList<ArrayList<UiDrawPrimitive>>()
    private var unclippedDepth = 0
    private val fullClip = Rectangle(-1e9f, -1e9f, 2e9f, 2e9f)

    fun reset() {
        clipStack.clear()
        clipCommandStack.clear()
    }

    fun isFullyOutside(x: Float, y: Float, w: Float, h: Float): Boolean {
        val clip = clipStack.lastOrNull() ?: return false
        return x + w <= clip.x || x >= clip.x + clip.width || y + h <= clip.y || y >= clip.y + clip.height
    }

    fun isRotatedQuadOutside(px: Float, py: Float, pw: Float, ph: Float, rotationDegrees: Float): Boolean {
        if (rotationDegrees == 0f) return isFullyOutside(px, py, pw, ph)
        val radians = rotationDegrees * (kotlin.math.PI.toFloat() / HALF_CIRCLE_DEGREES)
        val cosA = kotlin.math.abs(kotlin.math.cos(radians))
        val sinA = kotlin.math.abs(kotlin.math.sin(radians))
        val aabbW = pw * cosA + ph * sinA
        val aabbH = pw * sinA + ph * cosA
        val cx = px + pw * HALF_EXTENT
        val cy = py + ph * HALF_EXTENT
        return isFullyOutside(cx - aabbW * HALF_EXTENT, cy - aabbH * HALF_EXTENT, aabbW, aabbH)
    }

    fun withIsolatedLayerClips(block: () -> Unit) {
        val enclosingClips = if (clipStack.isEmpty()) null else ArrayList(clipStack).also { clipStack.clear() }
        val enclosingCommands = if (clipCommandStack.isEmpty()) null else ArrayList(clipCommandStack).also { clipCommandStack.clear() }
        try {
            block()
        } finally {
            clipStack.clear()
            if (enclosingClips != null) clipStack.addAll(enclosingClips)
            clipCommandStack.clear()
            if (enclosingCommands != null) clipCommandStack.addAll(enclosingCommands)
        }
    }

    fun pushRect(rect: Rectangle, output: MutableList<UiDrawPrimitive>, block: () -> Unit) {
        val previous = clipStack.lastOrNull() ?: fullClip
        val effective = rect.intersect(previous)
        val push = UiDrawPrimitive.ClipPush(effective)
        clipStack.addLast(effective)
        clipCommandStack.addLast(push)
        output += push
        try {
            block()
        } finally {
            clipCommandStack.removeLast()
            clipStack.removeLast()
            output += UiDrawPrimitive.ClipPop(previous)
        }
    }

    fun pushPath(
        placed: DrawPath,
        placedSafeInterior: Rectangle?,
        output: MutableList<UiDrawPrimitive>,
        block: () -> Unit,
    ) {
        val previous = clipStack.lastOrNull() ?: fullClip
        val effective = placed.bounds().intersect(previous)
        val push = UiDrawPrimitive.ClipPathPush(placed, effective, placedSafeInterior)
        clipStack.addLast(effective)
        clipCommandStack.addLast(push)
        output += push
        try {
            block()
        } finally {
            clipCommandStack.removeLast()
            clipStack.removeLast()
            output += UiDrawPrimitive.ClipPop(previous)
        }
    }

    fun unclipped(output: MutableList<UiDrawPrimitive>, block: () -> Unit) {
        if (clipStack.isEmpty()) {
            block()
            return
        }
        val depth = unclippedDepth
        if (depth == savedBoundsByDepth.size) {
            savedBoundsByDepth.add(ArrayList())
            savedCommandsByDepth.add(ArrayList())
        }
        val savedBounds = savedBoundsByDepth[depth]
        val savedCommands = savedCommandsByDepth[depth]
        savedBounds.clear()
        savedCommands.clear()
        // Indexed copies: addAll would make an array or an iterator.
        for (i in 0 until clipStack.size) savedBounds.add(clipStack[i])
        for (i in 0 until clipCommandStack.size) savedCommands.add(clipCommandStack[i])
        for (i in savedBounds.lastIndex downTo 0) {
            val restore = if (i > 0) savedBounds[i - 1] else fullClip
            output += UiDrawPrimitive.ClipPop(restore)
        }
        clipStack.clear()
        clipCommandStack.clear()
        unclippedDepth = depth + 1
        try {
            block()
        } finally {
            unclippedDepth = depth
            clipStack.clear()
            for (i in 0 until savedBounds.size) clipStack.addLast(savedBounds[i])
            clipCommandStack.clear()
            for (i in 0 until savedCommands.size) {
                clipCommandStack.addLast(savedCommands[i])
                output += savedCommands[i]
            }
            savedBounds.clear()
            savedCommands.clear()
        }
    }
}

/**
 * The largest side a layer's render target grows to for overflowing content: WebGPU's default texture
 * size limit, the smallest a backend here guarantees.
 */
private const val MAX_LAYER_TARGET_SIZE = 8192
private const val HALF_CIRCLE_DEGREES = 180f
private const val HALF_EXTENT = 0.5f
