// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.animation

import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.runtime.current
import io.github.ronjunevaldoz.awake.compose.runtime.remember
import io.github.ronjunevaldoz.awake.compose.ui.platform.LocalFrameClock

/**
 * A value in `0f..1f` that loops every [durationSeconds], starting at 0 when the caller appears.
 *
 * For anything that spins, sweeps or pulses forever: a spinner's rotation, a skeleton's shimmer
 * band, an indeterminate progress bar.
 *
 * **Starts at zero on appearance**, which is why this reads the delta and keeps a phase rather than
 * deriving one from `FrameClock.totalSeconds`. A phase derived from total time is one line and
 * needs no state, but a spinner that appears mid-session would start at an arbitrary rotation --
 * invisible for a spinner, wrong for anything whose zero means "not started yet".
 *
 * There is no id. Identity is the node's position in the tree, so two spinners cannot collide the
 * way `animateFloat(id, ...)` lets two callers pick the same string and share one phase.
 */
context(_: Composer)
fun rememberLoopingPhase(durationSeconds: Float): Float {
    require(durationSeconds > 0f) { "durationSeconds must be positive, was $durationSeconds" }
    val clock = LocalFrameClock.current
    val phase = remember { Phase() }
    return phase.advance(clock.deltaSeconds / durationSeconds)
}

/**
 * Eases toward [target], reaching it [durationSeconds] after it last changed.
 *
 * For anything that settles: a collapsible's open fraction, a progress bar's fill, a toast's
 * opacity. Returns [target] itself on the first pass, so a caller never animates in from zero
 * unless it asked to.
 *
 * Linear. Easing curves are a `05-animation` follow-up -- shipping the interpolation without the
 * clock behind it would have been the harder half done first.
 */
context(_: Composer)
fun animateFloat(target: Float, durationSeconds: Float = DEFAULT_DURATION_SECONDS): Float {
    val clock = LocalFrameClock.current
    val animation = remember { AnimatedFloat(target) }
    return animation.advance(target, durationSeconds, clock.deltaSeconds)
}

/** 200 ms, the shadcn transition default and close enough to Compose's own short tween. */
const val DEFAULT_DURATION_SECONDS: Float = 0.2f

/** Node-local, because it is created through `remember` and dies with the node. */
private class Phase {
    private var value = 0f

    fun advance(step: Float): Float {
        // `% 1f` after adding, not `coerce`: the point is to wrap, and a step larger than one whole
        // cycle (a stall the clock did not fully absorb) should still land somewhere sensible.
        value = (value + step) % 1f
        return value
    }
}

private class AnimatedFloat(initial: Float) {
    private var from = initial
    private var to = initial
    private var duration = 0f
    private var elapsed = 0f

    /**
     * Restarts from wherever the value currently is whenever [target] changes.
     *
     * From the *current* value, not from [to]: a target that changes mid-flight would otherwise
     * snap to the old destination before setting off for the new one, which reads as a stutter on
     * every interrupted transition -- a collapsible toggled twice quickly is the case that shows it.
     */
    fun advance(target: Float, durationSeconds: Float, delta: Float): Float {
        if (target != to) {
            from = value()
            to = target
            duration = durationSeconds
            elapsed = 0f
        }
        elapsed += delta
        return value()
    }

    private fun value(): Float {
        if (duration <= 0f) return to
        val t = (elapsed / duration).coerceIn(0f, 1f)
        return from + (to - from) * t
    }
}
