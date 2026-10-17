/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A browser's wheel carries its modifiers to Input as held keys, as desktop's Ctrl+wheel does. */
class WheelModifiersTest {

    /** Stands in for requestAnimationFrame: actions run when the test says a frame has passed. */
    private class Frames {
        val pending = mutableListOf<() -> Unit>()
        fun schedule(action: () -> Unit) {
            pending += action
        }
        fun pass() {
            val run = pending.toList()
            pending.clear()
            run.forEach { it() }
        }
    }

    @Test
    fun eachDomModifierFlagMapsToItsKey() {
        assertEquals(emptySet(), wheelModifierKeys(ctrl = false, shift = false, alt = false, meta = false))
        assertEquals(setOf(Key.Ctrl), wheelModifierKeys(ctrl = true, shift = false, alt = false, meta = false))
        assertEquals(setOf(Key.Shift), wheelModifierKeys(ctrl = false, shift = true, alt = false, meta = false))
        assertEquals(setOf(Key.Alt), wheelModifierKeys(ctrl = false, shift = false, alt = true, meta = false))
        assertEquals(setOf(Key.Meta), wheelModifierKeys(ctrl = false, shift = false, alt = false, meta = true))
        assertEquals(
            setOf(Key.Ctrl, Key.Shift, Key.Alt, Key.Meta),
            wheelModifierKeys(ctrl = true, shift = true, alt = true, meta = true),
        )
    }

    @Test
    fun aPinchReachesTheFrameAsCtrlWithTheScroll() {
        val input = Input()
        val frames = Frames()
        val modifiers = WheelModifiers(input, frames::schedule)

        // What the browser sends for a pinch: a wheel with ctrlKey set, and no key event.
        modifiers.hold(wheelModifierKeys(ctrl = true, shift = false, alt = false, meta = false))
        input.scrollDeltaY += domWheelToScrollDelta(-5.0, deltaMode = 0)
        val snapshot = input.updateSnapshot()

        assertTrue(snapshot.isDown(Key.Ctrl))
        assertEquals(0.05f, snapshot.scrollDeltaY)
        assertFalse(snapshot.isDown(Key.Shift))
    }

    @Test
    fun theModifierComesUpOnceTheFrameHasPassed() {
        val input = Input()
        val frames = Frames()
        val modifiers = WheelModifiers(input, frames::schedule)

        modifiers.hold(setOf(Key.Ctrl))
        assertTrue(input.updateSnapshot().isDown(Key.Ctrl))
        frames.pass()

        val next = input.updateSnapshot()
        assertFalse(next.isDown(Key.Ctrl))
        assertTrue(Key.Ctrl in next.keysReleased)
    }

    @Test
    fun aStreamOfWheelsSchedulesOneReleasePerFrame() {
        val input = Input()
        val frames = Frames()
        val modifiers = WheelModifiers(input, frames::schedule)

        repeat(5) { modifiers.hold(setOf(Key.Ctrl)) }

        assertEquals(1, frames.pending.size)
        frames.pass()
        modifiers.hold(setOf(Key.Ctrl))
        assertEquals(1, frames.pending.size, "the next frame's wheel holds Ctrl again")
        assertTrue(input.isKeyDown(Key.Ctrl))
    }

    @Test
    fun aPlainWheelHoldsNothingAndSchedulesNothing() {
        val input = Input()
        val frames = Frames()

        WheelModifiers(input, frames::schedule).hold(emptySet())

        assertTrue(frames.pending.isEmpty())
        assertFalse(input.isKeyDown(Key.Ctrl))
    }

    @Test
    fun aKeyAlreadyHeldByTheKeyboardIsNotReleased() {
        val input = Input()
        val frames = Frames()
        val modifiers = WheelModifiers(input, frames::schedule)
        input.setKeyDown(Key.Ctrl, true)

        modifiers.hold(setOf(Key.Ctrl, Key.Shift))
        frames.pass()

        assertTrue(input.isKeyDown(Key.Ctrl), "Ctrl belongs to the keyboard binding")
        assertFalse(input.isKeyDown(Key.Shift))
    }
}
