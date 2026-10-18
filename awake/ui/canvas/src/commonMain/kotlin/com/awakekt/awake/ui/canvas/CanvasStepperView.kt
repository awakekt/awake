/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.focusable
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.input.key.onKeyEvent
import com.awakekt.awake.compose.ui.platform.LocalFrameClock
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.input.NumberStepping
import com.awakekt.awake.core.text.theme.TextStyle

/**
 * A Stepper: a minus button at its left, a plus button at its right, each as wide as it is tall, and
 * its [CanvasElement.number] between them. A press steps once and a hold repeats; while it has focus,
 * Up and Down, Page Up and Page Down, and Home and End step it as a stepper's keys do.
 */
@Suppress("LongParameterList")
context(_: Composer)
internal fun StepperView(element: CanvasElement, styled: Modifier, textStyle: TextStyle, tree: CanvasTree, children: List<CanvasItem>) {
    val settings = element.stepper ?: CanvasStepper.Default
    readStepperNumber(element, tree)
    val delta = LocalFrameClock.current.deltaSeconds
    val steps = element.plusHold.advance(element.plusPress.isPressed, delta, settings.repeatDelay, settings.repeatInterval) -
        element.minusHold.advance(element.minusPress.isPressed, delta, settings.repeatDelay, settings.repeatInterval)
    element.stepBy(steps)
    val keyed = styled.focusable(interactionSource = element.interactions).onKeyEvent { event ->
        if (event.type != KeyEventType.Down) return@onKeyEvent false
        val bound = NumberStepping.boundOf(event.key)
        if (bound != 0) element.stepTo(if (bound < 0) settings.min else settings.max) else element.stepBy(NumberStepping.stepsOf(event.key))
        bound != 0 || NumberStepping.stepsOf(event.key) != 0
    }
    Box(keyed, contentAlignment = Alignment.Center) {
        val side = element.height.dp
        StepButton(element.minusPress, Modifier.align(Alignment.CenterStart).size(side).testTag("canvas-stepper-minus"), "-", textStyle)
        Text(NumberStepping.format(element.number, settings.decimals), style = textStyle)
        StepButton(element.plusPress, Modifier.align(Alignment.CenterEnd).size(side).testTag("canvas-stepper-plus"), "+", textStyle)
        Children(element, children, tree)
    }
}

/** One of a Stepper's buttons, its [press] watched each frame for a hold; its click itself does nothing. */
context(_: Composer)
private fun BoxScope.StepButton(press: InteractionSource, modifier: Modifier, sign: String, textStyle: TextStyle) {
    val shade = if (press.isPressed) PRESSED_SHADE else Color.Transparent
    Box(
        modifier.hoverable(press).clickable(press) {}.drawBehind { if (shade.a > 0f) drawRect(color = shade) },
        contentAlignment = Alignment.Center,
    ) { Text(sign, style = textStyle) }
}

/**
 * Writes a Stepper's bound [CanvasBinding.value] into its [CanvasElement.number], as it is in the
 * field: a stepper bound to game state shows that state, and a step the player takes is reported by
 * [CanvasElement.consumeChange] for the game to write back. A field that reads nothing leaves the
 * number as it is.
 */
private fun readStepperNumber(element: CanvasElement, tree: CanvasTree) {
    val bind = element.bind?.takeIf { it.value.isNotBlank() } ?: return
    tree.resolver.numberField(bind.node, bind.value)?.let { element.number = it }
}

/** What a Stepper's button draws over itself while it is held. */
private val PRESSED_SHADE = Color(1f, 1f, 1f, 0.2f)
