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

/**
 * A measured frame delta, from [rawSeconds], as every host passes it on: never above
 * [MAX_FRAME_DELTA_SECONDS], and never below 0. A frame's start can read earlier than the time a host took
 * before it, as a browser's `requestAnimationFrame` timestamp does against `performance.now()`, and a
 * negative step would run time backwards.
 */
fun clampFrameDelta(rawSeconds: Double): Double = rawSeconds.coerceIn(0.0, MAX_FRAME_DELTA_SECONDS)

/**
 * Core interface driving per-frame tick updates and rate limiting across platform hosts.
 */
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
