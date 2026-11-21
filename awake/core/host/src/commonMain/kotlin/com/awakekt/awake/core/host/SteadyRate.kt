/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/**
 * Whether a host holds frames at one steady rate the display divides evenly, chosen from what frames
 * cost (#1699). It applies while the frame-rate mode is [FrameRateMode.Auto]; a [FrameRateMode.Capped]
 * or [FrameRateMode.Unlimited] mode the app chose is left as it is.
 *
 * Unpaced on a 120 Hz panel, frames that cost 12 to 16 ms show for one refresh, then two, and the
 * rate reads anywhere from 60 to 83: the mix moves with the cost. A steady 60 looks smoother.
 */
sealed interface SteadyRate {
    /** Nothing is chosen: frames run as they do without this setting. The default. */
    data object Off : SteadyRate

    /** [SteadyFrameRate] picks the highest candidate rate the measured frame cost holds, and moves it as the cost changes. */
    data object Auto : SteadyRate

    /** Frames are held at [fps], whatever they cost. */
    data class Fixed(val fps: Int) : SteadyRate {
        init {
            require(fps > 0) { "fps must be positive: $fps" }
        }
    }
}

/** Why [SteadyRateChoice.targetFps] is the target. */
enum class SteadyRateReason {
    /** The first choice, from the first frames' cost. */
    Start,

    /** The rate was too fast for the frames: their cost passed the target's budget, so it stepped down. */
    SteppedDown,

    /** The frames fit the next faster rate with room to spare for a while, so it stepped up. */
    SteppedUp,

    /** [SteadyRate.Fixed] asks for this rate. */
    Fixed,
}

/**
 * The rate a host holds and why, for a frame-rate HUD: one screenshot of it says what the selector
 * saw. [costP90Nanos] is the 90th-percentile frame cost it last decided on, over the frames
 * [SteadyFrameRate] names for the [reason]; 0 for [SteadyRateReason.Fixed].
 */
data class SteadyRateChoice(
    val targetFps: Int,
    val reason: SteadyRateReason,
    val costP90Nanos: Long,
    val candidatesFps: List<Int>,
)
