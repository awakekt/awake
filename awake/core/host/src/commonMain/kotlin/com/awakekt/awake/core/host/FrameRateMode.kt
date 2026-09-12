/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/**
 * Target frame rate mode for engine frame loops.
 */
sealed interface FrameRateMode {
    /**
     * Uncapped / platform-driven frame rate. Rendering is synchronized to display vsync /
     * presentation cadence without artificial sleeping.
     */
    data object Auto : FrameRateMode

    /**
     * Hard frame rate cap (e.g. 30, 60, 120 FPS). Frame loop sleeps out the remaining budget per frame.
     */
    data class Capped(val targetFps: Int) : FrameRateMode {
        init {
            require(targetFps > 0) { "Target FPS must be positive, found $targetFps" }
        }
    }

    /**
     * Uncapped frame rate with no sleep throttling at all.
     */
    data object Unlimited : FrameRateMode
}
