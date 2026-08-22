// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.draw

import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.graphics.drawscope.DrawScope
import io.github.ronjunevaldoz.awake.compose.ui.node.DrawModifierNode
import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.core.math2d.Dp
import io.github.ronjunevaldoz.awake.core.math2d.dp

/**
 * Draws underneath this node's content, in node-local coordinates.
 *
 * Immediate-mode drawing inside a retained tree. What `ui-core` got wrong was layout by
 * re-execution, not painting -- emitting primitives for one frame is stateless and always was
 * fine. A debug overlay or a gizmo wants exactly this and gains nothing from retained identity.
 */
fun Modifier.drawBehind(onDraw: DrawScope.() -> Unit): Modifier = this then DrawBehindNode(onDraw)

/** Clips everything this node draws, including its children, to its own bounds. */
fun Modifier.clip(): Modifier = this then ClipNode

private class DrawBehindNode(private val onDraw: DrawScope.() -> Unit) : DrawModifierNode {
    override fun DrawScope.draw(drawContent: () -> Unit) {
        onDraw()
        drawContent()
    }

    override fun toString(): String = "drawBehind()"
}

private object ClipNode : DrawModifierNode {
    override fun DrawScope.draw(drawContent: () -> Unit) {
        clipped { drawContent() }
    }

    override fun toString(): String = "clip()"
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
    if (alpha >= 1f) this else this then AlphaNode(alpha)

private class AlphaNode(private val alpha: Float) : DrawModifierNode {
    override fun DrawScope.draw(drawContent: () -> Unit) {
        withAlpha(alpha) { drawContent() }
    }

    override fun toString(): String = "alpha($alpha)"
}
