/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FrameLoopTest {

    @Test
    fun frameRateModeCappedValidatesPositiveFps() {
        assertEquals(60, FrameRateMode.Capped(60).targetFps)
        assertFailsWith<IllegalArgumentException> {
            FrameRateMode.Capped(0)
        }
        assertFailsWith<IllegalArgumentException> {
            FrameRateMode.Capped(-30)
        }
    }

    @Test
    fun maxFrameDeltaConstantIsQuarterSecond() {
        assertEquals(0.25, MAX_FRAME_DELTA_SECONDS)
    }
}
