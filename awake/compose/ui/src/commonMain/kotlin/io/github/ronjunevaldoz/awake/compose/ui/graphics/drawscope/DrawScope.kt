// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.graphics.drawscope

import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import io.github.ronjunevaldoz.awake.core.graphics2d.UiLinearGradient
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle

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

    /** Emits an already-built primitive. The escape hatch for anything the helpers do not cover. */
    fun emit(primitive: UiDrawPrimitive)
}

/**
 * Collects a frame's primitives while walking the placed tree.
 *
 * One instance per paint, reused across every node: the node-local origin is swapped in as the walk
 * descends rather than a scope being allocated per node.
 */
internal class PaintScope : DrawScope {
    private val output = mutableListOf<UiDrawPrimitive>()
    private var originX = 0f
    private var originY = 0f

    override var width: Int = 0
        private set

    override var height: Int = 0
        private set

    override var density: Float = 1f
        private set

    private var alpha = 1f

    fun primitives(): List<UiDrawPrimitive> = output

    fun reset() {
        output.clear()
        alpha = 1f
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
    }

    override fun drawRect(x: Float, y: Float, width: Float, height: Float, color: Color) {
        output += UiDrawPrimitive.Quad(originX + x, originY + y, width, height, color.dimmedBy(alpha))
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
        output += UiDrawPrimitive.RoundedQuad(
            originX + x,
            originY + y,
            width,
            height,
            color.dimmedBy(alpha),
            radius,
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
        output += UiDrawPrimitive.Glyph(
            originX + x,
            originY + y,
            width,
            height,
            u0,
            v0,
            u1,
            v1,
            color.dimmedBy(alpha),
        )
    }

    override fun clipped(inset: Float, block: () -> Unit) {
        val rect = Rectangle(
            originX + inset,
            originY + inset,
            (width - inset * 2).coerceAtLeast(0f),
            (height - inset * 2).coerceAtLeast(0f),
        )
        output += UiDrawPrimitive.ClipPush(rect)
        try {
            block()
        } finally {
            // Popped even on a throw: an unbalanced clip stack silently clips the rest of the frame.
            output += UiDrawPrimitive.ClipPop(rect)
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
