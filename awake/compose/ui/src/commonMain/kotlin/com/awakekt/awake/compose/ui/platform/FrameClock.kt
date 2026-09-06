/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

import com.awakekt.awake.compose.runtime.compositionLocalOf

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

    /** Seconds covered by this frame, clamped -- see [advance]. */
    var deltaSeconds: Float = 0f
        private set

    /** Seconds since the host's first frame. Monotonic, and the same for every reader in a pass. */
    var totalSeconds: Float = 0f
        private set

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
        deltaSeconds = step
        totalSeconds += step
    }

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
