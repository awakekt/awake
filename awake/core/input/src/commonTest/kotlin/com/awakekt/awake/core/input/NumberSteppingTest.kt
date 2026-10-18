/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberSteppingTest {

    @Test
    fun keysAndThePadStepTheSameWay() {
        assertEquals(listOf(1, 1, -1, -1, 10, -10, 0), listOf(Key.ArrowUp, Key.ArrowRight, Key.ArrowDown, Key.ArrowLeft, Key.PageUp, Key.PageDown, Key.A).map(NumberStepping::stepsOf))
        assertEquals(
            listOf(1, 1, -1, -1, 10, -10, 0),
            listOf(GamepadButton.DpadUp, GamepadButton.DpadRight, GamepadButton.DpadDown, GamepadButton.DpadLeft, GamepadButton.RightShoulder, GamepadButton.LeftShoulder, GamepadButton.South)
                .map(NumberStepping::stepsOf),
        )
        assertEquals(listOf(-1, 1, 0), listOf(Key.Home, Key.End, Key.Enter).map(NumberStepping::boundOf))
    }

    @Test
    fun aStepLandsOnTheGridFromTheMinimumAndInsideTheRange() {
        assertEquals(1.5f, NumberStepping.stepped(1f, 1, 0f, 10f, 0.5f))
        assertEquals(4f, NumberStepping.stepped(1.2f, 3, 0f, 10f, 1f), "an off-grid value snaps first")
        assertEquals(10f, NumberStepping.stepped(9f, 5, 0f, 10f, 1f))
        assertEquals(3f, NumberStepping.stepped(5f, -1, 3f, 10f, 2f), "the grid starts at the minimum")
        assertEquals(2f, NumberStepping.stepped(1f, 1, 0f, 10f, 0f), "no step moves by ones")
    }

    @Test
    fun aHeldButtonRepeatsAfterItsDelay() {
        assertEquals(listOf(0, 0, 1, 2, 3), listOf(0f, 0.3f, 0.4f, 0.5f, 0.6f).map { NumberStepping.repeatsAfter(it, 0.4f, 0.1f) })

        val hold = HeldStep()
        val steps = listOf(true, true, true, true, true, true, false, true).map { hold.advance(it, 0.1f, 0.3f, 0.1f) }

        assertEquals(listOf(1, 0, 0, 1, 1, 1, 0, 1), steps, "one on the press, then one a frame from the delay; a new press starts over")
    }

    @Test
    fun numbersAreWrittenTheSameEverywhere() {
        assertEquals(listOf("3", "-2", "0.25", "1.50", "-0.1", "0.0"), listOf(NumberStepping.format(3.4f), NumberStepping.format(-2f), NumberStepping.format(0.25f, 2), NumberStepping.format(1.5f, 2), NumberStepping.format(-0.1f, 1), NumberStepping.format(-0.01f, 1)))
    }

    @Test
    fun aBackwardsRangeHoldsAtTheMinimum() {
        assertEquals(5f, NumberStepping.clamp(1f, 5f, 0f))
        assertEquals(0f, NumberStepping.clamp(Float.NaN, 0f, 10f))
    }
}
