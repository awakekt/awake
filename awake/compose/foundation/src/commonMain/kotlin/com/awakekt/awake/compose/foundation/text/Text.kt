/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.platform.LocalFont
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.theme.TextOutline
import com.awakekt.awake.core.text.theme.TextShadow
import com.awakekt.awake.core.text.theme.TextStyle

// PascalCase composables: Compose's own convention, and this is public API surface -- the same
// reason Constraints.Infinity keeps its casing. See 11-refinements.md rule 1.

private object TextNodeType

context(_: Composer)
/**
 * A run of text, sized from the font's own metrics.
 *
 * [style] merges over the inherited [LocalTextStyle] rather than replacing it, so setting a weight
 * does not silently drop an ancestor's size.
 *
 * Painted with the same run geometry the measure policy sizes with -- caret and glyphs computed by
 * different arithmetic drift a pixel per character, and the drift only shows at the end of a long
 * line where nobody is looking.
 *
 * Unchanged text is not re-shaped: the node's retained paint node keeps the last measurement and
 * glyph run and hands them to each frame's new policy, which reuses them while the text, style,
 * font and density are the same.
 */
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
) {
    val resolved = LocalTextStyle.current then style
    val font = LocalFont.current
    val policy = TextMeasurePolicy(text, resolved, font)
    Layout(
        nodeType = TextNodeType,
        modifier = modifier.then(TextPaintElement(policy, resolved.color ?: DefaultTextColor, resolved.shadow, resolved.outline)),
        measurePolicy = policy,
    )
}

/** Paints a [Text] run and owns the node's [TextLayoutCache], which outlives the frame. */
private class TextPaintElement(
    private val policy: TextMeasurePolicy,
    private val color: Color,
    private val shadow: TextShadow?,
    private val outline: TextOutline?,
) : ModifierNodeElement<TextPaintNode>() {
    override fun create(): TextPaintNode = TextPaintNode()

    override fun update(node: TextPaintNode) {
        node.policy = policy
        node.color = color
        node.shadow = shadow
        node.outline = outline
        // Runs while the modifier is applied during composition, before this frame's measure.
        policy.cache = node.cache
    }

    override fun toString(): String = "text()"
}

private class TextPaintNode :
    Modifier.Node(),
    DrawModifierNode {
    val cache = TextLayoutCache()
    lateinit var policy: TextMeasurePolicy
    lateinit var color: Color
    var shadow: TextShadow? = null
    var outline: TextOutline? = null

    override fun DrawScope.draw(drawContent: () -> Unit) {
        val run = policy.runFor(density, 1f)
        shadow?.let { run.paint(this, it.color, it.offsetX * density, it.offsetY * density) }
        // The run again in the outline's colour, moved a step in each of eight directions: the
        // glyphs' own shapes, widened, under them.
        outline?.let { ring ->
            val step = ring.width * density
            for (i in OUTLINE_X.indices) run.paint(this, ring.color, OUTLINE_X[i] * step, OUTLINE_Y[i] * step)
        }
        run.paint(this, color)
        drawContent()
    }

    override fun toString(): String = "text()"
}

// Unit steps to the eight neighbours; the diagonals are shortened so the ring is round, not square.
private val OUTLINE_X = floatArrayOf(1f, 0.7071f, 0f, -0.7071f, -1f, -0.7071f, 0f, 0.7071f)
private val OUTLINE_Y = floatArrayOf(0f, 0.7071f, 1f, 0.7071f, 0f, -0.7071f, -1f, -0.7071f)

/** Used when nothing in the chain provided one. Mid grey reads on both light and dark. */
internal val DefaultTextColor: Color = Color(0.9f, 0.9f, 0.92f, 1f)
