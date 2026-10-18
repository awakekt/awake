/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.BorderSides
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.rememberGroup
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.input.key.onPreviewKeyEvent
import com.awakekt.awake.compose.ui.platform.LocalFrameClock
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.core.input.HeldStep
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.NumberStepping
import com.awakekt.awake.tailwind.Tw

/**
 * A number stepper: minus and plus buttons around a field holding [value], kept between [min] and
 * [max] and moved [step] at a time, on the grid of steps from [min]. [onValueChange] gets every new
 * value; the caller holds it, as [ShadcnSlider]'s does.
 *
 * - **Buttons:** a press steps once, and holding one repeats after a short delay, as
 *   [NumberStepping.repeatsAfter] times it. Each button is disabled at its bound.
 * - **Field:** typing a number sets the value, held to the range. The field shows the value with
 *   [decimals] digits after the point whenever it changes some other way.
 * - **Keyboard:** while the stepper holds focus, Up and Down step once, Page Up and Page Down
 *   [NumberStepping.PAGE_STEPS] at a time, and Home and End jump to [min] and [max]. Left and Right
 *   are left to the field's caret.
 * - **Controller:** Compose has no controller events, so a host reads its controller and steps the
 *   value the same way: `onValueChange(NumberStepping.stepped(value, NumberStepping.stepsOf(button),
 *   min, max, step))`.
 *
 * Keywords: number stepper, number input, spin button, quantity, counter, increment, decrement.
 */
@Suppress("LongParameterList")
context(_: Composer)
fun ShadcnNumberStepper(
    value: Float,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = DEFAULT_STEPPER_MAX,
    step: Float = 1f,
    decimals: Int = 0,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit = {},
) {
    rememberGroup(ShadcnNumberStepperGroup) {
        val state = remember { StepperState() }
        val current = NumberStepping.clamp(value, min, max)
        val next = state.resolve(current, StepperRange(min, max, step), enabled, LocalFrameClock.current.deltaSeconds)
        if (next != current) onValueChange(next)
        state.show(next, current, decimals)

        Row(
            modifier.onPreviewKeyEvent { handleStepperKey(it, state) },
            horizontalArrangement = Arrangement.spacedByHorizontal(StepperGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(enabled && next > min, state.minusPress) { ShadcnIcon(ShadcnIcons.minus) }
            ShadcnInput(state.field, Modifier.width(StepperFieldWidth), enabled = enabled)
            StepButton(enabled && next < max, state.plusPress) { ShadcnIcon(ShadcnIcons.plus) }
        }
    }
}

/** An outline icon button whose press [source] the stepper watches; its click does nothing, as the hold steps. */
context(_: Composer)
private fun StepButton(enabled: Boolean, source: InteractionSource, content: context(Composer) () -> Unit) = shadcnButtonContent(
    Modifier,
    ShadcnButtonVariant.Outline,
    ShadcnButtonSizeVariant.Icon,
    enabled,
    onClick = {},
    shape = null,
    borderSides = BorderSides.All,
    hasIcon = false,
    pressSource = source,
    content = content,
)

/** Up, Down, Page Up and Page Down step, Home and End jump; the rest, Left and Right among them, reach the field. */
private fun handleStepperKey(event: KeyEvent, state: StepperState): Boolean {
    if (event.type != KeyEventType.Down || event.key == Key.ArrowLeft || event.key == Key.ArrowRight) return false
    val steps = NumberStepping.stepsOf(event.key)
    val bound = NumberStepping.boundOf(event.key)
    state.pendingSteps += steps
    if (bound != 0) state.pendingBound = bound
    return steps != 0 || bound != 0
}

/** A stepper's bounds and step, as one value. */
private class StepperRange(val min: Float, val max: Float, val step: Float)

/** What a stepper keeps between frames: its field, the two buttons' presses and holds, and keys not yet applied. */
private class StepperState {
    val field = TextFieldState()
    val minusPress = InteractionSource()
    val plusPress = InteractionSource()
    val minus = HeldStep()
    val plus = HeldStep()
    var pendingSteps = 0
    var pendingBound = 0

    /** The field's text as this stepper last saw or wrote it, so typing can be told apart. */
    var shown: String? = null

    /** The value as of the last frame, so a change the caller makes can be told apart; NaN at first. */
    var seen = Float.NaN

    /** Whether the field changed this frame because the player typed in it. */
    private var typedThisFrame = false

    /** Whether a button, a hold or a key moved the value this frame. */
    private var movedThisFrame = false

    /**
     * The value this frame, from [current]: what was typed, unless it is not a number yet such as
     * "-", then the presses, holds and keys that arrived before this build, as ShadcnSlider takes its
     * drag. Each is held to [range].
     */
    fun resolve(current: Float, range: StepperRange, enabled: Boolean, deltaSeconds: Float): Float {
        var next = current
        val typed = field.text
        typedThisFrame = shown != null && typed != shown
        if (typedThisFrame) {
            shown = typed
            typed.trim().toFloatOrNull()?.let { next = NumberStepping.clamp(it, range.min, range.max) }
        }
        val steps = plus.advance(enabled && plusPress.isPressed, deltaSeconds) -
            minus.advance(enabled && minusPress.isPressed, deltaSeconds) + if (enabled) pendingSteps else 0
        if (steps != 0) next = NumberStepping.stepped(next, steps, range.min, range.max, range.step)
        val bound = if (enabled) pendingBound else 0
        if (bound != 0) next = if (bound < 0) range.min else range.max
        movedThisFrame = steps != 0 || bound != 0
        pendingSteps = 0
        pendingBound = 0
        return next
    }

    /** Writes [next] into the field with [decimals] digits, unless the player is the one typing it. */
    fun show(next: Float, current: Float, decimals: Int) {
        if (!typedThisFrame && (movedThisFrame || seen != current)) {
            val text = NumberStepping.format(next, decimals)
            if (text != field.text) field.setText(text)
            shown = field.text
        }
        seen = next
    }
}

/** A stepper's upper bound when the caller gives none. */
private const val DEFAULT_STEPPER_MAX = 100f

/** shadcn `gap-1` between the buttons and the field. */
private val StepperGap: Dp = Tw.Spacing.s1

/** shadcn `w-16`: room for a few digits. */
private val StepperFieldWidth: Dp = Tw.Spacing.s16

/** Keys this component's `remember` slots as one group in its caller's node. */
private object ShadcnNumberStepperGroup
