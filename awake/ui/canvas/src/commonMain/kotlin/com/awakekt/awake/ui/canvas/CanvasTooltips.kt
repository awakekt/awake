/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.widthIn
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.node.PointerInputNode
import com.awakekt.awake.compose.ui.platform.LocalFrameClock
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.theme.TextStyle

/**
 * Which element's [CanvasElement.tooltip] shows, and since when. It outlives a pass, which rebuilds
 * the [CanvasTree]: [CanvasOverlay] keeps one beside the canvas's origin.
 *
 * The element is the innermost one under the pointer that has a tooltip. Its clock restarts when the
 * pointer comes over a different one, and a press ends the tooltip until the pointer leaves and
 * returns, so a hint never sits over what the player is clicking.
 */
internal class CanvasTooltips {
    private var target: CanvasElement? = null
    private var since = 0f
    private var hidden = false
    private var pressed = false
    private var known = false

    /** Where the pointer is, in canvas units from the canvas's top-left; read only once [known] is true. */
    var pointerX = 0f
        private set
    var pointerY = 0f
        private set

    /** Records the pointer over a tooltip element at ([x], [y]) canvas units. */
    fun moved(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        known = true
    }

    /** Records a press on, or inside, a tooltip element; the tooltip goes until the pointer comes back. */
    fun pressed() {
        pressed = true
    }

    /**
     * Decides this frame's tooltip: [hovered] is the element the pointer is over that has one, or null,
     * and [now] is the frame clock's time. Returns the element whose tooltip is showing, or null.
     */
    fun update(hovered: CanvasElement?, now: Float): CanvasElement? {
        if (hovered !== target) {
            target = hovered
            since = now
            hidden = false
        }
        if (pressed) hidden = true
        pressed = false
        val shown = target ?: return null
        val waited = now - since >= CanvasElement.TOOLTIP_DELAY_SECONDS
        return shown.takeIf { waited && known && !hidden }
    }
}

/** A TextField's hint: its [CanvasElement.placeholder] key's text in the active locale; empty when it has none. */
internal fun CanvasTree.placeholderOf(element: CanvasElement): String =
    if (element.placeholder.isEmpty()) "" else keyedText(element.placeholder, element)

/** The hint an element shows near the pointer: its [CanvasElement.tooltip] key's text in the active locale; empty when it has none. */
internal fun CanvasTree.tooltipOf(element: CanvasElement): String =
    if (element.tooltip.isEmpty()) "" else keyedText(element.tooltip, element)

/**
 * Records where the pointer is over an element with a [CanvasElement.tooltip], and a press on it. It
 * only watches: it takes no press and hides nothing under it from the pointer.
 */
internal fun Modifier.tooltipPointer(element: CanvasElement, tooltips: CanvasTooltips, units: Float): Modifier =
    if (element.tooltip.isEmpty()) this else this then TooltipPointerElement(element, tooltips, units)

private class TooltipPointerElement(
    private val element: CanvasElement,
    private val tooltips: CanvasTooltips,
    private val units: Float,
) : ModifierNodeElement<TooltipPointerNode>() {
    override fun create(): TooltipPointerNode = TooltipPointerNode(element, tooltips, units)

    override fun update(node: TooltipPointerNode) {
        node.element = element
        node.tooltips = tooltips
        node.units = units
    }

    override fun toString(): String = "tooltipPointer()"
}

private class TooltipPointerNode(var element: CanvasElement, var tooltips: CanvasTooltips, var units: Float) :
    Modifier.Node(),
    PointerInputNode {
    override val blocksHitTest: Boolean get() = false

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        // The mouse alone hovers; a finger has no hover to rest, so its press is not one on a tooltip.
        if (pass != PointerEventPass.Main || event.pointerId != MOUSE_POINTER || !event.isInBounds) return
        when (event.type) {
            PointerEventType.Enter, PointerEventType.Move -> {
                val bounds = element.screenBounds ?: return
                tooltips.moved(bounds.x + event.x / units, bounds.y + event.y / units)
            }
            PointerEventType.Press -> tooltips.pressed()
            else -> Unit
        }
    }
}

