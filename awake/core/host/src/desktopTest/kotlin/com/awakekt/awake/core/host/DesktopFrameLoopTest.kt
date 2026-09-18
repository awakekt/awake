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

    @Test
    fun desktopFrameLoopThrottlesWhenUnfocusedWithBackgroundRate() {
        try {
            DesktopFrameLoop.backgroundFrameRate = 30
            DesktopFrameLoop.isWindowFocused = false

            // Prime the previousFrameTime
            DesktopFrameLoop.tick(FrameRateMode.Unlimited) {}

            var delta = -1.0
            DesktopFrameLoop.tick(FrameRateMode.Unlimited) { d ->
                delta = d
            }
            // 30 FPS target is ~33.3ms. Expect delta to be at least 20ms accounting for timer granularity.
            assertTrue(delta >= 0.020, "Expected throttled delta >= 20ms, but was ${delta * 1000}ms")
        } finally {
            DesktopFrameLoop.resetForTest()
        }
    }

    @Test
    fun desktopFrameLoopDoesNotThrottleWhenFocused() {
        try {
            DesktopFrameLoop.backgroundFrameRate = 15
            DesktopFrameLoop.isWindowFocused = true

            // Prime the previousFrameTime
            DesktopFrameLoop.tick(FrameRateMode.Unlimited) {}

            var delta = -1.0
            DesktopFrameLoop.tick(FrameRateMode.Unlimited) { d ->
                delta = d
            }
            // In unlimited mode when focused, no sleep occurs (typically < 10ms).
            assertTrue(delta < 0.050, "Expected unthrottled delta < 50ms, but was ${delta * 1000}ms")
        } finally {
            DesktopFrameLoop.resetForTest()
        }
    }

    @Test
    fun desktopFrameLoopDoesNotThrottleWhenBackgroundRateIsNull() {
        try {
            DesktopFrameLoop.backgroundFrameRate = null
            DesktopFrameLoop.isWindowFocused = false

            // Prime the previousFrameTime
            DesktopFrameLoop.tick(FrameRateMode.Unlimited) {}

            var delta = -1.0
            DesktopFrameLoop.tick(FrameRateMode.Unlimited) { d ->
                delta = d
            }
            // With null backgroundFrameRate, unlimited mode should not sleep
            assertTrue(delta < 0.050, "Expected unthrottled delta < 50ms, but was ${delta * 1000}ms")
        } finally {
            DesktopFrameLoop.resetForTest()
        }
    }

    @Test
    fun desktopFrameLoopRespectsFrameRateOverride() {
        try {
            DesktopFrameLoop.isWindowFocused = true
            DesktopFrameLoop.frameRateOverride = FrameRateMode.Capped(30)

            // Prime the previousFrameTime
            DesktopFrameLoop.tick(FrameRateMode.Unlimited) {}

            var delta = -1.0
            DesktopFrameLoop.tick(FrameRateMode.Unlimited) { d ->
                delta = d
            }
            // 30 FPS target is ~33.3ms. Expect delta to be at least 20ms accounting for timer granularity.
            assertTrue(delta >= 0.020, "Expected override capped delta >= 20ms, but was ${delta * 1000}ms")
        } finally {
            DesktopFrameLoop.resetForTest()
        }
    }
}
