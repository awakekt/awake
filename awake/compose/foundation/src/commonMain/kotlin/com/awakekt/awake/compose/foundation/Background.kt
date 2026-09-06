/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.graphics.RectangleShape
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.graphics.Shape
import com.awakekt.awake.compose.ui.graphics.ShapeOutline
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.StrokeJoin
import com.awakekt.awake.core.graphics2d.drawPath
import com.awakekt.awake.core.graphics2d.tessellateStrokeAa
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.graphics2d.transform
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Size2D
import kotlin.math.min
import com.awakekt.awake.core.math2d.Dp as StrokeWidth

/**
 * Primitive visual modifiers -- colours and shapes, no tokens and no state rules.
 *
 * `Style`, variants and theme tokens stay in `ui-shadcn`, which resolves them *into* these.
 * Real Compose splits the same way: `Modifier.background`/`border` here, Material's opinions
 * elsewhere. See `docs/reference/compose-engine/04-styling-theme.md`.
 */
fun Modifier.background(color: Color, shape: Shape = RectangleShape): Modifier =
    this then BackgroundElement(color, shape)

/** Compatibility spelling for the former uniform-radius API. */
fun Modifier.background(color: Color, cornerRadius: Dp): Modifier =
    background(color, RoundedCornerShape(cornerRadius))

/**
 * Draws a border whose solid band sits *inside* the node's bounds.
 *
 * Inside, not centred on the edge: an outside-drawn border would overflow the space the parent
 * measured for this node, so a bordered child would overlap its sibling. The ring's centreline is
 * therefore inset by half the stroke width, putting the opaque band flush against the node's edge
 * and no further.
 *
 * **The anti-aliased edge does extend past it**, by `min(AA_FRINGE_PX, strokeWidth / 2)` -- half a
 * pixel for a 1dp border, one for anything wider. That rim is transparent at its outer edge and is
 * simply what an anti-aliased boundary is; `tessellateStrokeAa` places the whole fringe outside the
 * solid core deliberately, so a one-pixel border keeps a fully opaque pixel instead of dissolving
 * into its own coverage. A caller that needs a hard guarantee of nothing painted outside the node
 * wants a clip, not a narrower border.
 */
fun Modifier.border(
    width: Dp,
    color: Color,
    shape: Shape = RectangleShape,
    sides: BorderSides = BorderSides.All,
): Modifier = this then BorderElement(width, color, shape, sides)

/** Compatibility spelling for the former uniform-radius API. */
fun Modifier.border(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
    sides: BorderSides = BorderSides.All,
): Modifier = border(width, color, RoundedCornerShape(cornerRadius), sides)

/** The edges a [border] paints. Kept independent from any component or layout direction. */
data class BorderSides(
    val top: Boolean = true,
    val end: Boolean = true,
    val bottom: Boolean = true,
    val start: Boolean = true,
) {
    val isAll: Boolean get() = top && end && bottom && start

    companion object {
        val All = BorderSides()
        val None = BorderSides(false, false, false, false)
    }
}

private class BackgroundElement(
    private val color: Color,
    private val shape: Shape,
) : ModifierNodeElement<BackgroundNode>() {
    override fun create(): BackgroundNode = BackgroundNode()
    override fun update(node: BackgroundNode) {
        node.color = color
        node.shape = shape
    }
    override fun toString(): String = "background($color, shape=$shape)"
}

private class BackgroundNode :
    Modifier.Node(),
    DrawModifierNode {
    lateinit var color: Color
    lateinit var shape: Shape
    override fun DrawScope.draw(drawContent: () -> Unit) {
        when (val outline = shape.createOutline(Size2D(width.toFloat(), height.toFloat()), density)) {
            is ShapeOutline.Rectangle -> drawRect(color = color)
            is ShapeOutline.Rounded -> drawRoundedRect(color = color, radius = outline.radius)
            is ShapeOutline.Generic -> drawPath(outline.path, color)
        }
        drawContent()
    }

    override fun toString(): String = "background($color, shape=$shape)"
}

private class BorderElement(
    private val strokeWidth: Dp,
    private val color: Color,
    private val shape: Shape,
    private val sides: BorderSides,
) : ModifierNodeElement<BorderNode>() {
    override fun create(): BorderNode = BorderNode()
    override fun update(node: BorderNode) {
        node.strokeWidth = strokeWidth
        node.color = color
        node.shape = shape
        node.sides = sides
    }
    override fun toString(): String = "border($strokeWidth, $color, shape=$shape, sides=$sides)"
}

private class BorderNode :
    Modifier.Node(),
    DrawModifierNode {
    var strokeWidth: Dp = 0.dp
    lateinit var color: Color
    lateinit var shape: Shape
    var sides: BorderSides = BorderSides.All

    // Building a border outline costs what tessellating a whole icon costs: an offset ring per
    // side, a round join per arc segment, then an anti-aliased fringe over all of it. It changes
    // only when this node's size or paint does, so it is built then rather than every frame --
    // the same reason Compose's own BorderModifierNode caches through CacheDrawModifierNode. Held
    // on the node, which outlives the frame; see LayoutNode's retained modifier chain.
    private var cachedKey: BorderKey? = null
    private var cachedMesh: ColoredTriangleMesh? = null

    override fun DrawScope.draw(drawContent: () -> Unit) {
        drawContent()
        val stroke = strokeWidth.value * density
        if (stroke <= 0f || (!sides.top && !sides.end && !sides.bottom && !sides.start)) return
        val key = BorderKey(width, height, stroke, color, shape, sides, density)
        cachedMesh?.let { if (cachedKey == key) return drawMesh(it) }
        // Centred on a box inset by half the stroke width, so the ring's solid band lands inside
        // this node's bounds -- the same "inside, not centred on the edge" contract the old
        // edge-strip draw had, but as one continuous stroked outline instead of four independent
        // rects that could never represent a shared corner arc. Its anti-aliased rim sits outside
        // that band; see the doc on Modifier.border.
        val inset = stroke / 2f
        val bounds = Rectangle(
            inset,
            inset,
            (width - stroke).coerceAtLeast(0f),
            (height - stroke).coerceAtLeast(0f),
        )
        // Tessellated node-local, so moving the node reuses this rather than rebuilding it.
        val mesh = borderOutlinePath(bounds, shape, sides, density, inset).tessellateStrokeAa(
            DrawStroke(
                width = StrokeWidth(stroke),
                // Rounded outlines are already flattened into arc segments. Round joins prevent
                // miter spikes at those approximation points.
                join = if (shape is RoundedCornerShape) StrokeJoin.Round else StrokeJoin.Miter,
            ),
            color,
        )
        cachedKey = key
        cachedMesh = mesh
        drawMesh(mesh)
    }

    override fun toString(): String = "border($strokeWidth, $color, shape=$shape, sides=$sides)"
}

