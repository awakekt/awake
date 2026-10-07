/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals

/** A browser's wheel reaches Input with desktop's sign and scale. */
class WheelDeltaTest {

    @Test
    fun aWheelTurnedTowardTheUserScrollsTheWayItDoesOnDesktop() {
        // The DOM reports that notch as +100 px; GLFW reports the same notch as -1.
        assertEquals(-1f, domWheelToScrollDelta(100.0, deltaMode = 0))
        assertEquals(-3f, domWheelToScrollDelta(3.0, DOM_DELTA_LINE))
        assertEquals(-32f, domWheelToScrollDelta(1.0, DOM_DELTA_PAGE))
    }

    @Test
    fun sidewaysFollowsTheSameRule() {
        // DOM +x scrolls the page right; GLFW's matching offset is negative.
        assertEquals(-0.5f, domWheelToScrollDelta(50.0, deltaMode = 0))
    }
}