private const val MOUSE_POINTER = 0L

/**
 * The tooltip of the element the pointer has been over for [CanvasElement.TOOLTIP_DELAY_SECONDS],
 * if any: a small dark bubble near the pointer, kept inside this box. Drawn over everything but
 * taking no pointer, so it never gets between the pointer and the element it names.
 */
context(_: Composer)
internal fun Tooltip(tree: CanvasTree) {
    val tooltips = tree.tooltips
    val hovered = tree.items.lastOrNull { item -> tree.shows(item.element) && item.element.tooltip.isNotEmpty() && item.element.isHovered }
    val shown = tooltips.update(hovered?.element, LocalFrameClock.current.totalSeconds) ?: return
    val words = tree.tooltipOf(shown)
    if (words.isEmpty()) return
    val bubble = TextStyle(color = TOOLTIP_TEXT, size = TOOLTIP_FONT_SIZE.sp, font = tree.fontOf(shown))
    Layout(
        nodeType = TooltipNode,
        modifier = Modifier.fillMaxSize(),
        measurePolicy = TooltipPolicy(tooltips.pointerX.dp, tooltips.pointerY.dp),
    ) {
        Box(
            Modifier.widthIn(max = TOOLTIP_MAX_WIDTH.dp)
                .background(TOOLTIP_BACKGROUND, TOOLTIP_RADIUS.dp)
                .padding(horizontal = TOOLTIP_PADDING_X.dp, vertical = TOOLTIP_PADDING_Y.dp)
                .testTag("canvas-tooltip"),
        ) {
            Text(words, style = bubble)
        }
    }
}

/** [Tooltip]'s node type, so another layout at the same place is not taken for it. */
private object TooltipNode

/**
 * Fills the room it is given and puts the bubble below and right of the pointer at ([x], [y]), the
 * way a cursor's own tooltip sits, then keeps it inside the room: past the right edge it goes to the
 * pointer's left, past the bottom to its top, and whatever is still out is pulled back in.
 */
private class TooltipPolicy(private val x: Dp, private val y: Dp) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val loose = Constraints.of(0, constraints.maxWidth, 0, constraints.maxHeight)
        val bubble = measurables.firstOrNull()?.measure(loose)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else bubble?.width ?: 0
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else bubble?.height ?: 0
        val pointerX = x.roundToPx()
        val pointerY = y.roundToPx()
        val beside = TOOLTIP_BESIDE.dp.roundToPx()
        val below = TOOLTIP_BELOW.dp.roundToPx()
        val above = TOOLTIP_ABOVE.dp.roundToPx()
        return layout(width, height) {
            if (bubble == null) return@layout
            val right = pointerX + beside
            val left = if (right + bubble.width > width) pointerX - beside - bubble.width else right
            val down = pointerY + below
            val top = if (down + bubble.height > height) pointerY - above - bubble.height else down
            bubble.placeAbsoluteAt(
                left.coerceIn(0, (width - bubble.width).coerceAtLeast(0)),
                top.coerceIn(0, (height - bubble.height).coerceAtLeast(0)),
            )
        }
    }
}

private val TOOLTIP_BACKGROUND = Color.fromHex("#111111EE")
private val TOOLTIP_TEXT = Color.fromHex("#FFFFFF")
private const val TOOLTIP_FONT_SIZE = 12f
private const val TOOLTIP_MAX_WIDTH = 240f
private const val TOOLTIP_RADIUS = 4f
private const val TOOLTIP_PADDING_X = 8f
private const val TOOLTIP_PADDING_Y = 4f

// A pointer's arrow covers about a dozen dp to its right and two dozen below its tip.
private const val TOOLTIP_BESIDE = 12f
private const val TOOLTIP_BELOW = 20f
private const val TOOLTIP_ABOVE = 8f
