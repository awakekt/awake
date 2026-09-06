/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PointerButtonTest {

    @Test
    fun buttonsReportPressReleaseEdgesLikeKeysDo() {
        val input = Input()

        input.setButton(PointerButton.Middle, down = true)
        val pressed = input.updateSnapshot()
        assertTrue(pressed.isDown(PointerButton.Middle))
        assertTrue(pressed.wasPressed(PointerButton.Middle), "the frame it goes down is a press")
        assertFalse(pressed.wasReleased(PointerButton.Middle))

        // Still held, no longer a press: the edge is one frame wide.
        val held = input.updateSnapshot()
        assertTrue(held.isDown(PointerButton.Middle))
        assertFalse(held.wasPressed(PointerButton.Middle))

        input.setButton(PointerButton.Middle, down = false)
        val released = input.updateSnapshot()
        assertFalse(released.isDown(PointerButton.Middle))
        assertTrue(released.wasReleased(PointerButton.Middle))
    }

    /** The two that already had dedicated fields must stay in step with the set. */
    @Test
    fun primaryAndSecondaryAppearInTheSetToo() {
        val input = Input()

        input.setPointer(down = true, x = 1f, y = 2f)
        input.setSecondaryPointer(down = true)
        val snapshot = input.updateSnapshot()

        assertTrue(snapshot.pointerDown)
        assertTrue(snapshot.secondaryPointerDown)
        assertTrue(snapshot.isDown(PointerButton.Primary))
        assertTrue(snapshot.isDown(PointerButton.Secondary))

        input.setPointer(down = false, x = 1f, y = 2f)
        val after = input.updateSnapshot()
        assertFalse(after.isDown(PointerButton.Primary), "releasing the pointer clears Primary")
        assertTrue(after.isDown(PointerButton.Secondary), "and leaves the other button alone")
    }

    @Test
    fun buttonsAreIndependent() {
        val input = Input()
        input.setButton(PointerButton.Middle, down = true)
        input.setButton(PointerButton.Back, down = true)
        input.setButton(PointerButton.Middle, down = false)

        val snapshot = input.updateSnapshot()
        assertEquals(setOf(PointerButton.Back), snapshot.buttonsDown)
    }

    @Test
    fun anEmptySnapshotHoldsNothing() {
        assertTrue(Input().updateSnapshot().buttonsDown.isEmpty())
    }
}
