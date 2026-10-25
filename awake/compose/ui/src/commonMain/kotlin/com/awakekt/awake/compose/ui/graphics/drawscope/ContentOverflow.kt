/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics.drawscope

import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.bounds
import com.awakekt.awake.core.math2d.Rectangle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * How far a list of primitives paints past a box, on each side: what a `graphicsLayer` target sized to
 * its node would cut off.
 *
 * A node's content is not confined to its bounds -- a glyph's ink reaches past its text box, a shadow
 * past its card -- and Compose's `graphicsLayer` does not clip it. A layer drawn into a render target
 * the node's size did, so a fade or a press scale cut every overflowing glyph the moment it started.
 *
 * One instance per painter, reused for every layer: [measure] writes its four fields rather than
 * returning a box.
 */
internal class ContentOverflow {
    /** Pixels painted left of the box, or 0. */
    var left = 0f
        private set

    /** Pixels painted above the box, or 0. */
    var top = 0f
        private set

    /** Pixels painted right of the box, or 0. */
    var right = 0f
        private set

    /** Pixels painted below the box, or 0. */
    var bottom = 0f
        private set

    // The active clip in the list, unbounded outside every clip the list itself pushes.
    private var clipX0 = Float.NEGATIVE_INFINITY
    private var clipY0 = Float.NEGATIVE_INFINITY
    private var clipX1 = Float.POSITIVE_INFINITY
    private var clipY1 = Float.POSITIVE_INFINITY
    private var clipDepth = 0

    // The primitive [boundsOf] last measured.
    private var x0 = 0f
    private var y0 = 0f
    private var x1 = 0f
    private var y1 = 0f

    /**
     * Measures how far [primitives] paint past ([x], [y], [width], [height]), all in one coordinate
     * space. Content a clip in the list cuts off counts only up to that clip.
     */
    fun measure(primitives: List<UiDrawPrimitive>, x: Float, y: Float, width: Float, height: Float) {
        reset()
        // Indexed: a List iterator is an allocation, and this runs for every layer every frame.
        for (i in primitives.indices) {
            when (val primitive = primitives[i]) {
                is UiDrawPrimitive.ClipPush -> {
                    clipDepth++
                    setClip(primitive.rect)
                }
                is UiDrawPrimitive.ClipPathPush -> {
                    clipDepth++
                    setClip(primitive.boundsRect)
                }
                is UiDrawPrimitive.ClipPop -> {
                    clipDepth--
                    // A pop out of the list's own outermost clip restores what enclosed the layer,
                    // which its composite meets again; inside the layer nothing bounds the content.
                    if (clipDepth > 0) setClip(primitive.restoreRect) else unclip()
                }
                else -> if (boundsOf(primitive)) include(x, y, width, height)
            }
        }
    }

    /** Back to nothing painted past the box. */
    fun reset() {
        left = 0f
        top = 0f
        right = 0f
        bottom = 0f
        clipDepth = 0
        unclip()
    }

    private fun include(x: Float, y: Float, width: Float, height: Float) {
        val px0 = maxOf(x0, clipX0)
        val py0 = maxOf(y0, clipY0)
        val px1 = minOf(x1, clipX1)
        val py1 = minOf(y1, clipY1)
        if (px0 >= px1 || py0 >= py1) return
        left = maxOf(left, x - px0)
        top = maxOf(top, y - py0)
        right = maxOf(right, px1 - (x + width))
        bottom = maxOf(bottom, py1 - (y + height))
    }

    private fun setClip(rect: Rectangle) {
        clipX0 = rect.x
        clipY0 = rect.y
        clipX1 = rect.x + rect.width
        clipY1 = rect.y + rect.height
    }

    private fun unclip() {
        clipX0 = Float.NEGATIVE_INFINITY
        clipY0 = Float.NEGATIVE_INFINITY
        clipX1 = Float.POSITIVE_INFINITY
        clipY1 = Float.POSITIVE_INFINITY
    }

    /** Writes [primitive]'s painted box into [x0]..[y1]; false when it paints nothing measurable. */
    private fun boundsOf(primitive: UiDrawPrimitive): Boolean {
        when (primitive) {
            is UiDrawPrimitive.Quad -> box(primitive.x, primitive.y, primitive.w, primitive.h)
            is UiDrawPrimitive.GradientQuad -> box(primitive.x, primitive.y, primitive.w, primitive.h)
            is UiDrawPrimitive.RoundedQuad -> box(primitive.x, primitive.y, primitive.w, primitive.h)
            is UiDrawPrimitive.Glyph -> box(primitive.x, primitive.y, primitive.w, primitive.h)
            is UiDrawPrimitive.BackdropBlur -> box(primitive.x, primitive.y, primitive.w, primitive.h)
            is UiDrawPrimitive.Texture -> turnedBox(primitive.x, primitive.y, primitive.w, primitive.h, primitive.rotationDegrees)
            is UiDrawPrimitive.ShadowQuad -> {
                val reach = primitive.spread + primitive.blurRadius
                box(primitive.x + primitive.offsetX - reach, primitive.y + primitive.offsetY - reach, primitive.w + 2 * reach, primitive.h + 2 * reach)
            }
            // A pixel of antialiased fringe past the outline, and a stroke's whole width for a miter.
            is UiDrawPrimitive.FilledPath -> rect(primitive.path.bounds(), ANTIALIAS_FRINGE)
            is UiDrawPrimitive.StrokedPath -> rect(primitive.path.bounds(), primitive.stroke.width.value + ANTIALIAS_FRINGE)
            is UiDrawPrimitive.Mesh -> rect(if (primitive.rotationDegrees == 0f) placedBounds(primitive) else primitive.placedMesh().bounds(), 0f)
            is UiDrawPrimitive.ClipPush, is UiDrawPrimitive.ClipPathPush, is UiDrawPrimitive.ClipPop -> return false
        }
        return x1 > x0 && y1 > y0
    }

    private fun box(x: Float, y: Float, w: Float, h: Float) {
        x0 = x
        y0 = y
        x1 = x + w
        y1 = y + h
    }

    private fun rect(rect: Rectangle, outset: Float) = box(rect.x - outset, rect.y - outset, rect.width + 2 * outset, rect.height + 2 * outset)

    /** The box a [w] by [h] quad turned [degrees] about its centre covers. */
    private fun turnedBox(x: Float, y: Float, w: Float, h: Float, degrees: Float) {
        if (degrees == 0f) return box(x, y, w, h)
        val radians = degrees * PI.toFloat() / 180f
        val halfW = (abs(w * cos(radians)) + abs(h * sin(radians))) / 2f
        val halfH = (abs(w * sin(radians)) + abs(h * cos(radians))) / 2f
        val cx = x + w / 2f
        val cy = y + h / 2f
        box(cx - halfW, cy - halfH, 2 * halfW, 2 * halfH)
    }

    /** An unturned mesh's box: its own bounds scaled and moved as [UiDrawPrimitive.Mesh.placedMesh] would. */
    private fun placedBounds(primitive: UiDrawPrimitive.Mesh): Rectangle {
        val b = primitive.mesh.bounds()
        return Rectangle(
            b.x * primitive.scaleX + primitive.offsetX,
            b.y * primitive.scaleY + primitive.offsetY,
            b.width * primitive.scaleX,
            b.height * primitive.scaleY,
        )
    }

    private companion object {
        const val ANTIALIAS_FRINGE = 1f
    }
}
