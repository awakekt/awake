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
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.compose.ui.node.LayoutModifierNode
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * [element] against its anchor in this box, inside [safeArea] unless it reaches under it, and moved,
 * scaled and faded as the player's [CanvasPlayerLayout] says. A scaled control lays out at a denser
 * dp, so it grows from its anchor and takes touches across all of its new size.
 */
context(_: Composer)
internal fun BoxScope.AnchoredElement(element: CanvasElement, tag: String, tree: CanvasTree, safeArea: CanvasInsets) {
    val inside = if (element.safeArea) safeArea else CanvasInsets.None
    val view = Modifier.size(element.width.dp, element.height.dp).testTag(tag)
    val adjust = tree.controls.playerLayout.adjustOf(element)
    // The inset wraps the element rather than sitting in its modifier chain, so the element's own
    // bounds (hit area, editor picking) are exactly its size.
    if (adjust == null) {
        val placed = Modifier
            .align(element.anchor.alignment)
            .then(AnchoredSubpixelElement(element.anchor, element.offsetX, element.offsetY, inside))
            .anchorInset(element.anchor, element.offsetX, element.offsetY, inside)
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
        .then(AnchoredSubpixelElement(element.anchor, x, y, inside))
        .anchorInset(element.anchor, x, y, inside)
    if (adjust.opacity < 1f) placed = placed.alpha(adjust.opacity)
    Box(placed) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides density * adjust.scale) {
            CanvasElementView(element, view, tree, tree.childrenOf(element))
        }
    }
}

private class AnchoredSubpixelElement(
    private val anchor: CanvasAnchor,
    private val x: Float,
    private val y: Float,
    private val safeArea: CanvasInsets,
) : ModifierNodeElement<AnchoredSubpixelNode>() {
    override fun create(): AnchoredSubpixelNode = AnchoredSubpixelNode()
    override fun update(node: AnchoredSubpixelNode) {
        node.anchor = anchor
        node.x = x
        node.y = y
        node.safeArea = safeArea
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
        subpixel.step(continuousX, continuousY, placedIntX, placedIntY)
        val dx = subpixel.residualX
        val dy = subpixel.residualY
        if (dx != 0f || dy != 0f) {
            translate(dx, dy, drawContent)
        } else {
            drawContent()
        }
    }
}

private class CanvasSubpixelTracker {
    private var initialized = false
    private var lastPxX = 0f
    private var lastPxY = 0f
    private var lastRenderedX = 0f
    private var lastRenderedY = 0f
    private var active = false
    private var settleFrames = 0
    private var leadInErrorX = 0f
    private var leadInErrorY = 0f
    private var leadInFrames = 0

    var residualX: Float = 0f
        private set

    var residualY: Float = 0f
        private set

    fun step(pxX: Float, pxY: Float, intX: Int, intY: Int) {
        val rawResX = pxX - intX
        val rawResY = pxY - intY
        if (!initialized) {
            initializeAt(pxX, pxY, intX, intY)
            return
        }
        val prevPxX = lastPxX
        val prevPxY = lastPxY
        val dx = pxX - prevPxX
        val dy = pxY - prevPxY
        lastPxX = pxX
        lastPxY = pxY

        val moved = abs(dx) > MOTION_EPSILON || abs(dy) > MOTION_EPSILON
        val hasResidual = abs(rawResX) > FRACTION_EPSILON || abs(rawResY) > FRACTION_EPSILON
        if (moved) {
            advanceMoving(prevPxX, prevPxY, dx, dy, hasResidual)
        } else if (active) {
            advanceStationary(hasResidual)
        }
        computeResiduals(rawResX, rawResY)
        lastRenderedX = intX + residualX
        lastRenderedY = intY + residualY
    }

    private fun initializeAt(pxX: Float, pxY: Float, intX: Int, intY: Int) {
        initialized = true
        lastPxX = pxX
        lastPxY = pxY
        lastRenderedX = intX.toFloat()
        lastRenderedY = intY.toFloat()
        residualX = 0f
        residualY = 0f
    }

    private fun advanceMoving(prevPxX: Float, prevPxY: Float, dx: Float, dy: Float, hasResidual: Boolean) {
        val fractionalDelta = abs(dx - dx.roundToInt()) > FRACTION_EPSILON ||
            abs(dy - dy.roundToInt()) > FRACTION_EPSILON
        when {
            fractionalDelta -> {
                if (!active) {
                    startLeadIn(prevPxX, prevPxY)
                } else if (leadInFrames in 1..SETTLE_FRAMES) {
                    leadInFrames += 1
                }
                settleFrames = 0
            }
            active && hasResidual -> {
                settleFrames = 0
                if (leadInFrames in 1..SETTLE_FRAMES) leadInFrames += 1
            }
            !hasResidual -> leadInFrames = 0
        }
    }

    private fun startLeadIn(prevPxX: Float, prevPxY: Float) {
        leadInErrorX = lastRenderedX - prevPxX
        leadInErrorY = lastRenderedY - prevPxY
        val hasLeadError = abs(leadInErrorX) > FRACTION_EPSILON || abs(leadInErrorY) > FRACTION_EPSILON
        leadInFrames = if (hasLeadError) 1 else 0
        active = true
    }

    private fun advanceStationary(hasResidual: Boolean) {
        if (!hasResidual) {
            clearActiveMotion()
            return
        }
        settleFrames += 1
        if (settleFrames >= SETTLE_FRAMES) {
            clearActiveMotion()
        }
    }

    private fun clearActiveMotion() {
        active = false
        settleFrames = 0
        leadInFrames = 0
    }

    private fun computeResiduals(rawResX: Float, rawResY: Float) {
        if (!active) {
            residualX = 0f
            residualY = 0f
            return
        }
        val leadFactor = if (leadInFrames in 1 until SETTLE_FRAMES) {
            1f - smoothStep(leadInFrames.toFloat() / SETTLE_FRAMES)
        } else {
            0f
        }
        val baseX = rawResX + leadInErrorX * leadFactor
        val baseY = rawResY + leadInErrorY * leadFactor
        val settleFactor = if (settleFrames > 0) {
            1f - smoothStep(settleFrames.toFloat() / SETTLE_FRAMES)
        } else {
            1f
        }
        residualX = baseX * settleFactor
        residualY = baseY * settleFactor
    }

    private fun smoothStep(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    companion object {
        private const val MOTION_EPSILON = 1e-4f
        private const val FRACTION_EPSILON = 1e-3f
        private const val SETTLE_FRAMES = 8
    }
}
