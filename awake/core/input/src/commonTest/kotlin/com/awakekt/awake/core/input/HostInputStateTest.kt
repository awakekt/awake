/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostInputStateTest {
    @Test
    fun preferenceAndDeviceChangesNotifyOnceAndQuietFramesKeepTheDevice() {
        val input = Input()
        val state = HostInputState()
        assertFalse(state.update(input.updateSnapshot(), false))
        assertNull(state.lastInputDevice)
        assertEquals(0L, state.changeVersion)

        input.setTouch(1, 10f, 20f, down = true)
        assertTrue(state.update(input.updateSnapshot(), true))
        assertEquals(InputDevice.Touch, state.lastInputDevice)
        assertTrue(state.showTouchControls)
        assertEquals(1L, state.changeVersion, "two changed fields are one state change")
        input.setTouch(1, 10f, 20f, down = false)
        state.update(input.updateSnapshot(), true)
        assertFalse(state.update(input.updateSnapshot(), true))
        assertEquals(InputDevice.Touch, state.lastInputDevice)
        assertEquals(1L, state.changeVersion)

        input.setKeyDown(Key.W, true)
        assertTrue(state.update(input.updateSnapshot(), true))
        assertEquals(InputDevice.KeyboardMouse, state.lastInputDevice)
        assertEquals(2L, state.changeVersion)
        assertTrue(state.update(input.updateSnapshot(), false))
        assertFalse(state.showTouchControls)
        assertEquals(3L, state.changeVersion)
    }
}
