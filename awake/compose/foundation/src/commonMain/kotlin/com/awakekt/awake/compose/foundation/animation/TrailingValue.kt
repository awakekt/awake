/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.animation

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.rememberGroup
import com.awakekt.awake.compose.ui.platform.LocalFrameClock

/**
 * A value that trails behind the one it follows, as a health bar's second fill shows damage just
 * taken: it rises with the value at once, and when the value drops it holds where it was for a moment,
 * then drains down to it.
 *
 * Kept by whatever draws the trail, one per bar, and advanced once a frame; it allocates nothing.
 */
class TrailingValue(initial: Float = 0f) {
    /** Where the trail is now. */
    var value: Float = initial
        private set

    private var followed = initial
    private var held = 0f

    /**
     * Moves the trail one frame of [deltaSeconds] toward [target] and returns it: up to [target]
     * straight away, or, below it, after [holdSeconds] since [target] last dropped, down by
     * [drainPerSecond] a second until it meets it. A drop while it drains holds it again.
     */
    fun advance(target: Float, deltaSeconds: Float, holdSeconds: Float = DEFAULT_TRAIL_HOLD_SECONDS, drainPerSecond: Float = DEFAULT_TRAIL_DRAIN_PER_SECOND): Float {
        if (target.isNaN()) return value
        if (target < followed) held = 0f
        followed = target
        if (target >= value) {
            value = target
            held = 0f
        } else {
            val waited = held
            held += deltaSeconds
            // Only the part of this frame past the hold drains, so a long frame does not skip the hold.
            val draining = (held - maxOf(waited, holdSeconds)).coerceIn(0f, deltaSeconds)
            value = maxOf(target, value - drainPerSecond * draining)
        }
        return value
    }

    /** Puts the trail on [target] at once, as a bar refilled or shown for the first time. */
    fun snapTo(target: Float) {
        value = target
        followed = target
        held = 0f
    }
}

/** How long a [TrailingValue] holds before it drains: long enough to read what was lost. */
const val DEFAULT_TRAIL_HOLD_SECONDS: Float = 0.5f

/** How fast a [TrailingValue] drains, in the bar's units a second: a full bar in under a second. */
const val DEFAULT_TRAIL_DRAIN_PER_SECOND: Float = 1.5f

context(_: Composer)
/**
 * [target], trailed as [TrailingValue] does by this frame's clock: what a progress bar draws under its
 * fill to show what was just lost. It starts at [target] the first time it is read.
 */
fun animateTrailing(
    target: Float,
    holdSeconds: Float = DEFAULT_TRAIL_HOLD_SECONDS,
    drainPerSecond: Float = DEFAULT_TRAIL_DRAIN_PER_SECOND,
): Float = rememberGroup(AnimateTrailingGroup) {
    val clock = LocalFrameClock.current
    val trail = remember { TrailingValue(target) }
    trail.advance(target, clock.deltaSeconds, holdSeconds, drainPerSecond)
}

/** Keys this component's `remember` slots as one group in its caller's node. */
private object AnimateTrailingGroup
