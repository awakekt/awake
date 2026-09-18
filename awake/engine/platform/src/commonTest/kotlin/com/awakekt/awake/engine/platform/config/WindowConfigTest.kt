/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.config

import com.awakekt.awake.core.host.FrameRateMode
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowConfigTest {

    @Test
    fun effectiveFrameRateModeReturnsOriginalModeWhenFocused() {
        val config = WindowConfig(
            title = "Test",
            width = 800,
            height = 600,
            backend = AppWindowBackend.DEFAULT,
            frameRateMode = FrameRateMode.Auto,
            throttleCpuWhenNotForeground = true,
            backgroundFrameRate = 15,
        )

        assertEquals(FrameRateMode.Auto, config.effectiveFrameRateMode(isFocused = true))
    }

    @Test
    fun effectiveFrameRateModeCapsToBackgroundRateWhenUnfocusedAndThrottlingEnabled() {
        val config = WindowConfig(
            title = "Test",
            width = 800,
            height = 600,
            backend = AppWindowBackend.DEFAULT,
            frameRateMode = FrameRateMode.Auto,
            throttleCpuWhenNotForeground = true,
            backgroundFrameRate = 15,
        )

        assertEquals(FrameRateMode.Capped(15), config.effectiveFrameRateMode(isFocused = false))
    }

    @Test
    fun effectiveFrameRateModeBypassesThrottlingWhenThrottlingDisabled() {
        val config = WindowConfig(
            title = "Test",
            width = 800,
            height = 600,
            backend = AppWindowBackend.DEFAULT,
            frameRateMode = FrameRateMode.Capped(60),
            throttleCpuWhenNotForeground = false,
            backgroundFrameRate = 15,
        )

        assertEquals(FrameRateMode.Capped(60), config.effectiveFrameRateMode(isFocused = false))
    }

    @Test
    fun effectiveFrameRateModeCoercesNonPositiveBackgroundRate() {
        val config = WindowConfig(
            title = "Test",
            width = 800,
            height = 600,
            backend = AppWindowBackend.DEFAULT,
            frameRateMode = FrameRateMode.Auto,
            throttleCpuWhenNotForeground = true,
            backgroundFrameRate = 0,
        )

        assertEquals(FrameRateMode.Capped(1), config.effectiveFrameRateMode(isFocused = false))
    }
}
