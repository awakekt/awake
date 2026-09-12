/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopFrameLoopTest {

    @Test
    fun desktopFrameLoopClampsUnusuallyLargeDelta() {
        var recordedDelta = -1.0
        DesktopFrameLoop.tick(FrameRateMode.Auto) { delta ->
            recordedDelta = delta
        }
        assertTrue(recordedDelta in 0.0..MAX_FRAME_DELTA_SECONDS)
    }

    @Test
    fun desktopFrameLoopSupportsUnlimitedAndCappedModes() {
        var recordedDelta = -1.0
        DesktopFrameLoop.tick(FrameRateMode.Unlimited) { delta ->
            recordedDelta = delta
        }
        assertTrue(recordedDelta in 0.0..MAX_FRAME_DELTA_SECONDS)

        DesktopFrameLoop.tick(FrameRateMode.Capped(120)) { delta ->
            recordedDelta = delta
        }
        assertTrue(recordedDelta in 0.0..MAX_FRAME_DELTA_SECONDS)
    }

    @Test
    fun consecutiveTicksMeasureFullElapsedFrameInterval() {
        DesktopFrameLoop.tick(FrameRateMode.Capped(60)) {}
        var secondDelta = -1.0
        DesktopFrameLoop.tick(FrameRateMode.Capped(60)) { delta ->
            secondDelta = delta
        }
        assertTrue(secondDelta >= 0.010, "Expected second delta >= 10ms, but was ${secondDelta * 1000}ms")
    }
}
