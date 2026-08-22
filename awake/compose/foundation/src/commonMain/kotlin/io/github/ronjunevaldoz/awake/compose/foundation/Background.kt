// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.graphics.drawscope.DrawScope
import io.github.ronjunevaldoz.awake.compose.ui.node.DrawModifierNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Dp
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import io.github.ronjunevaldoz.awake.core.color.Color

/**
 * Primitive visual modifiers -- colours and shapes, no tokens and no state rules.
 *
 * `Style`, variants and theme tokens stay in `ui-designsystem`, which resolves them *into* these.
 * Real Compose splits the same way: `Modifier.background`/`border` here, Material's opinions
 * elsewhere. See `docs/reference/compose-engine/04-styling-theme.md`.
 */
fun Modifier.background(color: Color, cornerRadius: Dp = 0.dp): Modifier =
    this then BackgroundNode(color, cornerRadius)

/**
 * Draws a border *inside* the node's bounds.
 *
 * Inside, not centred on the edge: an outside-drawn border would overflow the space the parent
 * measured for this node, so a bordered child would overlap its sibling.
 */
fun Modifier.border(width: Dp, color: Color, cornerRadius: Dp = 0.dp): Modifier =
    this then BorderNode(width, color, cornerRadius)

private class BackgroundNode(
    private val color: Color,
    private val cornerRadius: Dp,
) : DrawModifierNode {
    override fun DrawScope.draw(drawContent: () -> Unit) {
        drawRoundedRect(color = color, radius = cornerRadius.value * density)
        drawContent()
    }

    override fun toString(): String = "background($color, radius=$cornerRadius)"
}

private class BorderNode(
    private val strokeWidth: Dp,
    private val color: Color,
    private val cornerRadius: Dp,
) : DrawModifierNode {
    override fun DrawScope.draw(drawContent: () -> Unit) {
        drawContent()
        val stroke = strokeWidth.value * density
        val radius = cornerRadius.value * density
        // Four edges rather than a stroked outline: the backend has no stroke primitive, and a
        // filled ring would need path tessellation for what is almost always a square.
        drawRoundedRect(0f, 0f, width.toFloat(), stroke, color, radius)
        drawRoundedRect(0f, height - stroke, width.toFloat(), stroke, color, radius)
        drawRoundedRect(0f, stroke, stroke, height - stroke * 2, color, radius)
        drawRoundedRect(
            width - stroke,
            stroke,
            stroke,
            height - stroke * 2,
            color,
            radius,
        )
    }

    override fun toString(): String = "border($strokeWidth, $color)"
}
