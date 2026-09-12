/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/** Default target FPS constant preserved for backward compatibility. */
const val TARGET_FPS = 60

/** Maximum frame delta allowed in a single tick (250 ms / 4 FPS) to prevent massive time jumps after tab suspension or pauses. */
const val MAX_FRAME_DELTA_SECONDS: Double = 0.25

interface FrameLoop {
    /** Runs exactly one frame -- measures the delta since the previous call, invokes
     * [onUpdate], then sleeps out the remainder of the frame budget if configured by [mode].
     * The caller owns the repeat loop. */
    fun tick(
        mode: FrameRateMode = FrameRateMode.Auto,
        onUpdate: (deltaTime: Double) -> Unit,
    )

    /** Convenience overload defaulting to [FrameRateMode.Auto]. */
    fun tick(onUpdate: (deltaTime: Double) -> Unit) {
        tick(FrameRateMode.Auto, onUpdate)
    }
}
