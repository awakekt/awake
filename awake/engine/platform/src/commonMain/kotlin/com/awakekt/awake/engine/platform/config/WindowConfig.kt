/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.config

import com.awakekt.awake.core.host.FrameRateMode
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend

data class WindowConfig(
    val title: String,
    val width: Int,
    val height: Int,
    val backend: AppWindowBackend,
    /** How finished frames reach the display; a request, not a guarantee. See [PresentMode]. */
    val presentMode: PresentMode = PresentMode.Auto,
    /** Target frame rate mode. Defaults to [FrameRateMode.Auto]. */
    val frameRateMode: FrameRateMode = FrameRateMode.Auto,
    /**
     * Whether to throttle the frame rate when the window is not in the foreground.
     * Defaults to true to conserve CPU/GPU and battery when the window is unfocused.
     */
    val throttleCpuWhenNotForeground: Boolean = true,
    /**
     * Target frame rate when the window is not in the foreground and [throttleCpuWhenNotForeground] is true.
     * Defaults to 15 FPS.
     */
    val backgroundFrameRate: Int = DEFAULT_BACKGROUND_FRAME_RATE,
) {
    /**
     * Computes the effective [FrameRateMode] based on window focus.
     */
    fun effectiveFrameRateMode(isFocused: Boolean = true): FrameRateMode =
        if (!isFocused && throttleCpuWhenNotForeground) {
            FrameRateMode.Capped(backgroundFrameRate.coerceAtLeast(1))
        } else {
            frameRateMode
        }

    companion object {
        const val DEFAULT_BACKGROUND_FRAME_RATE: Int = 15
    }
}
