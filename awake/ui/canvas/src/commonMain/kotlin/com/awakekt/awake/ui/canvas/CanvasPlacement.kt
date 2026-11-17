/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.wrapContentSize
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.compose.ui.node.LayoutModifierNode
import com.awakekt.awake.compose.ui.platform.FrameClock
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalFrameClock
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * [element] against its anchor in this box, inside [safeArea] unless it reaches under it, and moved,
 * scaled and faded as the player's [CanvasPlayerLayout] says. A scaled control lays out at a denser
 * dp, so it grows from its anchor and takes touches across all of its new size. An element [onCanvas],
 * a root, keeps its own size where it reaches past the canvas; a child is laid out in its parent.
 */
context(_: Composer)
internal fun BoxScope.AnchoredElement(element: CanvasElement, tag: String, tree: CanvasTree, safeArea: CanvasInsets, onCanvas: Boolean) {
    val inside = if (element.safeArea) safeArea else CanvasInsets.None
    val view = Modifier.size(element.width.dp, element.height.dp).testTag(tag)
    val adjust = tree.controls.playerLayout.adjustOf(element)
    // The clock object, not its time: reading it is untracked, and draw reads only its frame count.
    val clock = LocalFrameClock.current
    // The inset wraps the element rather than sitting in its modifier chain, so the element's own
    // bounds (hit area, editor picking) are exactly its size.
    if (adjust == null) {
        val placed = Modifier
            .align(element.anchor.alignment)
            .then(AnchoredSubpixelElement(element.anchor, element.offsetX, element.offsetY, inside, clock))
            .anchorInset(element.anchor, element.offsetX, element.offsetY, inside)
            .ownSize(element.anchor, onCanvas)
        Box(placed) {
            CanvasElementView(element, view, tree, tree.childrenOf(element))
        }
        return
    }
    // A player's offset is right and down on screen; an anchor's offset is inward from its edge.
    val x = element.offsetX + adjust.offsetX * if (element.anchor.column == 2) -1f else 1f
    val y = element.offsetY + adjust.offsetY * if (element.anchor.row == 2) -1f else 1f
    var placed = Modifier
        .align(element.anchor.alignment)
        .then(AnchoredSubpixelElement(element.anchor, x, y, inside, clock))
        .anchorInset(element.anchor, x, y, inside)
        .ownSize(element.anchor, onCanvas)
    if (adjust.opacity < 1f) placed = placed.alpha(adjust.opacity)
    Box(placed) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides density * adjust.scale) {
            CanvasElementView(element, view, tree, tree.childrenOf(element))
        }
    }
}

/**
 * Measures an element [onCanvas] at its own size, aligned to [anchor], even where its offset reaches
 * past the canvas: the inset leaves less space there, and a window moved partly off is clipped, never
 * squashed. A child is left to its parent's space, as before.
 */
private fun Modifier.ownSize(anchor: CanvasAnchor, onCanvas: Boolean): Modifier = if (!onCanvas) {
    this
} else {
    wrapContentSize(
        horizontalAlignment = when (anchor.column) { 0 -> Alignment.Start; 1 -> Alignment.CenterHorizontally; else -> Alignment.End },
        verticalAlignment = when (anchor.row) { 0 -> Alignment.Top; 1 -> Alignment.CenterVertically; else -> Alignment.Bottom },
        unbounded = true,
    )
}

private class AnchoredSubpixelElement(
    private val anchor: CanvasAnchor,
    private val x: Float,
    private val y: Float,
    private val safeArea: CanvasInsets,
    private val clock: FrameClock,
) : ModifierNodeElement<AnchoredSubpixelNode>() {
    override fun create(): AnchoredSubpixelNode = AnchoredSubpixelNode()
    override fun update(node: AnchoredSubpixelNode) {
        node.anchor = anchor
        node.x = x
        node.y = y
        node.safeArea = safeArea
        node.clock = clock
    }
}

