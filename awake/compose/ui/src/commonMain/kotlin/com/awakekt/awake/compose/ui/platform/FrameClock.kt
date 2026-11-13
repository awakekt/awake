/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

import com.awakekt.awake.compose.runtime.PolledState
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.readsAreTracked

/**
 * How much time this frame covers, and how much has passed since the host started.
 *
 * Read at build time rather than subscribed to. `05-animation.md` sketches
 * `Modifier.Node.requestFrames { }` with the animation's phase held as a field on the modifier
 * node -- that cannot work here, because **the modifier chain is rebuilt every pass**, so the
 * instance that started an animation is not the instance that would advance it. The same mistake
 * shipped once already in `clickable`, where the link that saw a press was gone by the release and
 * every two-frame click silently did nothing.
 *
 * So there is no subscription. Content runs every frame regardless -- this is a surface redrawing
 * behind a live 3D scene, not an app idling at 0 fps between taps -- and an animation reads the
 * clock where it is declared, keeping its own state in `remember`, which lives on the node and does
 * survive.
 *
 * For the same reason there is no `invalidateDraw()`: `ComposeHost.frame` repaints the whole tree
 * unconditionally, so there is no invalidation to request.
 */
class FrameClock {

    private var delta = 0f
    private var total = 0f

    /** Bumped by an [advance] that moves time, so a pass that read the clock is out of date. */
    private var revision = 0

    private val time = object : PolledState<Int>() {
        override fun read(): Int = revision
    }

    /**
     * Seconds covered by this frame, clamped -- see [advance].
     *
     * Reading it in a pass that tracks reads records the read: a host that skips unchanged passes
     * recomposes when the clock moves. Otherwise it records nothing.
     */
    val deltaSeconds: Float
        get() {
            if (readsAreTracked) time.value
            return delta
        }

    /** Seconds since the host's first frame. Monotonic, and the same for every reader in a pass. Tracked like [deltaSeconds]. */
    val totalSeconds: Float
        get() {
            if (readsAreTracked) time.value
            return total
        }

    /**
     * Clamped to [MAX_DELTA_SECONDS], because a stalled frame is not a fast-forward.
     *
     * A breakpoint, a backgrounded tab or a slow asset load hands the next frame a delta measured
     * in seconds. Advancing an animation by it teleports the thing being animated -- a 200 ms fade
     * completes between two visible frames, and a looping phase jumps to an arbitrary point. A
     * dropped frame should look like a dropped frame, not like a seek.
     */
    internal fun advance(delta: Float) {
        val step = delta.coerceIn(0f, MAX_DELTA_SECONDS)
        // A run of still frames (step 0 after step 0) leaves the clock where it was.
        if (step != 0f || this.delta != 0f) revision++
        this.delta = step
        total += step
    }

    /** Holds the clamp applied to every frame delta. */
    companion object {
        /** A 10 fps floor. Below it the frame is treated as a stall, not as elapsed time. */
        const val MAX_DELTA_SECONDS: Float = 0.1f
    }
}

/**
 * The clock driving this composition.
 *
 * Provided by [ComposeHost] around the content lambda, so every animation in one pass reads the
 * same delta. Two animations that stepped by different deltas in the same frame would drift apart
 * for no reason a caller could see.
 */
val LocalFrameClock = compositionLocalOf { FrameClock() }