/**
 * The centreline a border traces: the whole perimeter, or only the sides that were asked for.
 *
 * Split out of [BorderNode] so the node's draw is "check the cache, build, hand over triangles"
 * and the shape decisions live in one place beside [partialBorderPath], which is the same kind of
 * work for the partial case.
 */
private fun borderOutlinePath(
    bounds: Rectangle,
    shape: Shape,
    sides: BorderSides,
    density: Float,
    inset: Float,
): com.awakekt.awake.core.graphics2d.DrawPath {
    if (!sides.isAll) return partialBorderPath(bounds, shape, sides, density, inset)
    val outline = when (shape) {
        is RoundedCornerShape -> shape.createOutline(bounds, density, radiusInset = inset)
        else -> shape.createOutline(Size2D(bounds.width, bounds.height), density)
    }
    return when (outline) {
        is ShapeOutline.Rectangle -> com.awakekt.awake.core.graphics2d.DrawShape.Rectangle.toPath(bounds)
        is ShapeOutline.Rounded -> com.awakekt.awake.core.graphics2d.DrawShape.RoundedRectangle(outline.radius.dp)
            .toPath(bounds)
        is ShapeOutline.Generic -> if (shape is RoundedCornerShape) {
            outline.path
        } else {
            outline.path.transform(translateX = bounds.x, translateY = bounds.y)
        }
    }
}

/** Everything the cached border mesh is built from -- change any of it and the mesh is rebuilt. */
private data class BorderKey(
    val width: Int,
    val height: Int,
    val stroke: Float,
    val color: Color,
    val shape: Shape,
    val sides: BorderSides,
    val density: Float,
)

internal fun partialBorderPath(
    bounds: Rectangle,
    shape: Shape,
    sides: BorderSides,
    density: Float,
    inset: Float,
) = drawPath {
    val radii = when (shape) {
        RectangleShape -> CornerRadii.Zero
        is RoundedCornerShape -> CornerRadii.of(shape, bounds, density, inset)
        else -> error("Partial borders require RectangleShape or RoundedCornerShape")
    }
    val left = bounds.x
    val top = bounds.y
    val right = bounds.x + bounds.width
    val bottom = bounds.y + bounds.height

    if (sides.top) {
        moveTo(left + radii.topStart, top)
        lineTo(right - radii.topEnd, top)
    }
    if (sides.end) {
        moveTo(right, top + radii.topEnd)
        lineTo(right, bottom - radii.bottomEnd)
    }
    if (sides.bottom) {
        moveTo(right - radii.bottomEnd, bottom)
        lineTo(left + radii.bottomStart, bottom)
    }
    if (sides.start) {
        moveTo(left, bottom - radii.bottomStart)
        lineTo(left, top + radii.topStart)
    }
    if (sides.top && sides.end && radii.topEnd > 0f) {
        moveTo(right - radii.topEnd, top)
        arcTo(right - 2f * radii.topEnd, top, right, top + 2f * radii.topEnd, -90f, 90f)
    }
    if (sides.end && sides.bottom && radii.bottomEnd > 0f) {
        moveTo(right, bottom - radii.bottomEnd)
        arcTo(right - 2f * radii.bottomEnd, bottom - 2f * radii.bottomEnd, right, bottom, 0f, 90f)
    }
    if (sides.bottom && sides.start && radii.bottomStart > 0f) {
        moveTo(left + radii.bottomStart, bottom)
        arcTo(left, bottom - 2f * radii.bottomStart, left + 2f * radii.bottomStart, bottom, 90f, 90f)
    }
    if (sides.start && sides.top && radii.topStart > 0f) {
        moveTo(left, top + radii.topStart)
        arcTo(left, top, left + 2f * radii.topStart, top + 2f * radii.topStart, 180f, 90f)
    }
}

private data class CornerRadii(
    val topStart: Float,
    val topEnd: Float,
    val bottomEnd: Float,
    val bottomStart: Float,
) {
    companion object {
        val Zero = CornerRadii(0f, 0f, 0f, 0f)

        fun of(shape: RoundedCornerShape, bounds: Rectangle, density: Float, inset: Float): CornerRadii {
            val limit = min(bounds.width, bounds.height) / 2f
            fun resolve(radius: Dp): Float = (radius.value * density - inset).coerceIn(0f, limit)
            return CornerRadii(
                topStart = resolve(shape.topStart),
                topEnd = resolve(shape.topEnd),
                bottomEnd = resolve(shape.bottomEnd),
                bottomStart = resolve(shape.bottomStart),
            )
        }
    }
}