/**
 * Slides an anchored element by its continuous subpixel remainder while [x] or [y] animates by
 * fractions of a pixel, leaving static placement, whole-pixel motion, and hit-test bounds unchanged.
 */
private class AnchoredSubpixelNode :
    Modifier.Node(),
    LayoutModifierNode,
    DrawModifierNode {
    var anchor: CanvasAnchor = CanvasAnchor.TopLeft
    var x: Float = 0f
    var y: Float = 0f
    var safeArea: CanvasInsets = CanvasInsets.None
    var clock: FrameClock? = null

    private var continuousX = 0f
    private var continuousY = 0f
    private var placedIntX = 0
    private var placedIntY = 0
    private val subpixel = CanvasSubpixelTracker()

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        val spaceW = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
        val spaceH = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
        resolveAxis(
            axis = anchor.column,
            offsetDp = x,
            nearSafeDp = safeArea.left,
            farSafeDp = safeArea.right,
            paddedSize = placeable.width,
            spaceSize = spaceW,
            density = density,
            isX = true,
        )
        resolveAxis(
            axis = anchor.row,
            offsetDp = y,
            nearSafeDp = safeArea.top,
            farSafeDp = safeArea.bottom,
            paddedSize = placeable.height,
            spaceSize = spaceH,
            density = density,
            isX = false,
        )
        return layout(placeable.width, placeable.height) {
            placeable.placeAt(0, 0)
        }
    }

    @Suppress("LongParameterList")
    private fun resolveAxis(
        axis: Int,
        offsetDp: Float,
        nearSafeDp: Float,
        farSafeDp: Float,
        paddedSize: Int,
        spaceSize: Int,
        density: Float,
        isX: Boolean,
    ) {
        val nearPadDp = when (axis) {
            0 -> offsetDp.coerceAtLeast(0f)
            1 -> 2f * offsetDp.coerceAtLeast(0f)
            else -> 0f
        } + nearSafeDp
        val farPadDp = when (axis) {
            1 -> 2f * (-offsetDp).coerceAtLeast(0f)
            2 -> offsetDp.coerceAtLeast(0f)
            else -> 0f
        } + farSafeDp
        val nearPx = (nearPadDp * density).roundToInt()
        val farPx = (farPadDp * density).roundToInt()
        val childSize = (paddedSize - nearPx - farPx).coerceAtLeast(0)
        val intPos: Int
        val floatPos: Float
        when (axis) {
            0 -> {
                intPos = nearPx
                floatPos = (nearSafeDp + offsetDp.coerceAtLeast(0f)) * density
            }
            1 -> {
                val zeroNearPx = (nearSafeDp * density).roundToInt()
                val zeroFarPx = (farSafeDp * density).roundToInt()
                val zeroPos = (spaceSize - (childSize + zeroNearPx + zeroFarPx)) / 2 + zeroNearPx
                intPos = (spaceSize - paddedSize) / 2 + nearPx
                floatPos = zeroPos + offsetDp * density
            }
            else -> {
                intPos = spaceSize - paddedSize + nearPx
                floatPos = spaceSize - childSize - (farSafeDp + offsetDp.coerceAtLeast(0f)) * density
            }
        }
        if (isX) {
            placedIntX = intPos
            continuousX = floatPos
        } else {
            placedIntY = intPos
            continuousY = floatPos
        }
    }

    override fun DrawScope.draw(drawContent: () -> Unit) {
        val clock = clock
        subpixel.step(clock?.frame ?: 0L, continuousX, continuousY, placedIntX, placedIntY)
        // While it eases, the next frame differs with no write: reading the time makes a host that
        // replays unchanged drawings repaint this one each frame until it settles (#1512).
        if (subpixel.easing) clock?.deltaSeconds
        val dx = subpixel.residualX
        val dy = subpixel.residualY
        if (dx != 0f || dy != 0f) {
            translate(dx, dy, drawContent)
        } else {
            drawContent()
        }
    }
}
