/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.rememberGroup
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.graphics.vector.ImageVector
import com.awakekt.awake.compose.ui.graphics.vector.VectorPainter
import com.awakekt.awake.compose.ui.graphics.vector.rememberVectorPainter
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.layout.PlacementScope
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * An icon, drawn from an [ImageVector].
 *
 * **`Icon` is Material's, not Foundation's** -- `androidx.compose.material3.Icon`, with no
 * counterpart in `androidx.compose.foundation`. So this belongs here, in the design system, for the
 * same reason `ShadcnSlider` does. `ImageVector` itself is compose-ui's and lives there.
 *
 * shadcn sizes icons inside controls with `[&_svg:not([class*='size-'])]:size-4` -- a 16dp default
 * that a caller's own `size-*` overrides. That is the [size] default here.
 *
 * Drawn through `DrawScope.drawPath`, which this recipe is the reason for. Reaching for `emit`
 * instead -- the documented escape hatch -- painted the icon at the *frame's* origin rather than its
 * own node, because every other `DrawScope` helper maps node-local coordinates into tree space and
 * `emit` does not. Caught by looking at a gallery render and finding the chevron above the title it
 * was declared after.
 */
context(_: Composer)
fun ShadcnIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = IconSize,
    tint: Color? = null,
) {
    rememberGroup(ShadcnIconGroup) {
        val color = tint ?: LocalTextStyle.current.color ?: shadcnTheme.palette.foreground
        val painter = rememberVectorPainter(icon)
        val state = remember { ShadcnIconNodeState() }
        Layout(
            nodeType = ShadcnIconNodeType,
            modifier = state.modifierFor(modifier, size, painter, color),
            measurePolicy = IconMeasurePolicy,
        )
    }
}

private object ShadcnIconNodeType

private val PlaceNothing: PlacementScope.() -> Unit = {}

private object IconMeasurePolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult =
        layout(constraints.minWidth, constraints.minHeight, place = PlaceNothing)
}

private class ShadcnIconNodeState {
    private var lastModifier: Modifier? = null
    private var hasSize = false
    private var lastSize: Dp = 0.dp
    private var lastPainter: VectorPainter? = null
    private var lastColor: Color = Color.Transparent
    private var drawElement: ShadcnIconDrawElement? = null
    private var chain: Modifier = Modifier

    fun modifierFor(modifier: Modifier, size: Dp, painter: VectorPainter, color: Color): Modifier {
        val prevDraw = drawElement
        val drawChanged = prevDraw == null || lastPainter !== painter || lastColor != color
        val currentDraw = if (!drawChanged) {
            prevDraw
        } else {
            lastPainter = painter
            lastColor = color
            ShadcnIconDrawElement(painter, color).also { drawElement = it }
        }
        val layoutChanged = !hasSize || size != lastSize || modifier != lastModifier
        if (drawChanged || layoutChanged) {
            hasSize = true
            lastModifier = modifier
            lastSize = size
            chain = modifier.size(size).then(currentDraw)
        }
        return chain
    }
}

private class ShadcnIconDrawElement(
    private val painter: VectorPainter,
    private val color: Color,
) : ModifierNodeElement<ShadcnIconDrawNode>() {
    override fun create(): ShadcnIconDrawNode = ShadcnIconDrawNode()

    override fun update(node: ShadcnIconDrawNode) {
        node.painter = painter
        node.color = color
    }

    override fun toString(): String = "shadcnIconDraw()"
}

private class ShadcnIconDrawNode :
    Modifier.Node(),
    DrawModifierNode {
    lateinit var painter: VectorPainter
    var color: Color = Color.White

    override fun DrawScope.draw(drawContent: () -> Unit) {
        painter.draw(this, 0f, 0f, width.toFloat(), height.toFloat(), color)
        drawContent()
    }

    override fun toString(): String = "shadcnIconDraw()"
}

/** shadcn's `size-4` for an icon inside a control. */
private val IconSize: Dp = 16.dp

/** Keys this component's `remember` slots as one group in its caller's node. */
private object ShadcnIconGroup
