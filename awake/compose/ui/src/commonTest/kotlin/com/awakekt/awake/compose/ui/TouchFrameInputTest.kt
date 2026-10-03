/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.platform.toFrameInput
import com.awakekt.awake.core.input.Input
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TouchFrameInputTest {
    @Test
    fun fingersReachTheUiOnceEachAsTheirOwnPointers() {
        val input = Input()
        input.setTouch(0, 10f, 20f, down = true)
        input.setTouch(1, 200f, 20f, down = true)

        val frame = input.updateSnapshot().toFrameInput(400, 300)

        assertEquals(
            listOf(PointerFrame(1, 10, 20, down = true, pressed = true), PointerFrame(2, 200, 20, down = true, pressed = true)),
            frame.pointers,
            "ids start at 1, since 0 is the mouse's",
        )
        assertEquals(FrameInput.UNKNOWN_POINTER, frame.pointerX, "the primary pointer the first finger drives would press twice")
        assertFalse(frame.pointerDown || frame.pointerPressed)
    }

    @Test
    fun aMouseStillArrivesAsThePointer() {
        val input = Input()
        input.setPointer(down = true, x = 30f, y = 40f)

        val frame = input.updateSnapshot().toFrameInput(400, 300)

        assertEquals(30 to 40, frame.pointerX to frame.pointerY)
        assertEquals(emptyList(), frame.pointers)
    }
}
