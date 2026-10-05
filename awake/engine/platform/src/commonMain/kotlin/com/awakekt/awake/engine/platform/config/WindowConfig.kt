/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.config

import com.awakekt.awake.core.host.FrameRateMode
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend

/**
 * Configuration parameters for application window presentation, display dimensions, and rendering backend.
 *
 * @property title Window display title text.
 * @property width Initial window width in screen pixels.
 * @property height Initial window height in screen pixels.
 * @property backend Requested graphics window backend.
 * @property presentMode How finished frames reach the display; a request, not a guarantee. See [PresentMode].
 * @property frameRateMode Target frame rate mode. Defaults to [FrameRateMode.Auto].
 * @property throttleCpuWhenNotForeground Whether to throttle the frame rate when the window is not in the foreground.
 * @property backgroundFrameRate Target frame rate when the window is unfocused and background throttling is enabled.
 */
data class WindowConfig(
    val title: String,
    val width: Int,
    val height: Int,
    val backend: AppWindowBackend,
    val presentMode: PresentMode = PresentMode.Auto,
    val frameRateMode: FrameRateMode = FrameRateMode.Auto,
    val throttleCpuWhenNotForeground: Boolean = true,
    val backgroundFrameRate: Int = DEFAULT_BACKGROUND_FRAME_RATE,
) {
    /**
     * Computes the effective [FrameRateMode] based on window focus.
     *
     * @param isFocused Whether the window currently holds user input focus.
     * @return Computed effective frame rate mode.
     */
    fun effectiveFrameRateMode(isFocused: Boolean = true): FrameRateMode =
        if (!isFocused && throttleCpuWhenNotForeground) {
            FrameRateMode.Capped(backgroundFrameRate.coerceAtLeast(1))
        } else {
            frameRateMode
        }

    /** Companion constants for window configuration defaults. */
    companion object {
        /** Default frame rate cap in frames per second applied when an unfocused window is throttled. */
        const val DEFAULT_BACKGROUND_FRAME_RATE: Int = 15
    }
}
