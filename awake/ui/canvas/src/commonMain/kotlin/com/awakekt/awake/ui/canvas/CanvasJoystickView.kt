/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.graphics.CircleShape
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A round pad whose knob follows the pointer holding it, up to the pad's reach, and what its
 * [CanvasElement.joystick] settings make of that: where the pad sits, and how it fades while idle.
 * The element's box is where a thumb may land; the pad and knob are drawn inside it, each in its
 * skin's slot, or plainly in the element's [CanvasElement.background] and [CanvasElement.color].
 */
context(_: Composer)
internal fun JoystickView(element: CanvasElement, modifier: Modifier, back: Color, tree: CanvasTree, children: List<CanvasItem>) {
    val density = LocalDensity.current
    val settings = element.joystick ?: CanvasJoystick.Default
    val stick = element.stick
    val width = element.width
    val height = element.height
    val reach = settings.radiusIn(width, height)
    val centerX = stick.padCenterX(width, height, settings)
    val centerY = stick.padCenterY(width, height, settings)
    val alpha = stick.alpha(settings)
    val skin = tree.skinOf(element)?.joystick
    val state = element.controlState()
    Box(modifier.stickInput(element, density, tree.touchMarginPx(element, density)).let { if (alpha < 1f) it.alpha(alpha) else it }) {
        val pad = Modifier.offset((centerX - reach).dp, (centerY - reach).dp).size((reach * 2f).dp)
        val base = skin?.base
        if (base != null) Slot(element, CanvasSlots.JOYSTICK_BASE, base, state, pad, tree.images, round = true) else Box(pad.clip(CircleShape).background(back))
        val ring = skin?.sprintRing
        if (ring != null && stick.sprinting) Slot(element, CanvasSlots.JOYSTICK_SPRINT_RING, ring, state, pad, tree.images, round = true)
        if (skin?.arrows != null) Arrow(element, tree, centerX, centerY, reach)
        val knobSlot = skin?.knob
        val knob = knobSlot?.size ?: (reach * 2f * KNOB_FRACTION)
        val placed = Modifier.offset((centerX + stick.knobX - knob / 2f).dp, (centerY + stick.knobY - knob / 2f).dp).size(knob.dp)
        if (knobSlot != null) {
            Slot(element, CanvasSlots.JOYSTICK_KNOB, knobSlot, state, placed, tree.images, round = true)
        } else {
            Box(placed.clip(CircleShape).background(colorOf(element.color, Color.White)))
        }
        Children(element, children, tree)
    }
}

/** The stick's direction arrow at the pad's edge on the side it is pushed, turned to point that way. */
context(_: Composer)
private fun Arrow(element: CanvasElement, tree: CanvasTree, centerX: Float, centerY: Float, reach: Float) {
    val slot = tree.skinOf(element)?.joystick?.arrows ?: return
    val stick = element.stick
    if (stick.knobX == 0f && stick.knobY == 0f) return
    val radians = atan2(stick.knobY, stick.knobX)
    val size = slot.size ?: (reach * ARROW_FRACTION)
    val x = centerX + cos(radians) * reach - size / 2f
    val y = centerY + sin(radians) * reach - size / 2f
    val turned = Modifier.offset(x.dp, y.dp).size(size.dp).graphicsLayer(rotationDegrees = radians * HALF_TURN / PI.toFloat())
    Slot(element, CanvasSlots.JOYSTICK_ARROWS, slot, element.controlState(), turned, tree.images)
}

private const val KNOB_FRACTION = 0.45f
private const val ARROW_FRACTION = 0.3f
private const val HALF_TURN = 180f
