/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputActionsTest {
    private val actions = InputActions(
        listOf(
            AxisAction("move", up = setOf(Key.W), down = setOf(Key.S), left = setOf(Key.A), right = setOf(Key.D)),
            ButtonAction("jump", keys = setOf(Key.Space)),
            ButtonAction("interact", keys = setOf(Key.E), trigger = ActionTrigger.Press),
            ButtonAction("walk", keys = setOf(Key.X), trigger = ActionTrigger.Toggle),
            ButtonAction("run", keys = setOf(Key.Shift), trigger = ActionTrigger.Toggle, startsOn = true),
            ButtonAction("fire", buttons = setOf(PointerButton.Primary)),
        ),
    )

    @Test
    fun aHoldIsActiveOnlyWhileItsKeyIsDown() {
        frame(down = setOf(Key.Space), pressed = setOf(Key.Space))
        assertTrue(actions.isActive("jump"))
        frame(down = setOf(Key.Space))
        assertTrue(actions.isActive("jump"))
        frame()
        assertFalse(actions.isActive("jump"))
    }

    @Test
    fun aPressIsActiveForTheFrameItIsPressed() {
        frame(down = setOf(Key.E), pressed = setOf(Key.E))
        assertTrue(actions.isActive("interact"))
        frame(down = setOf(Key.E))
        assertFalse(actions.isActive("interact"), "holding the key does not press it again")
    }

    @Test
    fun eachPressSwitchesAToggleWhichStartsAsDefined() {
        assertFalse(actions.isActive("walk"))
        assertTrue(actions.isActive("run"), "startsOn")

        frame(down = setOf(Key.X, Key.Shift), pressed = setOf(Key.X, Key.Shift))
        assertTrue(actions.isActive("walk"))
        assertFalse(actions.isActive("run"))
        frame(down = setOf(Key.X))
        frame()
        assertTrue(actions.isActive("walk"), "a toggle keeps its state without a press")
    }

    @Test
    fun aToggleSwitchesOnceAFrameHoweverManySourcesPressIt() {
        actions.beginFrame()
        actions.read(Source(down = setOf(Key.X), pressed = setOf(Key.X)))
        actions.press("walk")

        assertTrue(actions.isActive("walk"), "the key and an on-screen button pressed together switch it once")
    }

    @Test
    fun anAxisNormalisesDiagonalsAndCancelsOpposites() {
        frame(down = setOf(Key.W, Key.D))
        val half = 1f / sqrt(2f)
        assertEquals(half, actions.axisX("move"), 1e-6f)
        assertEquals(half, actions.axisY("move"), 1e-6f)

        frame(down = setOf(Key.A, Key.D, Key.S))
        assertEquals(0f, actions.axisX("move"))
        assertEquals(-1f, actions.axisY("move"))
    }

    @Test
    fun aPushReplacesWhatTheKeysGaveAnAxis() {
        actions.beginFrame()
        actions.read(Source(down = setOf(Key.W)))
        actions.push("move", 0.3f, -0.4f)

        assertEquals(0.3f, actions.axisX("move"))
        assertEquals(-0.4f, actions.axisY("move"))
    }

    @Test
    fun aPointerButtonTriggersAnAction() {
        actions.beginFrame()
        actions.read(Source(buttons = setOf(PointerButton.Primary)))

        assertTrue(actions.isActive("fire"))
    }

    @Test
    fun anUnknownActionIsInactiveAndOtherSourcesLeaveItAlone() {
        actions.hold("missing")
        actions.press("missing")
        actions.push("missing", 1f, 1f)

        assertFalse("missing" in actions)
        assertFalse(actions.isActive("missing"))
        assertFalse(actions.wasPressed("missing"))
        assertEquals(0f, actions.axisX("missing"))
    }

    @Test
    fun namesAreCheckedAndUnique() {
        assertFailsWith<IllegalArgumentException> { InputActions(listOf(ButtonAction("open door"))) }
        assertFailsWith<IllegalArgumentException> { InputActions(listOf(ButtonAction("jump"), AxisAction("jump"))) }
    }

    private fun frame(down: Set<Key> = emptySet(), pressed: Set<Key> = emptySet()) {
        actions.beginFrame()
        actions.read(Source(down, pressed))
    }

    private class Source(
        private val down: Set<Key> = emptySet(),
        private val pressed: Set<Key> = emptySet(),
        private val buttons: Set<PointerButton> = emptySet(),
    ) : ActionInputSource {
        override fun isDown(key: Key) = key in down
        override fun wasPressed(key: Key) = key in pressed
        override fun isDown(button: PointerButton) = button in buttons
        override fun wasPressed(button: PointerButton) = false
    }
}
