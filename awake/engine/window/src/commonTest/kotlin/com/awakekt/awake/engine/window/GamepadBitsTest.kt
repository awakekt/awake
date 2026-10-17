/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.Input
import kotlin.test.Test
import kotlin.test.assertEquals

/** A browser's standard-layout controller, polled as bits, reaches [Input] as buttons named by position. */
class GamepadBitsTest {
    @Test
    fun theStandardLayoutNamesEachOfItsSeventeenButtonsOnce() {
        assertEquals(17, StandardGamepadLayout.size)
        assertEquals(GamepadButton.entries.toSet(), StandardGamepadLayout.toSet())
        assertEquals(GamepadButton.South, StandardGamepadLayout[0], "buttons[0] is the bottom face button")
        assertEquals(GamepadButton.LeftTrigger, StandardGamepadLayout[6])
        assertEquals(GamepadButton.DpadUp, StandardGamepadLayout[12])
        assertEquals(GamepadButton.Home, StandardGamepadLayout[16])
    }

    @Test
    fun aPollPressesHoldsAndReleases() {
        val input = Input()

        applyGamepadBits((1 shl 1) or (1 shl 12), input, StandardGamepadLayout)
        var snapshot = input.snapshot()
        assertEquals(setOf(GamepadButton.East, GamepadButton.DpadUp), snapshot.gamepadButtonsPressed)

        applyGamepadBits(1 shl 1, input, StandardGamepadLayout)
        snapshot = input.snapshot()
        assertEquals(setOf(GamepadButton.East), snapshot.gamepadButtonsDown)
        assertEquals(emptySet(), snapshot.gamepadButtonsPressed, "held, not pressed again")
        assertEquals(setOf(GamepadButton.DpadUp), snapshot.gamepadButtonsReleased)
    }

    @Test
    fun noControllerReleasesEveryButton() {
        val input = Input()
        applyGamepadBits(-1 ushr 1, input, StandardGamepadLayout)
        assertEquals(GamepadButton.entries.toSet(), input.snapshot().gamepadButtonsDown)

        applyGamepadBits(NO_GAMEPAD, input, StandardGamepadLayout)

        assertEquals(emptySet(), input.snapshot().gamepadButtonsDown)
    }
}
