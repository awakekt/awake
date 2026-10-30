/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.node.PointerInputNode
import com.awakekt.awake.core.math2d.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Whether [this] takes a tap: it has a [CanvasButtonWidget], or it is a picture or a plain box its [CanvasInput] makes pressable. */
internal val CanvasElement.takesTaps: Boolean
    get() = widget is CanvasButtonWidget || pressableImageOrPanel

/** A [CanvasImageWidget], or an element with neither a widget nor words, that its [CanvasInput] makes pressable. */
private val CanvasElement.pressableImageOrPanel: Boolean
    get() = inputPart?.pressable == true && (widget is CanvasImageWidget || widget == null && textPart == null)

/** A pressable Image's or Panel's tap, as a Button's `clickable` takes one; anything else unchanged. */
internal fun Modifier.pressableTap(element: CanvasElement): Modifier =
    if (element.pressableImageOrPanel) clickable(element.interactions) { element.press() } else this

/**
 * Records where a press lands on an element that [takesTaps] as its [CanvasElement.pressStart], for
 * [CanvasElement.consumePressAt]. It only watches: what takes the press, a Button's or a pressable
 * element's `clickable`, decides whether it becomes a tap.
 */
internal fun Modifier.pressPoint(element: CanvasElement, density: Float, units: Float = density): Modifier =
    if (element.takesTaps) this then PressPointElement(element, density, units) else this

/**
 * [x], [y], pixels from the top-left of [element]'s unrotated box, in the element's own units on
 * its picture: divided by [density], which carries the element's scale, and turned back by the
 * element's `style.rotation` about the box's centre.
 */
internal fun elementPoint(element: CanvasElement, x: Int, y: Int, density: Float, units: Float = density): Vec2 {
    val px = x / density
    val py = y / density
    val degrees = element.style.rotation ?: return Vec2(px, py)
    val bounds = element.screenBounds
    val toLocal = if (density > 0f) units / density else 1f
    val cx = (bounds?.width?.let { it * toLocal } ?: element.width) / 2f
    val cy = (bounds?.height?.let { it * toLocal } ?: element.height) / 2f
    // The picture turns clockwise on a y-down screen; undoing it turns the point the other way.
    val radians = -degrees * PI.toFloat() / 180f
    val c = cos(radians)
    val s = sin(radians)
    val dx = px - cx
    val dy = py - cy
    return Vec2(cx + dx * c - dy * s, cy + dx * s + dy * c)
}

private class PressPointElement(
    private val element: CanvasElement,
    private val density: Float,
    private val units: Float,
) : ModifierNodeElement<PressPointNode>() {
    override fun create(): PressPointNode = PressPointNode(element, density, units)

    override fun update(node: PressPointNode) {
        node.element = element
        node.density = density
        node.units = units
    }

    override fun toString(): String = "pressPoint()"
}

private class PressPointNode(var element: CanvasElement, var density: Float, var units: Float) :
    Modifier.Node(),
    PointerInputNode {
    override val blocksHitTest: Boolean get() = false

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main || !event.isInBounds) return
        if (event.type == PointerEventType.Press) {
            element.pressStart = elementPoint(element, event.x, event.y, density, units)
        } else if (event.type == PointerEventType.SecondaryPress && !event.isConsumed) {
            element.secondaryPress()
            event.consume()
        }
    }
}
